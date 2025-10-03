package app.flicky.install

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.flicky.AppGraph
import app.flicky.R
import app.flicky.data.local.AppVariant
import app.flicky.data.local.RepoConfig
import app.flicky.data.model.FDroidApp
import app.flicky.data.remote.HttpClientProvider
import app.flicky.data.remote.MirrorPolicyProvider
import app.flicky.data.remote.MirrorRegistry
import app.flicky.data.remote.MirrorRegistry.Strategy
import app.flicky.data.repository.PreferredRepo
import app.flicky.data.repository.SettingsRepository
import app.flicky.data.repository.VariantSelector
import app.flicky.helper.DebugLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.resume

class Installer(
    private val context: Context,
    private val settings: SettingsRepository,
    private val mirrorPolicies: MirrorPolicyProvider,
    private val httpClients: HttpClientProvider
) {
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloads = ConcurrentHashMap<String, Long>()
    private val cancelFlags = ConcurrentHashMap<String, Boolean>()
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val activeProcs = ConcurrentHashMap<String, Process>()

    private val _tasks = MutableStateFlow<Map<String, TaskStage>>(emptyMap())
    val tasks: StateFlow<Map<String, TaskStage>> = _tasks.asStateFlow()

    private fun isCancelled(pkg: String) = cancelFlags[pkg] == true

    private fun emitStage(pkg: String, stage: TaskStage) {
        _tasks.update { it + (pkg to stage) }
    }

    companion object {
        private const val CACHE_DIR = "flicky_downloads"
        private const val CACHE_EXPIRY_HOURS = 1
        private const val STREAM_BUF = 64 * 1024
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

    fun cancel(packageName: String) {
        cancelFlags[packageName] = true

        // Cancel downloads
        activeDownloads.entries
            .filter { it.key.startsWith("$packageName-") }
            .toList()
            .forEach { (key, id) ->
                dm.remove(id)
                activeDownloads.remove(key)
            }

        // Cancel any active OkHttp call
        activeCalls.remove(packageName)?.cancel()

        // Kill any active root/shizuku process with proper cleanup
        activeProcs.remove(packageName)?.let { process ->
            runCatching {
                process.destroyForciblyCompat()
                // Don't wait here as it might block
            }
        }

        // Best effort: remove partial files
        scope.launch(Dispatchers.IO) {
            getBaseCacheDir().listFiles()?.forEach { f ->
                if (f.name.startsWith("$packageName-")) {
                    runCatching { f.delete() }
                }
            }
        }

        // Notify UI
        emitStage(packageName, TaskStage.Cancelled)
    }

    private fun clearCancel(packageName: String) { cancelFlags.remove(packageName) }

    fun clearDownloadCache() {
        getBaseCacheDir().listFiles()?.forEach { f -> runCatching { f.delete() } }
    }

    suspend fun install(app: FDroidApp): Boolean {
        val pref = PreferredRepo.fromIndex(settings.settingsFlow.first().preferredRepo)
        val variants = runCatching { AppGraph.db.appDao().variantsFor(app.packageName) }
            .getOrElse { emptyList() }
        val chosen = VariantSelector.pick(variants, pref)
        val req = when {
            chosen != null -> resolve(chosen)
            else -> resolve(app)
        } ?: return false

        return installResolved(req)
    }

    suspend fun install(variant: AppVariant): Boolean {
        val req = resolve(variant) ?: return false
        return installResolved(req)
    }

    private suspend fun executeWithProcess(
        packageName: String,
        processBuilder: () -> Process?,
        pipeData: suspend (Process) -> Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        var process: Process? = null

        try {
            process = processBuilder()
            if (process == null) return@withContext false

            activeProcs[packageName] = process

            // Execute the actual operation
            val result = pipeData(process)

            // Wait for process completion with timeout
            val exitCode = withTimeoutOrNull(30_000) {
                process.waitFor()
            }

            return@withContext result && exitCode == 0

        } catch (e: Exception) {
            DebugLog.log("Installer", "Process execution failed: ${e.message}")
            false
        } finally {
            // Ensure cleanup happens even if coroutine is cancelled
            withContext(NonCancellable) {
                activeProcs.remove(packageName)

                // Force destroy if still running
                process?.let {
                    if (it.isAliveCompat()) {
                        it.destroyForciblyCompat()
                        // Wait a bit for process to terminate
                        withTimeoutOrNull(1000) {
                            it.waitFor()
                        }
                    }
                }
            }
        }
    }



    private suspend fun installResolved(req: ResolvedApk): Boolean = withContext(NonCancellable) {
        val mode = settings.settingsFlow.first().installerMode
        val showDebug = runCatching { settings.settingsFlow.first().showDebugInfo }.getOrDefault(false)

        val existedBefore = cacheFileFor(req).exists()
        if (showDebug) DebugLog.log("Installer", "Starting ${req.packageName} via mode=$mode")

        emitStage(req.packageName, TaskStage.Downloading(0f))
        val file = download(req) ?: run {
            if (isCancelled(req.packageName))
                emitStage(req.packageName, TaskStage.Cancelled)
            else
                emitStage(req.packageName, TaskStage.Finished(false))
            if (showDebug) DebugLog.log("Installer", "Download failed for ${req.packageName}")
            clearCancel(req.packageName)
            return@withContext false
        }

        emitStage(req.packageName, TaskStage.Verifying)
        if (isCancelled(req.packageName)) {
            emitStage(req.packageName, TaskStage.Cancelled); clearCancel(req.packageName)
            return@withContext false }
        if (req.sha256.isNotBlank() && !verifySha256File(file, req.sha256)) {
            if (showDebug) DebugLog.log("Installer", "SHA256 mismatch for ${req.packageName}")
            file.delete()
            emitStage(req.packageName, TaskStage.Finished(false))
            clearCancel(req.packageName)
            return@withContext false
        }

        val result = when (mode) {
            0 -> { emitStage(req.packageName, TaskStage.Installing(0f)); InstallSessionResult(installSystem(file, req.packageName)) }
            1 -> installSessionFromFile(file, req.packageName, req.sha256)
            2 -> InstallSessionResult(installRootStream(file, req.packageName))
            3 -> InstallSessionResult(installShizukuStream(file, req.packageName))
            else -> { emitStage(req.packageName, TaskStage.Installing(0f)); InstallSessionResult(installSystem(file, req.packageName)) }
        }

        if (isCancelled(req.packageName)) {
            emitStage(req.packageName, TaskStage.Cancelled)
        } else if (result.wasCancelledByUser) {
            emitStage(req.packageName, TaskStage.Cancelled)
        } else {
            emitStage(req.packageName, TaskStage.Finished(result.success))
        }

        if (showDebug) DebugLog.log("Installer", "Install ${if (result.success) "succeeded" else "failed"} for ${req.packageName} (cancelled=${result.wasCancelledByUser})")

        if (!settings.settingsFlow.first().keepCache && !existedBefore) {
            scheduleCleanup(file)
        }
        clearCancel(req.packageName)
        return@withContext result.success && !result.wasCancelledByUser
    }

    private data class ResolvedApk(
        val packageName: String,
        val title: String,
        val urls: List<String>,
        val sha256: String,
        val size: Long,
        val repoBase: String,
        val trustMode: String
    )

    private fun normalize(urlOrId: String) = urlOrId.trim().trimEnd('/')

    private suspend fun resolve(app: FDroidApp): ResolvedApk? {
        val title = "${app.name} ${app.version}"
        val base = resolveBase(app.repositoryUrl.ifBlank { app.repository })
        val (dlBase, trustMode) = resolveDownloadBaseAndTrust(base)
        val urls = resolveUrls(dlBase, app.apkUrl)
        return ResolvedApk(app.packageName, title, urls, app.sha256, app.size, dlBase, trustMode)
    }

    private suspend fun resolve(variant: AppVariant): ResolvedApk? {
        val title = "${variant.packageName} ${variant.versionName}"
        val base = resolveBase(variant.repositoryUrl)
        val (dlBase, trustMode) = resolveDownloadBaseAndTrust(base)
        val urls = resolveUrls(dlBase, variant.apkUrl)
        return ResolvedApk(variant.packageName, title, urls, variant.sha256, variant.size, dlBase, trustMode)
    }

    private suspend fun resolveDownloadBaseAndTrust(baseUrl: String): Pair<String, String> {
        val cfg: RepoConfig? = runCatching { AppGraph.db.repoConfigDao().get(baseUrl) }.getOrNull()
        val dlBase = normalize(cfg?.downloadBase?.takeIf { it.isNotBlank() } ?: baseUrl)
        val trust = cfg?.trustMode ?: "HttpsOnly"
        return dlBase to trust
    }

    private suspend fun resolveUrls(repoBase: String, apkPathOrUrl: String): List<String> {
        // Absolute URLs: try repo base or any known mirror,
        if (apkPathOrUrl.startsWith("http://") || apkPathOrUrl.startsWith("https://")) {
            val abs = normalize(apkPathOrUrl)
            val policy = mirrorPolicies.policyFor(repoBase)
            val basesRaw = MirrorRegistry.candidates(
                base = repoBase,
                includeOnion = policy.includeOnion,
                strategy = if (policy.rotateMirrors) policy.strategy else Strategy.StickyLastGood
            )

            // downloadBase & mirror candidates
            val (dlBase, trustMode) = resolveDownloadBaseAndTrust(repoBase)
            val allBasesToMatch = (listOf(normalize(dlBase)) + basesRaw).distinct()

            val matchedBase = allBasesToMatch.firstOrNull { base ->
                val prefix = if (abs.startsWith("$base/")) "$base/" else base
                abs.startsWith(prefix)
            }

            if (matchedBase != null) {
                val prefix = if (abs.startsWith("$matchedBase/")) "$matchedBase/" else matchedBase
                val relPath = abs.removePrefix(prefix).trimStart('/')
                val filteredBases = when {
                    trustMode.equals("HttpsOnly", true) ||
                            trustMode.equals("Pinned", true) ||
                            trustMode.equals("CustomCA", true) -> (listOf(normalize(dlBase)) + basesRaw)
                        .distinct()
                        .filter { it.startsWith("https://") || (policy.includeOnion && it.contains(".onion")) }
                    else -> (listOf(normalize(dlBase)) + basesRaw).distinct()
                }
                return filteredBases.map { b -> "${normalize(b)}/$relPath" }
            }

            return listOf(abs)
        }

        // relative path
        val policy = mirrorPolicies.policyFor(repoBase)
        val basesRaw = MirrorRegistry.candidates(
            base = repoBase,
            includeOnion = policy.includeOnion,
            strategy = if (policy.rotateMirrors) policy.strategy else Strategy.StickyLastGood
        )
        val (dlBase, trustMode) = resolveDownloadBaseAndTrust(repoBase)
        val filteredBases = when {
            trustMode.equals("HttpsOnly", true) ||
                    trustMode.equals("Pinned", true) ||
                    trustMode.equals("CustomCA", true) -> (listOf(normalize(dlBase)) + basesRaw)
                .distinct()
                .filter { it.startsWith("https://") || (policy.includeOnion && it.contains(".onion")) }
            else -> (listOf(normalize(dlBase)) + basesRaw).distinct()
        }
        val path = apkPathOrUrl.trimStart('/')
        return filteredBases.map { b -> "${normalize(b)}/$path" }
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

    private suspend fun preflightPickUrl(repoBase: String, urls: List<String>): String? {
        val client = runCatching { httpClients.clientFor(repoBase) }.getOrNull() ?: return urls.firstOrNull()
        for (u in urls) {
            runCatching {
                val req = Request.Builder().url(u).header("Range", "bytes=0-0").get().build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful || resp.code in 200..399) return u
                }
            }.onFailure {
                if (it is SSLHandshakeException) {
                    DebugLog.log("Downloader", "TLS handshake failed for $u (${it.message})")
                }
            }
        }
        return null
    }

    private fun defaultStreamingClient(): OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .build()

    // Main download: policy = if trust requires pin/CA, skip DM and stream directly; else DM first, stream fallback
    private suspend fun download(req: ResolvedApk): File? = withContext(Dispatchers.IO) {
        val out = cacheFileFor(req)
        if (out.exists()) return@withContext out
        if (isCancelled(req.packageName)) return@withContext null

        val failOnTrustErrors = runCatching { settings.settingsFlow.first().failOnTrustErrors }.getOrDefault(false)
        val preferred = preflightPickUrl(req.repoBase, req.urls)
        val tryUrls = if (preferred != null) listOf(preferred) + req.urls.filterNot { it == preferred } else req.urls
        val userAgent = "Flicky/${app.flicky.BuildConfig.VERSION_NAME} (${Build.MODEL}; ${Build.SUPPORTED_ABIS.joinToString()})"

        val trustRequiresCustomClient = req.trustMode.equals("Pinned", true) || req.trustMode.equals("CustomCA", true)

        for (url in tryUrls) {
            if (trustRequiresCustomClient) {
                val client = try { httpClients.clientFor(req.repoBase) } catch (e: Exception) {
                    DebugLog.log("Downloader", "TLS client failed: ${e.message}")
                    if (failOnTrustErrors) return@withContext null
                    // permissive fallback only if setting is OFF
                    defaultStreamingClient()
                }
                DebugLog.log("Downloader", "Pinned/CustomCA: streaming for $url (strict=${failOnTrustErrors})")
                val ok = streamWithOkHttp(
                    client = client,
                    url = url, dest = out, userAgent = userAgent, referer = req.repoBase,
                    expectedSize = req.size, packageName = req.packageName,
                )
                if (ok && out.exists()) {
                    MirrorRegistry.markHealthy(req.repoBase, url)
                    return@withContext out
                } else {
                    runCatching { if (out.exists()) out.delete() }
                    continue
                }
            }


            // Otherwise: try DownloadManager first
            val request = DownloadManager.Request(url.toUri())
                .setTitle(req.title)
                .setDescription(context.getString(R.string.settings_downloads))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .apply {
                    addRequestHeader("User-Agent", userAgent)
                    addRequestHeader("Accept-Encoding", "gzip, deflate")
                    addRequestHeader("Referer", req.repoBase)
                    // For external cache, write directly
                    (context.externalCacheDir?.let { setDestinationUri(Uri.fromFile(out)) })
                }

            DebugLog.log("Downloader", "Enqueue (DM) $url")
            val id = dm.enqueue(request)
            activeDownloads[out.name] = id
            val uri = monitorDownload(id, req.packageName, url)
            activeDownloads.remove(out.name)

            if (uri != null) {
                if (!out.exists()) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { src ->
                            out.outputStream().use { dst -> src.copyTo(dst) }
                        }
                    }
                }
                if (out.exists()) {
                    MirrorRegistry.markHealthy(req.repoBase, url)
                    return@withContext out
                }
            } else {
                runCatching { if (out.exists()) out.delete() }
            }

            // Fallback: direct streaming
            DebugLog.log("Downloader", "Fallback to streaming for $url")
            val client = try { httpClients.clientFor(req.repoBase) } catch (e: Exception) {
                if (failOnTrustErrors) {
                    DebugLog.log("Downloader", "TLS client failed (strict): ${e.message}")
                    null
                } else defaultStreamingClient()
            }
            if (client != null) {
                val ok = streamWithOkHttp(
                    client = client,
                    url = url, dest = out, userAgent = userAgent, referer = req.repoBase,
                    expectedSize = req.size, packageName = req.packageName,
                )
                if (ok && out.exists()) {
                    MirrorRegistry.markHealthy(req.repoBase, url)
                    return@withContext out
                } else {
                    runCatching { if (out.exists()) out.delete() }
                }
            }
        }
        null
    }


    // Direct streaming with resume + progress updates
    private fun streamWithOkHttp(
        client: OkHttpClient,
        url: String,
        dest: File,
        userAgent: String,
        referer: String,
        expectedSize: Long,
        packageName: String,
    ): Boolean {
        return try {
            val already = if (dest.exists()) dest.length().coerceAtLeast(0L) else 0L
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Referer", referer)
            if (already > 0) reqBuilder.header("Range", "bytes=$already-")

            val call = client.newCall(reqBuilder.build())
            activeCalls[packageName] = call
            call.execute().use { resp ->
                activeCalls.remove(packageName)
                val isResume = already > 0
                val isPartial = resp.code == 206
                if (isResume && !isPartial) {
                    // Server ignored Range. Restart from scratch.
                    if (dest.exists()) dest.delete()
                }
                if (!(resp.isSuccessful || isPartial)) {
                    DebugLog.log("Downloader", "HTTP ${resp.code} for $url")
                    return false
                }
                val body = resp.body
                val totalFromServer = body.contentLength().takeIf { it > 0 } ?: -1L
                val totalTarget = if (totalFromServer > 0 && isPartial) already + totalFromServer else (if (totalFromServer > 0) totalFromServer else expectedSize)

                dest.parentFile?.mkdirs()
                val append = isResume && isPartial
                FileOutputStream(dest, append).use { os ->
                    body.byteStream().use { ins ->
                        val buf = ByteArray(STREAM_BUF)
                        var written = if (append) already else 0L
                        var r = ins.read(buf)
                        var last = System.nanoTime()
                        while (r != -1) {
                            if (isCancelled(packageName)) { return false }
                            os.write(buf, 0, r); written += r
                            val now = System.nanoTime()
                            if (totalTarget > 0 && now - last > 30_000_000L) {
                                val p = (written.toDouble() / totalTarget.toDouble()).toFloat().coerceIn(0f, 0.999f)
                                emitStage(packageName, TaskStage.Downloading(p))
                                last = now
                            }
                            r = ins.read(buf)
                        }
                        os.flush()
                    }
                }
                emitStage(packageName, TaskStage.Downloading(1f))
                true
            }
        } catch (t: Throwable) {
            DebugLog.log("Downloader", "Direct stream error for $url: ${t.message}")
            false
        }
    }

    private suspend fun monitorDownload(id: Long, packageName: String, url: String): Uri? = withContext(Dispatchers.IO) {
        val q = DownloadManager.Query().setFilterById(id)
        var lastProgress = -1f
        var lastStatusChange = System.currentTimeMillis()
        var lastStatus = -1
        fun reasonText(code: Int): String = when (code) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Device not found"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File exists"
            DownloadManager.ERROR_FILE_ERROR -> "File error"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP data error"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "No space"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "HTTP code"
            DownloadManager.ERROR_UNKNOWN -> "Unknown"
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi‑Fi"
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for network"
            DownloadManager.PAUSED_WAITING_TO_RETRY -> "Retrying"
            else -> "Pending"
        }
        while (isActive) {
            if (isCancelled(packageName)) { dm.remove(id); return@withContext null }
            val c = dm.query(q)
            try {
                if (c != null && c.moveToFirst()) {
                    val statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = if (statusIdx != -1) c.getInt(statusIdx) else DownloadManager.STATUS_PENDING
                    val reasonIdx = c.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = if (reasonIdx != -1 && !c.isNull(reasonIdx)) c.getInt(reasonIdx) else 0

                    if (status != lastStatus) {
                        lastStatus = status
                        lastStatusChange = System.currentTimeMillis()
                        DebugLog.log("Downloader", "Status=$status (${reasonText(reason)}) for $url")
                    }

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            emitStage(packageName, TaskStage.Downloading(1f))
                            return@withContext dm.getUriForDownloadedFile(id)
                        }
                        DownloadManager.STATUS_FAILED -> {
                            DebugLog.log("Downloader", "Failed (${reasonText(reason)}) for $url")
                            return@withContext null
                        }
                        DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_PENDING -> {
                            val soFarIdx = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val totalIdx = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            if (soFarIdx != -1 && totalIdx != -1) {
                                val total = c.getLong(totalIdx)
                                if (total > 0) {
                                    val p = c.getLong(soFarIdx).toFloat() / total.toFloat()
                                    if (p != lastProgress) {
                                        if (isCancelled(packageName)) { dm.remove(id); return@withContext null }
                                        lastProgress = p
                                        emitStage(packageName, TaskStage.Downloading(p.coerceIn(0f, 0.999f))) }
                                }
                            }
                            val waitingOnNetwork = reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK ||
                                    reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI
                            val elapsed = System.currentTimeMillis() - lastStatusChange
                            if (!waitingOnNetwork && (status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED)) {
                                if (elapsed > 30_000) {
                                    DebugLog.log("Downloader", "Stalled ($elapsed ms) on $url, switching")
                                    dm.remove(id)
                                    return@withContext null
                                }
                            }
                        }
                    }
                }
            } finally { c?.close() }
            delay(300)
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
    } catch (_: Exception) { false }

    private suspend fun installSystem(file: File, packageName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i); return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val launched = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }); true
        }.getOrDefault(false)
        if (!launched) return false

        return awaitPackageInstall(packageName)
    }

    private suspend fun awaitPackageInstall(
        packageName: String,
        timeoutMs: Long = 300_000L // 5 minutes max
    ): Boolean {
        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addDataScheme("package")
                }
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        val pkg = intent?.data?.schemeSpecificPart
                        if (pkg == packageName) {
                            try { context.unregisterReceiver(this) } catch (_: Exception) {}
                            cont.resume(true)
                        }
                    }
                }
                context.registerReceiver(receiver, filter)
                cont.invokeOnCancellation {
                    try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                }
            }
        }
        return result == true
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    private suspend fun installSessionFromFile(
        file: File,
        packageName: String,
        expectedSha256: String = "",
    ): InstallSessionResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i)
            return InstallSessionResult(success = false)
        }
        if (expectedSha256.isNotBlank() && !verifySha256File(file, expectedSha256)) return InstallSessionResult(success = false) // <-- Return new type

        val pm = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(packageName) }

        val sessionId = pm.createSession(params)
        val session = pm.openSession(sessionId)
        val total = file.length().coerceAtLeast(1L)
        FileInputStream(file).use { fis ->
            session.openWrite("base.apk", 0, -1).use { out ->
                val buf = ByteArray(STREAM_BUF)
                var written = 0L
                var r = fis.read(buf)
                while (r != -1) {
                    if (isCancelled(packageName)) {
                        runCatching { out.flush() }
                        session.abandon()
                        emitStage(packageName, TaskStage.Cancelled)
                        return InstallSessionResult(success = false, wasCancelledByUser = true)
                    }
                    out.write(buf, 0, r)
                    written += r
                    emitStage(packageName, TaskStage.Installing((written.toFloat() / total.toFloat()).coerceIn(0f, 1f)))
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

        val intent = Intent(context, InstallResultReceiver::class.java)
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT

        val pending = PendingIntent.getBroadcast(context, sessionId, intent, pendingFlags)

        try {
            session.commit(pending.intentSender)
            session.close()
        } catch (e: Exception) {
            DebugLog.log("Installer", "Session commit() unexpected error: ${e.message}")
            runCatching { session.abandon() }
            waitJob.cancel()
            return InstallSessionResult(success = false)
        }

        val status = try { withTimeout(180_000) { result.await() } } finally { waitJob.cancel() }

        return when (status) {
            PackageInstaller.STATUS_SUCCESS -> InstallSessionResult(success = true)
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallSessionResult(success = false, wasCancelledByUser = true)
            else -> InstallSessionResult(success = false)
        }
    }

    private suspend fun installRootStream(file: File, packageName: String): Boolean {
        val size = file.length().coerceAtLeast(1L)

        return executeWithProcess(
            packageName = packageName,
            processBuilder = {
                try {
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "cmd package install -r -S $size"))
                } catch (e: Exception) {
                    DebugLog.log("Installer", "Failed to start root process: ${e.message}")
                    null
                }
            },
            pipeData = { process ->
                try {
                    FileInputStream(file).use { fis ->
                        process.outputStream.use { os ->
                            pipeWithProgress(fis, os, size, packageName)
                        }
                    }
                    true
                } catch (e: Exception) {
                    DebugLog.log("Installer", "Root install pipe failed: ${e.message}")
                    false
                }
            }
        )
    }


    private suspend fun installShizukuStream(file: File, packageName: String): Boolean {
        if (!Shizuku.pingBinder()) {
            DebugLog.log("Installer", "Shizuku not available")
            return false
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            if (!requestShizukuPermission()) {
                DebugLog.log("Installer", "Shizuku permission denied")
                return false
            }
        }

        val size = file.length().coerceAtLeast(1L)

        return executeWithProcess(
            packageName = packageName,
            processBuilder = {
                try {
                    shizukuNewProcess(
                        arrayOf("cmd", "package", "install", "-r", "-S", size.toString())
                    )
                } catch (e: Exception) {
                    DebugLog.log("Installer", "Failed to start Shizuku process: ${e.message}")
                    null
                }
            },
            pipeData = { process ->
                try {
                    FileInputStream(file).use { fis ->
                        process.outputStream.use { os ->
                            pipeWithProgress(fis, os, size, packageName)
                        }
                    }
                    true
                } catch (e: Exception) {
                    DebugLog.log("Installer", "Shizuku install pipe failed: ${e.message}")
                    false
                }
            }
        )
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
        val m: Method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        m.isAccessible = true
        m.invoke(null, cmd, env, dir) as Process
    } catch (_: Exception) { null }

    private fun pipeWithProgress(src: FileInputStream, dst: OutputStream, total: Long, packageName: String) {
        val buf = ByteArray(STREAM_BUF)
        var written = 0L
        var r = src.read(buf)
        while (r != -1) {
            if (isCancelled(packageName)) return
            dst.write(buf, 0, r)
            written += r
            val p = (written.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            emitStage(packageName, TaskStage.Installing(p))
            r = src.read(buf)
        }
        dst.flush()
    }

    private fun scheduleCleanup(file: File) {
        scope.launch { delay(120_000); runCatching { if (file.exists()) file.delete() } }
    }

    private fun cleanOldCache() {
        scope.launch {
            val cutoff = System.currentTimeMillis() - CACHE_EXPIRY_HOURS * 60L * 60L * 1000L
            getBaseCacheDir().listFiles()?.forEach { f -> if (f.lastModified() < cutoff) runCatching { f.delete() } }
        }
    }
}

private fun Process.isAliveCompat(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        this.isAlive
    } else {
        try {
            exitValue()
            false
        } catch (_: IllegalThreadStateException) {
            true
        }
    }
}

private fun Process.destroyForciblyCompat() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        this.destroyForcibly()
    } else {
        this.destroy()

        // try to use reflection for better termination
        try {
            val pidField = this.javaClass.getDeclaredField("pid")
            pidField.isAccessible = true
            val pid = pidField.getInt(this)
            Runtime.getRuntime().exec("kill -9 $pid")
        } catch (e: Exception) {
            // Fallback already done with destroy()
            DebugLog.log("Installer", "Force kill failed: ${e.message}")
        }
    }
}