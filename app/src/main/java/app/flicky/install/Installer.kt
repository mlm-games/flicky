package app.flicky.install

import android.app.DownloadManager
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.flicky.data.model.FDroidApp
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.coroutines.resume

class Installer(private val context: Context) {
    companion object {
        private const val CACHE_DIR = "flicky_downloads"
        private const val CACHE_EXPIRY_HOURS = 1
    }

    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloads = mutableMapOf<String, Long>() // packageName to downloadId

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
            // Clean up partial file
            getCacheFile(packageName)?.delete()
        }
    }

    private fun getCacheDir(): File {
        val cacheDir = File(context.externalCacheDir, CACHE_DIR)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir
    }

    private fun getCacheFile(packageName: String): File? {
        return File(getCacheDir(), "$packageName.apk")
    }

    private fun cleanOldCache() {
        scope.launch {
            val cacheDir = getCacheDir()
            val cutoffTime = System.currentTimeMillis() - (CACHE_EXPIRY_HOURS * 60 * 60 * 1000)

            cacheDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    file.delete()
                }
            }
        }
    }

    suspend fun install(app: FDroidApp, onProgress: (Float) -> Unit = {}): Boolean {
        // Check for cached file first
        val cachedFile = getCacheFile(app.packageName)
        if (cachedFile?.exists() == true) {
            // Verify and install from cache
            if (app.sha256.isBlank() || verifySha256File(cachedFile, app.sha256)) {
                return installApk(cachedFile.toUri(), app.packageName)
            } else {
                cachedFile.delete() // Invalid cache, delete it
            }
        }

        // Check install permission
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
        } catch (e: Exception) {
            e.printStackTrace()
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
        return when(repository) {
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
        val cacheFile = getCacheFile(app.packageName) ?: return@withContext false

        val req = DownloadManager.Request(downloadUrl.toUri())
            .setTitle("${app.name} ${app.version}")
            .setDescription("Downloading from ${app.repository}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationUri(cacheFile.toUri())

        val downloadId = dm.enqueue(req)
        activeDownloads[app.packageName] = downloadId

        val uri = monitorDownload(downloadId, onProgress)
        activeDownloads.remove(app.packageName)

        if (uri == null) {
            cacheFile.delete()
            return@withContext false
        }

        if (app.sha256.isNotBlank() && !verifySha256(uri, app.sha256)) {
            cacheFile.delete()
            return@withContext false
        }

        installApk(uri, app.packageName)
    }

    private fun installApk(uri: Uri, packageName: String): Boolean {
        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(install)

        // Schedule cache cleanup after install attempt
        scope.launch {
            delay(50000) // Wait 50 secs
            getCacheFile(packageName)?.let { file ->
                if (file.exists() && file.lastModified() < System.currentTimeMillis() - 60000) {
                    file.delete()
                }
            }
        }

        return true
    }

    private suspend fun monitorDownload(id: Long, onProgress: (Float) -> Unit): Uri? =
        withContext(Dispatchers.IO) {
            val query = DownloadManager.Query().setFilterById(id)
            var downloading = true
            var lastProgress = -1f

            while (downloading) {
                val cursor = dm.query(query)
                if (cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusIdx)

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            downloading = false
                            val uri = dm.getUriForDownloadedFile(id)
                            cursor.close()
                            onProgress(1f)
                            return@withContext uri
                        }
                        DownloadManager.STATUS_FAILED -> {
                            downloading = false
                            cursor.close()
                            return@withContext null
                        }
                        DownloadManager.STATUS_RUNNING -> {
                            val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                            if (bytesIdx != -1 && totalIdx != -1) {
                                val bytes = cursor.getLong(bytesIdx)
                                val total = cursor.getLong(totalIdx)

                                if (total > 0) {
                                    val progress = bytes.toFloat() / total.toFloat()
                                    if (progress != lastProgress) {
                                        lastProgress = progress
                                        onProgress(progress)
                                    }
                                }
                            }
                        }
                    }
                    cursor.close()
                }

                if (downloading) {
                    delay(50)
                }
            }
            null
        }

    private fun verifySha256(uri: Uri, expectedHex: String): Boolean {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return false
            pfd.use {
                val fis = FileInputStream(it.fileDescriptor)
                val md = MessageDigest.getInstance("SHA-256")
                val buf = ByteArray(8192)
                var r = fis.read(buf)
                while (r != -1) {
                    md.update(buf, 0, r)
                    r = fis.read(buf)
                }
                val actual = md.digest().joinToString("") { byte -> "%02x".format(byte) }
                actual.equals(expectedHex, ignoreCase = true)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun verifySha256File(file: File, expectedHex: String): Boolean {
        return try {
            file.inputStream().use { fis ->
                val md = MessageDigest.getInstance("SHA-256")
                val buf = ByteArray(8192)
                var r = fis.read(buf)
                while (r != -1) {
                    md.update(buf, 0, r)
                    r = fis.read(buf)
                }
                val actual = md.digest().joinToString("") { byte -> "%02x".format(byte) }
                actual.equals(expectedHex, ignoreCase = true)
            }
        } catch (e: Exception) {
            false
        }
    }
}