package app.flicky.install

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.flicky.R
import app.flicky.data.local.AppVariant
import app.flicky.data.model.FDroidApp
import app.flicky.data.remote.MirrorRegistry
import app.flicky.data.repository.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
        val existedBefore = cacheFileFor(req).exists()
        val file = download(req) { p -> onProgress(0.5f * p) } ?: return false

        if (req.sha256.isNotBlank() && !verifySha256File(file, req.sha256)) {
            file.delete(); return false
        }

        val installProgress: (Float) -> Unit = { p -> onProgress(0.5f + 0.5f * p) }
        val ok = when (mode) {
            0 -> installSystem(file)
            1 -> installSessionFromFile(file, req.packageName, req.sha256, installProgress)
            2 -> installRootStream(file, installProgress)
            3 -> installShizukuStream(file, installProgress)
            else -> installSystem(file)
        }

        if (!settings.settingsFlow.first().keepCache && !existedBefore) {
            scheduleCleanup(file)
        }
        return ok
    }

    private data class ResolvedApk(
        val packageName: String,
        val title: String,
        val urls: List<String>,
        val sha256: String,
        val size: Long
    )

    private fun normalize(urlOrId: String) = urlOrId.trim().trimEnd('/')

    private suspend fun resolve(app: FDroidApp): ResolvedApk? {
        val title = "${app.name} ${app.version}"
        // Prefer repositoryUrl (canonical) for mirror rotation when resolving a path
        val baseId = app.repositoryUrl.ifBlank { app.repository }
        val urls = resolveUrls(baseId, app.apkUrl)
        return ResolvedApk(
            packageName = app.packageName,
            title = title,
            urls = urls,
            sha256 = app.sha256,
            size = app.size
        )
    }

    private suspend fun resolve(variant: AppVariant): ResolvedApk? {
        val title = "${variant.packageName} ${variant.versionName}"
        val urls = resolveUrls(variant.repositoryUrl, variant.apkUrl)
        return ResolvedApk(
            packageName = variant.packageName,
            title = title,
            urls = urls,
            sha256 = variant.sha256,
            size = variant.size
        )
    }

    private suspend fun resolveUrls(repoIdOrUrl: String, apkPathOrUrl: String): List<String> {
        if (apkPathOrUrl.startsWith("http://") || apkPathOrUrl.startsWith("https://")) {
            return listOf(apkPathOrUrl)
        }

        val base = resolveBase(repoIdOrUrl) // canonical base
        val rotate = settings.settingsFlow.first().mirrorRotation
        val includeOnion = settings.settingsFlow.first().useOnionMirrors

        val bases = if (rotate && MirrorRegistry.hasMirrors(base)) {
            MirrorRegistry.candidates(base, includeOnion)
        } else listOf(base)

        val path = apkPathOrUrl.trimStart('/')
        return bases.map { b -> "${normalize(b)}/$path" }
    }

    private suspend fun resolveBase(repo: String): String {
        if (repo.startsWith("http")) return normalize(repo)
        val repos = runCatching { settings.repositoriesFlow.first() }.getOrElse { emptyList() }
        val norm = normalize(repo)
        val byName = repos.firstOrNull { it.name.equals(norm, true) }
        val byUrl = repos.firstOrNull { normalize(it.url).equals(norm, true) }
        return normalize(byName?.url ?: byUrl?.url ?: "https://f-droid.org/repo")
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

        val base = getBaseCacheDir()
        val canDirectWrite = base == context.externalCacheDir

        for ((idx, url) in req.urls.withIndex()) {
            val desc = try {
                context.getString(R.string.settings_downloads)
            } catch (_: Exception) {
                "Downloading"
            }
            val request = DownloadManager.Request(url.toUri())
                .setTitle(req.title)
                .setDescription("$desc (${idx + 1}/${req.urls.size})")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)

            if (canDirectWrite) {
                request.setDestinationUri(Uri.fromFile(out))
            }
            val id = dm.enqueue(request)
            activeDownloads[out.name] = id
            val uri = monitorDownload(id, onProgress)
            activeDownloads.remove(out.name)

            if (uri != null) {
                // On Android 10+, DM may not honor custom paths; copy back if needed
                if (!out.exists()) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { src ->
                            out.outputStream().use { dst -> src.copyTo(dst) }
                        }
                    }
                }
                if (out.exists()) return@withContext out
                // else try next mirror
            } else {
                // Clean and try next mirror
                runCatching { out.delete() }
            }
        }
        null
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
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            onProgress(1f)
                            return@withContext dm.getUriForDownloadedFile(id)
                        }
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
        FileInputStream(file).use { fis ->
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(8192)
            var r = fis.read(buf)
            while (r != -1) { md.update(buf, 0, r); r = fis.read(buf) }
            md.digest().joinToString("") { "%02x".format(it) }.equals(expectedHex, true)
        }
    } catch (_: Exception) {
        false
    }

    private fun installSystem(file: File): Boolean {
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

    private suspend fun installSessionFromFile(
        file: File,
        packageName: String,
        expectedSha256: String = "",
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = "package:${context.packageName}".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(i)
                return false
            }
        }
        if (expectedSha256.isNotBlank()) {
            val ok = try {
                verifySha256File(file, expectedSha256)
            } catch (_: Exception) {
                false
            }
            if (!ok) return false
        }

        val pm = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply { setAppPackageName(packageName) }

        val sessionId = pm.createSession(params)
        val session = pm.openSession(sessionId)

        val total = file.length().coerceAtLeast(1L)
        FileInputStream(file).use { fis ->
            session.openWrite("base.apk", 0, -1).use { out ->
                val buf = ByteArray(64 * 1024)
                var written = 0L
                var r = fis.read(buf)
                while (r != -1) {
                    out.write(buf, 0, r)
                    written += r
                    // Report raw [0..1] to be scaled by caller
                    onProgress(written.toFloat() / total.toFloat())
                    r = fis.read(buf)
                }
                session.fsync(out)
            }
        }
        val result = CompletableDeferred<Int>()
        val waitJob = CoroutineScope(Dispatchers.Default).launch {
            val (_, status) = SessionInstallBus.events.first { it.first == sessionId }
            result.complete(status)
        }

        val intent = Intent("app.flicky.INSTALL_RESULT").apply {
            component = ComponentName(context, InstallResultReceiver::class.java)
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pending = PendingIntent.getBroadcast(context, sessionId, intent, pendingFlags)

        session.commit(pending.intentSender)
        session.close()

        val status = try {
            withTimeout(60_000) { result.await() }
        } finally {
            waitJob.cancel()
        }
        return status == PackageInstaller.STATUS_SUCCESS

    }

    private suspend fun installRootStream(file: File, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val size = file.length().coerceAtLeast(1L)
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "cmd package install -r -S $size"))
            FileInputStream(file).use { fis ->
                proc.outputStream.use { os -> pipeWithProgress(fis, os, size, onProgress) }
            }
            proc.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun installShizukuStream(file: File, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
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
            } finally {
                runCatching { proc.destroy() }
            }
            ok
        } catch (_: Exception) {
            false
        }
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
            getBaseCacheDir().listFiles()?.forEach { f ->
                if (f.lastModified() < cutoff) runCatching { f.delete() }
            }
        }
    }
}