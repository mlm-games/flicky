package app.flicky.install

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.flicky.data.model.FDroidApp
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class Installer(private val context: Context) {
    companion object {
        private const val CACHE_DIR = "flicky_downloads"
        private const val CACHE_EXPIRY_HOURS = 1
    }

    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloads = mutableMapOf<String, Long>() // packageName -> downloadId

    init {
        // Clean old cache on init
        cleanOldCache()
    }

    fun open(packageName: String) {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun uninstall(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, "package:$packageName".toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun cancelDownload(packageName: String) {
        activeDownloads[packageName]?.let { downloadId ->
            dm.remove(downloadId)
            activeDownloads.remove(packageName)
            getCacheFile(packageName).delete()
        }
    }

    private fun getBaseCacheDir(): File {
        val base = context.externalCacheDir ?: context.cacheDir
        return File(base, CACHE_DIR).apply { mkdirs() }
    }

    private fun getCacheFile(packageName: String): File {
        return File(getBaseCacheDir(), "$packageName.apk")
    }

    private fun cleanOldCache() {
        scope.launch {
            val cacheDir = getBaseCacheDir()
            val cutoffTime = System.currentTimeMillis() - (CACHE_EXPIRY_HOURS * 60L * 60L * 1000L)
            cacheDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    runCatching { file.delete() }
                }
            }
        }
    }

    suspend fun install(app: FDroidApp, onProgress: (Float) -> Unit = {}): Boolean {
        // If we already cached it, verify and install
        val cached = getCacheFile(app.packageName)
        if (cached.exists()) {
            if (app.sha256.isBlank() || verifySha256File(cached, app.sha256)) {
                return installApk(fileProviderUri(cached), app.packageName)
            } else {
                cached.delete()
            }
        }

        // Unknown sources permission (O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = "package:${context.packageName}".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return false
            }
        }

        val downloadUrl = constructDownloadUrl(app)
        return try {
            downloadAndInstall(app, downloadUrl, onProgress)
        } catch (_: Exception) {
            false
        }
    }

    private fun constructDownloadUrl(app: FDroidApp): String {
        return when {
            app.apkUrl.startsWith("http://") || app.apkUrl.startsWith("https://") -> app.apkUrl
            app.apkUrl.startsWith("/") -> {
                val repoBase = getRepoBase(app.repository)
                repoBase + app.apkUrl
            }
            else -> {
                val repoBase = getRepoBase(app.repository)
                "$repoBase/${app.apkUrl}"
            }
        }
    }

    private fun getRepoBase(repository: String): String {
        return when (repository) {
            "F-Droid" -> "https://f-droid.org/repo"
            "IzzyOnDroid" -> "https://apt.izzysoft.de/fdroid/repo"
            "F-Droid Archive" -> "https://f-droid.org/archive"
            else -> "https://f-droid.org/repo"
        }
    }

    private suspend fun downloadAndInstall(
        app: FDroidApp,
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val cacheFile = getCacheFile(app.packageName)

        val req = DownloadManager.Request(downloadUrl.toUri())
            .setTitle("${app.name} ${app.version}")
            .setDescription("Downloading from ${app.repository}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationUri(Uri.fromFile(cacheFile))

        val downloadId = dm.enqueue(req)
        activeDownloads[app.packageName] = downloadId

        val finishedUri = monitorDownload(downloadId, onProgress)
        activeDownloads.remove(app.packageName)

        if (finishedUri == null) {
            cacheFile.delete()
            return@withContext false
        }

        // Verify from the known cache file path
        if (app.sha256.isNotBlank() && !verifySha256File(cacheFile, app.sha256)) {
            cacheFile.delete()
            return@withContext false
        }

        val ok = installApk(fileProviderUri(cacheFile), app.packageName)

        scope.launch {
            delay(60_000)
            runCatching { if (cacheFile.exists()) cacheFile.delete() }
        }

        ok
    }

    private fun fileProviderUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun installApk(uri: Uri, packageName: String): Boolean {
        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(install)
            // extra cleanup after a bit more delay
            scope.launch {
                delay(120_000)
                val f = getCacheFile(packageName)
                if (f.exists()) f.delete()
            }
            true
        }.getOrDefault(false)
    }

    private suspend fun monitorDownload(
        id: Long,
        onProgress: (Float) -> Unit
    ): Uri? = withContext(Dispatchers.IO) {
        val query = DownloadManager.Query().setFilterById(id)
        var lastProgress = -1f

        while (isActive) {
            val cursor = dm.query(query)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIdx >= 0) {
                        when (cursor.getInt(statusIdx)) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                onProgress(1f)
                                return@withContext dm.getUriForDownloadedFile(id)
                            }
                            DownloadManager.STATUS_FAILED -> {
                                return@withContext null
                            }
                            DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> {
                                val bytesIdx =
                                    cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                                val totalIdx =
                                    cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                                if (bytesIdx >= 0 && totalIdx >= 0) {
                                    val total = cursor.getLong(totalIdx)
                                    if (total > 0) {
                                        val prog =
                                            cursor.getLong(bytesIdx).toFloat() / total.toFloat()
                                        if (prog != lastProgress) {
                                            lastProgress = prog
                                            onProgress(prog)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                cursor?.close()
            }
            delay(100)
        }
        null
    }

    private fun verifySha256File(file: File, expectedHex: String): Boolean {
        return try {
            FileInputStream(file).use { fis ->
                val md = MessageDigest.getInstance("SHA-256")
                val buf = ByteArray(8192)
                var r = fis.read(buf)
                while (r != -1) {
                    md.update(buf, 0, r)
                    r = fis.read(buf)
                }
                val actual = md.digest().joinToString("") { b -> "%02x".format(b) }
                actual.equals(expectedHex, ignoreCase = true)
            }
        } catch (_: Exception) {
            false
        }
    }
}