package app.flicky.install

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.flicky.data.local.AppVariant
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
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloads = mutableMapOf<String, Long>()

    companion object {
        private const val CACHE_DIR = "flicky_downloads"
        private const val CACHE_EXPIRY_HOURS = 1
    }

    init { cleanOldCache() }

    fun open(packageName: String) {
        context.packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(it)
        }
    }
    fun uninstall(packageName: String) {
        val i = Intent(Intent.ACTION_DELETE, "package:$packageName".toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(i)
    }
    fun cancelDownload(packageName: String) {
        activeDownloads.entries.filter { it.key.startsWith("$packageName-") }.forEach { (_, id) ->
            dm.remove(id)
        }
        activeDownloads.keys.removeAll { it.startsWith("$packageName-") }
        getBaseCacheDir().listFiles()?.forEach { f ->
            if (f.name.startsWith("$packageName-")) runCatching { f.delete() }
        }
    }

    suspend fun install(app: FDroidApp, onProgress: (Float) -> Unit = {}): Boolean {
        val req = resolve(app) ?: return false
        return installResolved(req, onProgress)
    }
    suspend fun install(variant: AppVariant, onProgress: (Float) -> Unit = {}): Boolean {
        val req = resolve(variant) ?: return false
        return installResolved(req, onProgress)
    }

    private suspend fun installResolved(req: ResolvedApk, onProgress: (Float) -> Unit): Boolean {
        val mode = settings.settingsFlow.first().installerMode

        val file = download(req) { p -> onProgress(0.5f * p) } ?: return false

        if (req.sha256.isNotBlank() && !verifySha256File(file, req.sha256)) {
            file.delete(); return false
        }

        // Install (progress 0.5..1.0)
        val installProgress: (Float) -> Unit = { p -> onProgress(0.5f + 0.5f * p) }
        val ok = when (mode) {
            0 -> installSystem(file, req.packageName)
            1 -> SessionInstaller(context).installFromFile(file, req.packageName, req.sha256, installProgress)
            2 -> installRootStream(file, req.packageName, installProgress)
            3 -> installShizukuStream(file, installProgress)
            else -> installSystem(file, req.packageName)
        }

        if (!settings.settingsFlow.first().keepCache) {
            scheduleCleanup(file)
        }
        return ok
    }


    private data class ResolvedApk(
        val packageName: String,
        val title: String,
        val url: String,
        val sha256: String,
        val size: Long
    )

    private suspend fun resolve(app: FDroidApp): ResolvedApk? {
        val base = resolveBase(app.repository)
        val url = if (app.apkUrl.startsWith("http")) app.apkUrl
        else base.trimEnd('/') + "/" + app.apkUrl.trimStart('/')
        return ResolvedApk(
            packageName = app.packageName,
            title = "${app.name} ${app.version}",
            url = url,
            sha256 = app.sha256,
            size = app.size
        )
    }

    private fun resolve(variant: AppVariant): ResolvedApk? {
        val base = if (variant.repositoryUrl.startsWith("http")) variant.repositoryUrl else "https://f-droid.org/repo"
        val url = if (variant.apkUrl.startsWith("http")) variant.apkUrl
        else base.trimEnd('/') + "/" + variant.apkUrl.trimStart('/')
        return ResolvedApk(
            packageName = variant.packageName,
            title = "${variant.packageName} ${variant.versionName}",
            url = url,
            sha256 = variant.sha256,
            size = variant.size
        )
    }

    private suspend fun resolveBase(repo: String): String {
        if (repo.startsWith("http")) return repo
        val repos = runCatching { settings.repositoriesFlow.first() }.getOrElse { emptyList() }
        val byName = repos.firstOrNull { it.name.equals(repo, true) }
        val byUrl  = repos.firstOrNull { it.url.equals(repo, true) }
        return (byName?.url ?: byUrl?.url ?: "https://f-droid.org/repo").trimEnd('/')
    }

    private fun getBaseCacheDir(): File {
        val base = context.externalCacheDir ?: context.cacheDir
        return File(base, CACHE_DIR).apply { mkdirs() }
    }

    private fun cacheFileFor(req: ResolvedApk): File {
        val key = if (req.sha256.isNotBlank()) req.sha256.take(12) else req.size.toString()
        return File(getBaseCacheDir(), "${req.packageName}-$key.apk")
    }

    private suspend fun download(req: ResolvedApk, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        val out = cacheFileFor(req)
        if (out.exists()) return@withContext out

        val request = DownloadManager.Request(req.url.toUri())
            .setTitle(req.title)
            .setDescription("Downloading")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationUri(Uri.fromFile(out))

        val id = dm.enqueue(request)
        activeDownloads[out.name] = id

        val uri = monitorDownload(id, onProgress)
        activeDownloads.remove(out.name)

        if (uri == null) { out.delete(); return@withContext null }
        out
    }

    @SuppressLint("Range")
    private suspend fun monitorDownload(id: Long, onProgress: (Float) -> Unit): Uri? = withContext(Dispatchers.IO) {
        val q = DownloadManager.Query().setFilterById(id)
        var last = -1f
        while (isActive) {
            val c = dm.query(q)
            try {
                if (c != null && c.moveToFirst()) {
                    val statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIdx == -1) {
                        delay(100)
                        continue
                    }
                    when (c.getInt(statusIdx)) {
                        DownloadManager.STATUS_SUCCESSFUL -> { onProgress(1f); return@withContext dm.getUriForDownloadedFile(id) }
                        DownloadManager.STATUS_FAILED -> return@withContext null

                        DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> {
                            val soFarIdx = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val totalIdx = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            if (soFarIdx != -1 && totalIdx != -1) {
                                val total = c.getLong(totalIdx)
                                if (total > 0) {
                                    val p = c.getLong(soFarIdx).toFloat() / total.toFloat()
                                    if (p != last) { last = p; onProgress(p) }
                                }
                            }
                        }
                    }
                }
            } finally { c?.close() }
            delay(100)
        }
        null
    }

    private fun verifySha256File(file: File, expectedHex: String): Boolean = try {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(8192)
            var r = fis.read(buf)
            while (r != -1) { md.update(buf, 0, r); r = fis.read(buf) }
        }
        md.digest().joinToString("") { "%02x".format(it) }.equals(expectedHex, true)
    } catch (_: Exception) { false }

    private fun installSystem(file: File, packageName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i)
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        }.getOrDefault(false)
    }

    private suspend fun installRootStream(file: File, packageName: String, onProgress: (Float) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val size = file.length().coerceAtLeast(1L)
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "cmd package install -r -S $size"))
                FileInputStream(file).use { fis ->
                    proc.outputStream.use { os -> pipeWithProgress(fis, os, size, onProgress) }
                }
                proc.waitFor() == 0
            } catch (_: Exception) { false }
        }

    private suspend fun installShizukuStream(file: File, onProgress: (Float) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            if (!Shizuku.pingBinder()) return@withContext false
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if (!requestShizukuPermission()) return@withContext false
            }
            try {
                val size = file.length().coerceAtLeast(1L)
                val proc = shizukuNewProcess(arrayOf("cmd", "package", "install", "-r", "-S", size.toString()))
                    ?: return@withContext false
                val ok = try {
                    FileInputStream(file).use { fis ->
                        proc.outputStream.use { os -> pipeWithProgress(fis, os, size, onProgress) }
                    }
                    proc.waitFor() == 0
                } finally { runCatching { proc.destroy() } }
                ok
            } catch (_: Exception) { false }
        }

    private suspend fun requestShizukuPermission(timeoutMs: Long = 15_000): Boolean {
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true
        val deferred = CompletableDeferred<Boolean>()
        val listener = Shizuku.OnRequestPermissionResultListener { _, res ->
            deferred.complete(res == PackageManager.PERMISSION_GRANTED)
        }
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.requestPermission(0)
        val granted = runCatching { withTimeout(timeoutMs) { deferred.await() } }.getOrDefault(false)
        Shizuku.removeRequestPermissionResultListener(listener)
        return granted
    }

    @Suppress("UNCHECKED_CAST")
    private fun shizukuNewProcess(cmd: Array<String>, env: Array<String>? = null, dir: String? = null): Process? = try {
        val m: Method = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        m.invoke(null, cmd, env, dir) as Process
    } catch (_: Exception) { null }

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

    private fun scheduleCleanup(file: File) {
        scope.launch {
            delay(120_000)
            runCatching { if (file.exists()) file.delete() }
        }
    }

    private fun cleanOldCache() {
        scope.launch {
            val cutoff = System.currentTimeMillis() - CACHE_EXPIRY_HOURS * 60L * 60L * 1000L
            getBaseCacheDir().listFiles()?.forEach { f -> if (f.lastModified() < cutoff) runCatching { f.delete() } }
        }
    }
}