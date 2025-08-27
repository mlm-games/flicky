package app.flicky.install

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.flicky.data.model.FDroidApp
import app.flicky.data.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.security.MessageDigest

class Installer(
    private val context: Context,
    private val settings: SettingsRepository
) {
    companion object {
        private const val CACHE_DIR = "flicky_downloads"
        private const val CACHE_EXPIRY_HOURS = 1
    }

    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloads = mutableMapOf<String, Long>()

    init {
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
        activeDownloads[packageName]?.let { id ->
            dm.remove(id)
            activeDownloads.remove(packageName)
            getCacheFile(packageName).delete()
        }
    }

    private fun getBaseCacheDir(): File {
        val base = context.externalCacheDir ?: context.cacheDir
        return File(base, CACHE_DIR).apply { mkdirs() }
    }

    private fun getCacheFile(packageName: String): File = File(getBaseCacheDir(), "$packageName.apk")

    private fun cleanOldCache() {
        scope.launch {
            val cutoff = System.currentTimeMillis() - (CACHE_EXPIRY_HOURS * 60L * 60L * 1000L)
            getBaseCacheDir().listFiles()?.forEach { f ->
                if (f.lastModified() < cutoff) runCatching { f.delete() }
            }
        }
    }

    suspend fun install(app: FDroidApp, onProgress: (Float) -> Unit = {}): Boolean {
        val mode = settings.settingsFlow.first().installerMode
        return when (mode) {
            0 -> installSystem(app, onProgress)
            1 -> installSession(app, onProgress)
            2 -> installRoot(app, onProgress)
            3 -> installShizuku(app, onProgress)
            else -> installSystem(app, onProgress)
        }
    }

    private suspend fun installSystem(app: FDroidApp, onProgress: (Float) -> Unit): Boolean {
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
        val (repoLabel, downloadUrl) = constructDownloadInfo(app)
        return downloadAndInstallSystem(app, repoLabel, downloadUrl, onProgress)
    }

    private suspend fun installSession(app: FDroidApp, onProgress: (Float) -> Unit): Boolean {
        return try {
            SessionInstaller(context).install(app, onProgress)
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun installRoot(app: FDroidApp, onProgress: (Float) -> Unit): Boolean {
        val file = downloadToCache(app, onProgress) ?: return false
        if (app.sha256.isNotBlank() && !verifySha256File(file, app.sha256)) {
            file.delete()
            return false
        }
        val ok = runSu(arrayOf("pm", "install", "-r", file.absolutePath))
        scheduleCleanup(file)
        return ok
    }

    private suspend fun installShizuku(app: FDroidApp, onProgress: (Float) -> Unit): Boolean {
        if (!Shizuku.pingBinder()) return false

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            val granted = requestShizukuPermission()
            if (!granted) return false
        }

        val file = downloadToCache(app, onProgress) ?: return false
        if (app.sha256.isNotBlank() && !verifySha256File(file, app.sha256)) {
            file.delete()
            return false
        }

        val size = file.length().coerceAtLeast(1L)
        val cmd = arrayOf("cmd", "package", "install", "-r", "-S", size.toString())

        val proc = shizukuNewProcess(cmd) ?: return false
        val ok = try {
            FileInputStream(file).use { fis ->
                proc.outputStream.use { os -> pipeWithProgress(fis, os, size, onProgress) }
            }
            val exit = proc.waitFor()
            exit == 0
        } catch (_: Exception) {
            false
        } finally {
            runCatching { proc.destroy() }
        }

        scheduleCleanup(file)
        return ok
    }

    private suspend fun requestShizukuPermission(timeoutMs: Long = 15_000): Boolean {
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true
        val deferred = CompletableDeferred<Boolean>()
        val listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            deferred.complete(grantResult == PackageManager.PERMISSION_GRANTED)
        }
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.requestPermission(0)
        val granted = runCatching { withTimeout(timeoutMs) { deferred.await() } }.getOrDefault(false)
        Shizuku.removeRequestPermissionResultListener(listener)
        return granted
    }

    @Suppress("UNCHECKED_CAST")
    private fun shizukuNewProcess(cmd: Array<String>, env: Array<String>? = null, dir: String? = null): Process? {
        return try {
            val m: Method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            m.isAccessible = true
            m.invoke(null, cmd, env, dir) as Process
        } catch (e: Exception) {
            null
        }
    }

    private fun scheduleCleanup(file: File) {
        scope.launch {
            delay(120_000)
            runCatching { if (file.exists()) file.delete() }
        }
    }

    private fun pipeWithProgress(src: FileInputStream, dst: OutputStream, total: Long, onProgress: (Float) -> Unit) {
        val buf = ByteArray(64 * 1024)
        var written = 0L
        var r = src.read(buf)
        while (r != -1) {
            dst.write(buf, 0, r)
            written += r
            onProgress((written.toFloat() / total.toFloat()).coerceIn(0f, 1f))
            r = src.read(buf)
        }
        dst.flush()
    }

    private fun runSu(cmd: Array<String>): Boolean {
        return try {
            val args = arrayOf("su", "-c", cmd.joinToString(" "))
            val proc = Runtime.getRuntime().exec(args)
            val code = proc.waitFor()
            code == 0
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun downloadToCache(app: FDroidApp, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        val cacheFile = getCacheFile(app.packageName)
        if (cacheFile.exists()) return@withContext cacheFile

        val (repoLabel, downloadUrl) = constructDownloadInfo(app)
        val req = DownloadManager.Request(downloadUrl.toUri())
            .setTitle("${app.name} ${app.version}")
            .setDescription("Downloading from $repoLabel")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationUri(Uri.fromFile(cacheFile))

        val id = dm.enqueue(req)
        activeDownloads[app.packageName] = id

        val finishedUri = monitorDownload(id, onProgress)
        activeDownloads.remove(app.packageName)

        if (finishedUri == null) {
            cacheFile.delete()
            return@withContext null
        }
        cacheFile
    }

    private suspend fun downloadAndInstallSystem(
        app: FDroidApp,
        repoLabel: String,
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val cacheFile = getCacheFile(app.packageName)

        val req = DownloadManager.Request(downloadUrl.toUri())
            .setTitle("${app.name} ${app.version}")
            .setDescription("Downloading from $repoLabel")
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

        if (app.sha256.isNotBlank() && !verifySha256File(cacheFile, app.sha256)) {
            cacheFile.delete()
            return@withContext false
        }

        val ok = installApk(fileProviderUri(cacheFile), app.packageName)
        scheduleCleanup(cacheFile)
        ok
    }

    private suspend fun constructDownloadInfo(app: FDroidApp): Pair<String, String> {
        if (app.apkUrl.startsWith("http://") || app.apkUrl.startsWith("https://")) {
            return app.repository to app.apkUrl
        }
        val repos = runCatching { settings.repositoriesFlow.first() }.getOrElse { emptyList() }
        val byName = repos.firstOrNull { it.name.equals(app.repository, ignoreCase = true) }
        val byUrl = repos.firstOrNull { it.url.equals(app.repository, ignoreCase = true) }
        val chosen = byName ?: byUrl
        val base = when {
            chosen != null -> chosen.url.trimEnd('/')
            app.repository.startsWith("http://") || app.repository.startsWith("https://") -> app.repository.trimEnd('/')
            else -> "https://f-droid.org/repo"
        }

        val finalUrl = if (app.apkUrl.startsWith("/")) base + app.apkUrl else "$base/${app.apkUrl}"
        val label = chosen?.name ?: app.repository
        return label to finalUrl
    }

    private suspend fun monitorDownload(id: Long, onProgress: (Float) -> Unit): Uri? = withContext(Dispatchers.IO) {
        val query = DownloadManager.Query().setFilterById(id)
        var last = -1f
        while (isActive) {
            val cursor = dm.query(query)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIdx == -1) {
                        delay(100)
                        continue
                    }
                    when (cursor.getInt(statusIdx)) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            onProgress(1f)
                            return@withContext dm.getUriForDownloadedFile(id)
                        }
                        DownloadManager.STATUS_FAILED -> return@withContext null
                        DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> {
                            val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            if (bytesIdx != -1 && totalIdx != -1) {
                                val total = cursor.getLong(totalIdx)
                                if (total > 0) {
                                    val p = cursor.getLong(bytesIdx).toFloat() / total.toFloat()
                                    if (p != last) { last = p; onProgress(p) }
                                }
                            }
                        }
                        else -> { /* ignore */ }
                    }
                }
            } finally {
                cursor?.close()
            }
            delay(100)
        }
        null
    }

    private fun fileProviderUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun installApk(uri: Uri, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun verifySha256File(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(8192)
            var r = fis.read(buf)
            while (r != -1) {
                md.update(buf, 0, r)
                r = fis.read(buf)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun verifySha256File(file: File, expectedHex: String): Boolean {
        return try {
            verifySha256File(file).equals(expectedHex, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }
}