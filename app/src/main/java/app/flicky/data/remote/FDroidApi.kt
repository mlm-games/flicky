package app.flicky.data.remote

import android.annotation.SuppressLint
import android.os.Build
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import androidx.core.os.LocaleListCompat
import androidx.core.text.ICUCompat
import app.flicky.BuildConfig
import app.flicky.data.local.AppDatabase
import app.flicky.data.local.AppVariant
import app.flicky.data.local.RepositoryEntity
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.RepositoryInfo
import app.flicky.data.remote.parseProxyConfig
import app.flicky.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class FDroidApi(
    context: android.content.Context,
    private val clientProvider: HttpClientProvider,
    private val settings: SettingsRepository,
    private val mirrorPolicyProvider: MirrorPolicyProvider,
    private val db: AppDatabase
) {
    companion object {
        private const val TAG = "FDroidApi"
        private const val BATCH_SIZE = 50
        private const val MAX_RETRIES = 2
        private const val RETRY_BACKOFF_MS = 1200L

        // v1/v0 assets
        private const val INDEX_V1_JAR = "index-v1.jar"
        private const val INDEX_V1_JSON = "index-v1.json"
        private const val INDEX_V0_JAR = "index.jar"
        private const val INDEX_V0_XML = "index.xml"
        private const val INDEX_V2_JSON = "index-v2.json"
    }

    // Fallback client (rarely used when provider throws)
    private val defaultClient = OkHttpClient.Builder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val currentCall = java.util.concurrent.atomic.AtomicReference<okhttp3.Call?>(null)

    fun cancelOngoing() {
        currentCall.getAndSet(null)?.cancel()
    }

    data class RepoHeaders(val etag: String?, val lastModified: String?)
    data class FetchResult(val headers: RepoHeaders?, val modified: Boolean)

    suspend fun fetchWithCache(
        repo: RepositoryInfo,
        previous: RepoHeaders,
        force: Boolean = false,
        enableDifferential: Boolean = true,
        includeIncompatible: Boolean = true,
        onApp: suspend (FDroidApp) -> Unit,
        onVariant: (AppVariant) -> Unit = {}
    ): FetchResult? = withContext(Dispatchers.IO) {
        val baseUrl = repo.url.trimEnd('/')
        val currentSettings = settings.settingsFlow.first()
        val strict = currentSettings.failOnTrustErrors
        val proxyEnabled = parseProxyConfig(currentSettings) != null

        suspend fun client(): OkHttpClient {
            return try {
                clientProvider.clientFor(baseUrl)
            } catch (e: ClientConfigurationException) {
                throw e
            } catch (e: Exception) {
                if (proxyEnabled || strict) throw e else defaultClient
            }
        }

        fun baseRequest(url: String, method: String): Request.Builder {
            val b = Request.Builder()
                .url(url)
                .method(method, null)
                .header(
                    "User-Agent",
                    "Flicky/${BuildConfig.VERSION_NAME} (${Build.MODEL}; ${Build.SUPPORTED_ABIS.joinToString()})"
                )
            if (!force) {
                previous.etag?.let { b.header("If-None-Match", it) }
                previous.lastModified?.let { b.header("If-Modified-Since", it) }
            }
            return b
        }

        // Try HEAD for v2 (lightweight diff)
        if (!force && enableDifferential) {
            var headCall: okhttp3.Call? = null
            try {
                headCall = client().newCall(baseRequest("$baseUrl/$INDEX_V2_JSON", "HEAD").build())
                currentCall.set(headCall)
                headCall.execute().use { head ->
                    when (head.code) {
                        304 -> {
                            Log.d(TAG, "HEAD 304 Not Modified for ${repo.name}")
                            return@withContext FetchResult(previous, modified = false)
                        }

                        405, 501 -> { /* unsupported; try GET */
                        }

                        else -> { /* proceed */
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "HEAD skipped for ${repo.name}: ${e.message}")
            } finally {
                currentCall.compareAndSet(headCall, null)
            }
        }

        // Try index-v2.json first
        var attempt = 0
        var lastException: Exception? = null
        while (attempt <= MAX_RETRIES) {
            var getCall: okhttp3.Call? = null
            try {
                val req = baseRequest("$baseUrl/$INDEX_V2_JSON", "GET")
                    .header("Accept", "application/json")
                    .build()
                getCall = client().newCall(req)
                currentCall.set(getCall)
                getCall.execute().use { resp ->
                    when {
                        resp.code == 304 -> {
                            Log.d(TAG, "GET 304 Not Modified for ${repo.name}")
                            return@withContext FetchResult(previous, modified = false)
                        }

                        resp.isSuccessful -> {
                            Log.d(TAG, "Parsing v2 index for ${repo.name}")
                            parseIndexV2(resp, baseUrl, repo.name, onApp, includeIncompatible, onVariant)
                            val etag = resp.header("ETag")
                            val lastMod = resp.header("Last-Modified")
                            return@withContext FetchResult(RepoHeaders(etag, lastMod), modified = true)
                        }
                        // If 404/403/400 etc., fall back to v1
                        else -> {
                            Log.w(TAG, "v2 GET ${resp.code} for ${repo.name}; trying v1...")
                            break // exit retry loop; switch to v1
                        }
                    }
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "v2 fetch error for ${repo.name} (attempt $attempt): ${e.message}")
                if (attempt < MAX_RETRIES) {
                    attempt++
                    delay(RETRY_BACKOFF_MS * attempt)
                } else {
                    break
                }
            } finally {
                currentCall.compareAndSet(getCall, null)
            }
        }

        // Fallback 1: index-v1.jar -> index-v1.json
        fetchV1(
            baseUrl,
            repo.name,
            previous,
            force,
            includeIncompatible,
            onApp,
            onVariant,
            client()
        )?.let { return@withContext it }

        // Fallback 2 (legacy legacy): index.jar (v0) -> index.xml
        fetchV0(
            baseUrl,
            repo.name,
            previous,
            force,
            includeIncompatible,
            onApp,
            onVariant,
            client()
        )?.let { return@withContext it }

        Log.e(TAG, "Failed to fetch ${repo.name}", lastException)
        null
    }

    private suspend fun parseIndexV2(
        resp: Response,
        baseUrl: String,
        repoName: String,
        onApp: suspend (FDroidApp) -> Unit,
        includeIncompatible: Boolean = true,
        onVariant: (AppVariant) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        var totalApps = 0
        val batch = mutableListOf<FDroidApp>()

        val body = resp.body
        InputStreamReader(body.byteStream(), Charsets.UTF_8).use { isr ->
            JsonReader(isr).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "repo" -> parseRepoBlockV2(reader)
                        "packages" -> {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val packageName = reader.nextName()
                                val app = parsePackageStreamingBest(
                                    reader,
                                    packageName,
                                    baseUrl,
                                    repoName,
                                    includeIncompatible,
                                    onVariant
                                )
                                if (app != null) {
                                    batch.add(app)
                                    totalApps++
                                    if (batch.size >= BATCH_SIZE) {
                                        batch.forEach { onApp(it) }
                                        batch.clear()
                                    }
                                }
                            }
                            reader.endObject()
                        }

                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
        }

        if (batch.isNotEmpty()) {
            batch.forEach { onApp(it) }
            batch.clear()
        }
        Log.d(TAG, "Parsed $totalApps apps from $repoName (v2)")
    }

    private suspend fun parseRepoBlockV2(reader: JsonReader) {
        var address: String? = null
        val mirrors = mutableListOf<String>()
        var primaryUrl: String? = null
        var nameLocalized: MutableMap<String, String>? = null
        var descLocalized: MutableMap<String, String>? = null
        var webBaseUrl: String? = null
        var timestamp = 0L

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "address" -> address = reader.nextString()
                "mirrors" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        var url: String? = null
                        var isPrimary = false
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "url" -> url = reader.nextString()
                                "isPrimary" -> {
                                    if (reader.peek() == JsonToken.BOOLEAN) isPrimary = reader.nextBoolean()
                                    else reader.skipValue()
                                }

                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        url?.let {
                            mirrors.add(it)
                            if (isPrimary) primaryUrl = it
                        }
                    }
                    reader.endArray()
                }

                "name" -> nameLocalized = parseLocalizedStrings(reader).toMutableMap()
                "description" -> descLocalized = parseLocalizedStrings(reader).toMutableMap()
                "webBaseUrl" -> webBaseUrl = reader.nextString()
                "timestamp" -> timestamp = reader.nextLong()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (!address.isNullOrBlank()) {
            val base = address.trim().trimEnd('/')
            MirrorRegistry.register(base, listOf(base) + mirrors, primaryUrl)
            runCatching { mirrorPolicyProvider.ensureDefault(base) }

            val name = pickLocalized(nameLocalized) ?: ""
            val desc = pickLocalized(descLocalized) ?: ""
            runCatching {
                val entity = RepositoryEntity(
                    baseUrl = base,
                    name = name,
                    description = desc,
                    webBaseUrl = webBaseUrl.orEmpty(),
                    timestamp = timestamp,
                    fingerprint = "" // v2 has no jar signer fingerprint
                )
                db.repositoryDao().upsert(entity)
            }
        }
    }

    private suspend fun fetchV1(
        baseUrl: String,
        repoName: String,
        previous: RepoHeaders,
        force: Boolean,
        includeIncompatible: Boolean,
        onApp: suspend (FDroidApp) -> Unit,
        onVariant: (AppVariant) -> Unit,
        client: OkHttpClient
    ): FetchResult? {
        var call: okhttp3.Call? = null
        try {
            val req = Request.Builder()
                .url("$baseUrl/$INDEX_V1_JAR")
                .get()
                .header(
                    "User-Agent",
                    "Flicky/${BuildConfig.VERSION_NAME} (${Build.MODEL}; ${Build.SUPPORTED_ABIS.joinToString()})"
                )
                .apply {
                    if (!force) {
                        previous.etag?.let { header("If-None-Match", it) }
                        previous.lastModified?.let { header("If-Modified-Since", it) }
                    }
                }
                .build()
            call = client.newCall(req)
            currentCall.set(call)
            call.execute().use { resp ->
                if (resp.code == 304) {
                    Log.d(TAG, "v1 JAR 304 Not Modified for $repoName")
                    return FetchResult(previous, modified = false)
                }
                if (!resp.isSuccessful) {
                    Log.w(TAG, "v1 JAR non-success ${resp.code} for $repoName")
                    return null
                }
                Log.d(TAG, "Parsing v1 index for $repoName")
                parseIndexV1Zip(resp, baseUrl, repoName, onApp, includeIncompatible, onVariant)
                val etag = resp.header("ETag")
                val lastMod = resp.header("Last-Modified")
                return FetchResult(RepoHeaders(etag, lastMod), modified = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "v1 fetch error for $repoName: ${e.message}")
            return null
        } finally {
            currentCall.compareAndSet(call, null)
        }
    }

    private suspend fun parseIndexV1Zip(
        resp: Response,
        baseUrl: String,
        repoName: String,
        onApp: suspend (FDroidApp) -> Unit,
        includeIncompatible: Boolean,
        onVariant: (AppVariant) -> Unit
    ) {
        val body = resp.body
        val zis = ZipInputStream(body.byteStream())
        try {
            var entry = zis.nextEntry
            var parsed = false
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(INDEX_V1_JSON)) {
                    InputStreamReader(zis, Charsets.UTF_8).use { isr ->
                        JsonReader(isr).use { reader ->
                            parseIndexV1Json(reader, baseUrl, repoName, onApp, includeIncompatible, onVariant)
                            parsed = true
                        }
                    }
                    break
                }
                entry = zis.nextEntry
            }
            if (!parsed) Log.w(TAG, "v1 zip did not contain $INDEX_V1_JSON for $repoName")
        } catch (e: Exception) {
            Log.w(TAG, "v1 parse error: ${e.message}")
        } finally {
            zis.closeQuietly()
        }
    }

    private suspend fun parseIndexV1Json(
        reader: JsonReader,
        baseUrl: String,
        repoName: String,
        onApp: suspend (FDroidApp) -> Unit,
        includeIncompatible: Boolean,
        onVariant: (AppVariant) -> Unit
    ) {
        // Map of basic metadata by package
        data class Meta(
            val name: String? = null,
            val summary: String? = null,
            val description: String? = null,
            val icon: String? = null,
            val author: String? = null,
            val website: String? = null,
            val source: String? = null,
            val categories: List<String> = emptyList(),
            val anti: List<String> = emptyList(),
            val added: Long = 0L,
            val updated: Long = 0L
        )

        val metaByPkg = hashMapOf<String, Meta>()

        // We’ll stream: first parse repo, then apps[], then packages{}
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "repo" -> parseRepoBlockV1(reader)
                "apps" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        var pkg = ""
                        var name: String? = null
                        var summary: String? = null
                        var description: String? = null
                        var icon: String? = null
                        var authorName: String? = null
                        var web: String? = null
                        var source: String? = null
                        var categories: List<String> = emptyList()
                        var anti: List<String> = emptyList()
                        var added = 0L
                        var updated = 0L

                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "packageName" -> pkg = reader.nextString()
                                "name" -> name = safeString(reader)
                                "summary" -> summary = safeString(reader)
                                "description" -> description = safeString(reader)
                                "icon" -> icon = safeString(reader)
                                "authorName" -> authorName = safeString(reader)
                                "webSite" -> web = safeString(reader)
                                "sourceCode" -> source = safeString(reader)
                                "categories" -> categories = parseStringArray(reader)
                                "antiFeatures" -> anti = parseStringArray(reader)
                                "added" -> added = safeLong(reader)
                                "lastUpdated" -> updated = safeLong(reader)
                                "localized" -> {
                                    // try to pick en-US or first
                                    val loc = parseV1Localized(reader)
                                    if (name.isNullOrEmpty()) name = loc["name"]
                                    if (summary.isNullOrEmpty()) summary = loc["summary"]
                                    if (description.isNullOrEmpty()) description = loc["description"]
                                    if (icon.isNullOrEmpty()) icon = loc["icon"]
                                }

                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        if (pkg.isNotBlank()) {
                            metaByPkg[pkg] = Meta(
                                name = name,
                                summary = summary,
                                description = description,
                                icon = icon,
                                author = authorName,
                                website = web,
                                source = source,
                                categories = categories,
                                anti = anti,
                                added = added,
                                updated = updated
                            )
                        }
                    }
                    reader.endArray()
                }

                "packages" -> {
                    // packages: { "pkg": [ {versionName, versionCode, apkName, ...}, ... ], ... }
                    reader.beginObject()
                    val batch = mutableListOf<FDroidApp>()
                    while (reader.hasNext()) {
                        val pkg = reader.nextName()
                        reader.beginArray()
                        // choose best by versionCode (desc), tie-breaker smaller size
                        var best: V1Version? = null
                        val variants = mutableListOf<V1Version>()
                        while (reader.hasNext()) {
                            val v = parseV1Version(reader)
                            variants.add(v)
                            best = when {
                                best == null -> v
                                v.versionCode > best.versionCode -> v
                                v.versionCode == best.versionCode &&
                                        v.size in 1..Long.MAX_VALUE &&
                                        best.size in 1..Long.MAX_VALUE &&
                                        v.size < best.size -> v

                                else -> best
                            }
                        }
                        reader.endArray()

                        // emit variants
                        variants.forEach { v ->
                            val isCompat = isCompatible(v.minSdkVersion, v.nativecode)
                            val variant = AppVariant(
                                packageName = pkg,
                                repositoryUrl = baseUrl,
                                repositoryName = repoName,
                                versionName = v.versionName,
                                versionCode = v.versionCode.toInt(),
                                apkUrl = if (v.apkName.startsWith("http")) v.apkName else "$baseUrl/${v.apkName}",
                                sha256 = v.hash,
                                size = v.size,
                                isCompatible = isCompat,
                                releaseChannels = v.releaseChannels
                            )
                            runCatching { onVariant(variant) }
                        }

                        val m = metaByPkg[pkg] ?: Meta()
                        val b = best ?: continue

                        val hasCompatible = variants.any { isCompatible(it.minSdkVersion, it.nativecode) }

                        if (!hasCompatible && !includeIncompatible) continue

                        val iconUrl = when {
                            !m.icon.isNullOrBlank() && m.icon.startsWith("http") -> m.icon
                            !m.icon.isNullOrBlank() && m.icon.startsWith("/") -> "$baseUrl${m.icon}"
                            !m.icon.isNullOrBlank() -> "$baseUrl/${m.icon}"
                            else -> "$baseUrl/icons/$pkg.png"
                        }

                        val app = FDroidApp(
                            packageName = pkg,
                            name = m.name ?: pkg,
                            summary = m.summary ?: "",
                            description = m.description ?: "",
                            iconUrl = iconUrl,
                            version = b.versionName,
                            versionCode = b.versionCode.toInt(),
                            size = b.size,
                            apkUrl = if (b.apkName.startsWith("http")) b.apkName else "$baseUrl/${b.apkName}",
                            license = "", // v1 repo has license per app; omitted here for brevity
                            category = m.categories.firstOrNull() ?: "Other",
                            author = m.author ?: "Unknown",
                            website = m.website ?: "",
                            sourceCode = m.source ?: "",
                            added = m.added,
                            lastUpdated = m.updated,
                            screenshots = emptyList(),
                            antiFeatures = m.anti,
                            repository = repoName,
                            repositoryUrl = baseUrl,
                            sha256 = b.hash,
                            whatsNew = "",
                            isCompatible = hasCompatible
                        )
                        batch.add(app)
                        if (batch.size >= BATCH_SIZE) {
                            batch.forEach { onApp(it) }
                            batch.clear()
                        }
                    }
                    if (batch.isNotEmpty()) {
                        batch.forEach { onApp(it) }
                        batch.clear()
                    }
                    reader.endObject()
                }

                else -> reader.skipValue()
            }
        }
        reader.endObject()
        Log.d(TAG, "Parsed apps from $repoName (v1)")
    }

    private data class V1Version(
        val versionCode: Long,
        val versionName: String,
        val apkName: String,
        val size: Long,
        val hash: String,
        val minSdkVersion: Int,
        val nativecode: List<String>,
        val releaseChannels: List<String> = emptyList()
    )

    private fun parseV1Version(reader: JsonReader): V1Version {
        var versionCode = 0L
        var versionName = "1.0"
        var apkName = ""
        var size = 0L
        var hash = ""
        var minSdk = 1
        var nativecode: List<String> = emptyList()
        var releaseChannels: List<String> = emptyList()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "versionCode" -> versionCode = safeLong(reader)
                "versionName" -> versionName = safeString(reader) ?: "1.0"
                "apkName" -> apkName = safeString(reader) ?: ""
                "size" -> size = safeLong(reader)
                "hash" -> hash = safeString(reader) ?: ""
                "hashType" -> reader.skipValue() // assume sha256 or v1 hash semantics
                "minSdkVersion" -> minSdk = safeInt(reader)
                "nativecode" -> nativecode = parseStringArray(reader)
                "srcname" -> {
                    // sometimes v1 uses srcname; prefer apkName if present
                    if (apkName.isBlank()) apkName = safeString(reader) ?: ""
                }

                "releaseChannels" -> releaseChannels = parseStringArray(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return V1Version(versionCode, versionName, apkName, size, hash, minSdk, nativecode, releaseChannels)
    }

    private suspend fun parseRepoBlockV1(reader: JsonReader) {
        var address = ""
        val mirrors = mutableListOf<String>()
        var name = ""
        var description = ""
        var timestamp = 0L

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "address" -> address = safeString(reader) ?: ""
                "mirrors" -> {
                    // mirrors can be ["url", ...] or [{ "url": "..."}]
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            when (reader.peek()) {
                                JsonToken.STRING -> mirrors.add(reader.nextString())
                                JsonToken.BEGIN_OBJECT -> {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        if (reader.nextName() == "url" && reader.peek() == JsonToken.STRING) {
                                            mirrors.add(reader.nextString())
                                        } else reader.skipValue()
                                    }
                                    reader.endObject()
                                }

                                else -> reader.skipValue()
                            }
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }

                "name" -> name = safeString(reader) ?: ""
                "description" -> description = safeString(reader) ?: ""
                "timestamp" -> timestamp = safeLong(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val base = address.trim().trimEnd('/')
        if (base.isNotBlank()) {
            MirrorRegistry.register(base, listOf(base) + mirrors)
            runCatching { mirrorPolicyProvider.ensureDefault(base) }
            runCatching {
                val entity = RepositoryEntity(
                    baseUrl = base,
                    name = name,
                    description = description,
                    webBaseUrl = "",
                    timestamp = timestamp,
                    fingerprint = "" // v1 JAR signer not inspected here (kept minimal)
                )
                db.repositoryDao().upsert(entity)
            }
        }
    }

    // v0 fallback (minimal; best-effort)

    private fun fetchV0(
        baseUrl: String,
        repoName: String,
        previous: RepoHeaders,
        force: Boolean,
        includeIncompatible: Boolean,
        onApp: suspend (FDroidApp) -> Unit,
        onVariant: (AppVariant) -> Unit,
        client: OkHttpClient
    ): FetchResult? {
        var call: okhttp3.Call? = null
        try {
            val req = Request.Builder()
                .url("$baseUrl/$INDEX_V0_JAR")
                .get()
                .header(
                    "User-Agent",
                    "Flicky/${BuildConfig.VERSION_NAME} (${Build.MODEL}; ${Build.SUPPORTED_ABIS.joinToString()})"
                )
                .apply {
                    if (!force) {
                        previous.etag?.let { header("If-None-Match", it) }
                        previous.lastModified?.let { header("If-Modified-Since", it) }
                    }
                }
                .build()
            call = client.newCall(req)
            currentCall.set(call)
            call.execute().use { resp ->
                if (resp.code == 304) {
                    Log.d(TAG, "v0 JAR 304 Not Modified for $repoName")
                    return FetchResult(previous, modified = false)
                }
                if (!resp.isSuccessful) {
                    Log.w(TAG, "v0 JAR non-success ${resp.code} for $repoName")
                    return null
                }
                // Many modern repos at least have v1; v0 is rare. Just mark modified and let sync proceed without apps.
                val etag = resp.header("ETag")
                val lastMod = resp.header("Last-Modified")
                Log.d(TAG, "Fetched v0 jar for $repoName (parser not implemented)")
                return FetchResult(RepoHeaders(etag, lastMod), modified = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "v0 fetch error for $repoName: ${e.message}")
            return null
        } finally {
            currentCall.compareAndSet(call, null)
        }
    }

    private fun safeString(r: JsonReader): String? = when (r.peek()) {
        JsonToken.STRING -> r.nextString()
        else -> {
            r.skipValue(); null
        }
    }

    private fun safeLong(r: JsonReader): Long = when (r.peek()) {
        JsonToken.NUMBER -> runCatching { r.nextLong() }.getOrElse { r.skipValue(); 0L }
        JsonToken.STRING -> runCatching { r.nextString().toLong() }.getOrElse { 0L }
        else -> {
            r.skipValue(); 0L
        }
    }

    private fun safeInt(r: JsonReader): Int = when (r.peek()) {
        JsonToken.NUMBER -> runCatching { r.nextInt() }.getOrElse { r.skipValue(); 0 }
        JsonToken.STRING -> runCatching { r.nextString().toInt() }.getOrElse { 0 }
        else -> {
            r.skipValue(); 0
        }
    }

    private fun parseStringArray(reader: JsonReader): List<String> {
        val list = mutableListOf<String>()
        if (reader.peek() == JsonToken.BEGIN_ARRAY) {
            reader.beginArray()
            while (reader.hasNext()) {
                if (reader.peek() == JsonToken.STRING) list.add(reader.nextString()) else reader.skipValue()
            }
            reader.endArray()
        } else {
            reader.skipValue()
        }
        return list
    }

    private fun parseV1Localized(reader: JsonReader): Map<String, String> {
        val names = mutableMapOf<String, String>()
        val summaries = mutableMapOf<String, String>()
        val descriptions = mutableMapOf<String, String>()
        val icons = mutableMapOf<String, String>()

        reader.beginObject()
        while (reader.hasNext()) {
            val locale = reader.nextName()
            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "name" -> safeString(reader)?.let { names[locale] = it }
                        "summary" -> safeString(reader)?.let { summaries[locale] = it }
                        "description" -> safeString(reader)?.let { descriptions[locale] = it }
                        "icon" -> safeString(reader)?.let { icons[locale] = it }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()

        return buildMap {
            pickLocalized(names)?.let { put("name", it) }
            pickLocalized(summaries)?.let { put("summary", it) }
            pickLocalized(descriptions)?.let { put("description", it) }
            pickLocalized(icons)?.let { put("icon", it) }
        }
    }

    private fun isCompatible(minSdkVersion: Int, nativecode: List<String>): Boolean {
        val sdkOk = Build.VERSION.SDK_INT >= minSdkVersion
        val abiOk = nativecode.isEmpty() ||
                nativecode.any { repoAbi ->
                    Build.SUPPORTED_ABIS.any { deviceAbi -> deviceAbi.equals(repoAbi, ignoreCase = true) }
                }
        return sdkOk && abiOk
    }


    private data class Metadata(
        val name: Map<String, String>? = null,
        val summary: Map<String, String>? = null,
        val description: Map<String, String>? = null,
        val icon: Map<String, IconInfo>? = null,
        val categories: List<String> = emptyList(),
        val antiFeatures: List<String> = emptyList(),
        val license: String? = null,
        val authorName: String? = null,
        val webSite: String? = null,
        val sourceCode: String? = null,
        val added: Long = 0,
        val lastUpdated: Long = 0,
        val screenshots: List<String>? = null
    )

    private data class IconInfo(val name: String)

    private data class Version(
        val versionCode: Int,
        val versionName: String,
        val file: String,
        val size: Long,
        val sha256: String,
        val minSdkVersion: Int,
        val targetSdkVersion: Int,
        val nativecode: List<String> = emptyList(),
        val whatsNew: String? = null,
        val antiFeatures: List<String> = emptyList(),
        val reproducible: Boolean = false,
        val releaseChannels: List<String> = emptyList(),
    )

    @SuppressLint("CheckResult")
    private fun parsePackageStreamingBest(
        reader: JsonReader,
        packageName: String,
        baseUrl: String,
        repoName: String,
        includeIncompatible: Boolean,
        onVariant: (AppVariant) -> Unit
    ): FDroidApp? {
        var metadata: Metadata? = null
        var best: Version? = null
        val variants = mutableListOf<Version>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "metadata" -> metadata = parseMetadata(reader)
                "versions" -> {
                    when (reader.peek()) {
                        JsonToken.BEGIN_OBJECT -> {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                reader.nextName() // version hash
                                val v = parseVersionLenient(reader)
                                variants.add(v)
                                best = when {
                                    best == null -> v
                                    v.versionCode > best.versionCode -> v
                                    v.versionCode == best.versionCode &&
                                            v.size in 1..Long.MAX_VALUE &&
                                            best.size in 1..Long.MAX_VALUE &&
                                            v.size < best.size -> v

                                    else -> best
                                }
                            }
                            reader.endObject()
                        }

                        JsonToken.BEGIN_ARRAY -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                val v = parseVersionLenient(reader)
                                variants.add(v)
                                best = when {
                                    best == null -> v
                                    v.versionCode > best.versionCode -> v
                                    v.versionCode == best.versionCode &&
                                            v.size in 1..Long.MAX_VALUE &&
                                            best.size in 1..Long.MAX_VALUE &&
                                            v.size < best.size -> v

                                    else -> best
                                }
                            }
                            reader.endArray()
                        }

                        else -> reader.skipValue()
                    }
                }

                else -> reader.skipValue()
            }
        }
        reader.endObject()

        // emit variants
        variants.forEach { v ->
            val variant = AppVariant(
                packageName = packageName,
                repositoryUrl = baseUrl,
                repositoryName = repoName,
                versionName = v.versionName,
                versionCode = v.versionCode,
                apkUrl = if (v.file.startsWith("http")) v.file else "$baseUrl/${v.file}",
                sha256 = v.sha256,
                size = v.size,
                isCompatible = isCompatible(v),
                reproducible = v.reproducible,
                releaseChannels = v.releaseChannels
            )
            runCatching { onVariant(variant) }
        }

        val meta = metadata ?: Metadata()
        val bestVersion = best ?: return null

        val hasCompatible = variants.any { v -> isCompatible(v) }


        val resolvedIconUrl = when {
            meta.icon != null -> {
                val iconName = pickLocalizedObj(meta.icon)?.name
                when {
                    iconName.isNullOrBlank() -> "$baseUrl/icons/$packageName.png"
                    iconName.startsWith("http") -> iconName
                    iconName.startsWith("/") -> "$baseUrl$iconName"
                    else -> "$baseUrl/$iconName"
                }
            }

            repoName == "F-Droid Archive" -> "https://f-droid.org/repo/icons/$packageName.png"
            else -> "$baseUrl/icons/$packageName.png"
        }

        val shotUrls = (meta.screenshots ?: emptyList()).map { s ->
            when {
                s.startsWith("http://") || s.startsWith("https://") -> s
                s.startsWith("/") -> "$baseUrl$s"
                else -> "$baseUrl/$s"
            }
        }

        val resolvedAnti = bestVersion.antiFeatures.takeIf { it.isNotEmpty() } ?: meta.antiFeatures

        return FDroidApp(
            packageName = packageName,
            name = pickLocalized(meta.name) ?: packageName,
            summary = pickLocalized(meta.summary) ?: "",
            description = pickLocalized(meta.description) ?: "",
            iconUrl = resolvedIconUrl,
            version = bestVersion.versionName,
            versionCode = bestVersion.versionCode,
            size = bestVersion.size,
            apkUrl = if (bestVersion.file.startsWith("http")) bestVersion.file else "$baseUrl/${bestVersion.file}",
            license = meta.license ?: "Unknown",
            category = meta.categories.firstOrNull() ?: "Other",
            author = meta.authorName ?: "Unknown",
            website = meta.webSite ?: "",
            sourceCode = meta.sourceCode ?: "",
            added = meta.added,
            lastUpdated = meta.lastUpdated,
            screenshots = shotUrls,
            antiFeatures = resolvedAnti,
            repository = repoName,
            repositoryUrl = baseUrl,
            sha256 = bestVersion.sha256,
            whatsNew = bestVersion.whatsNew ?: "",
            isCompatible = hasCompatible
        )
    }

    private fun isCompatible(version: Version): Boolean {
        val sdkOk = Build.VERSION.SDK_INT >= version.minSdkVersion
        val abiOk = version.nativecode.isEmpty() ||
                version.nativecode.any { repoAbi ->
                    Build.SUPPORTED_ABIS.any { deviceAbi -> deviceAbi.equals(repoAbi, ignoreCase = true) }
                }
        return sdkOk && abiOk
    }

    private fun parseVersionLenient(reader: JsonReader): Version {
        var versionCode = 0
        var versionName = "1.0"
        var file = ""
        var size = 0L
        var sha256 = ""
        var minSdk = 1
        var targetSdk = 1
        var nativecode = emptyList<String>()
        var whatsNew: String? = null
        var antiFeatures: List<String> = emptyList()
        var reproducible = false
        var releaseChannels: List<String> = emptyList()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "file" -> {
                    when (reader.peek()) {
                        JsonToken.STRING -> file = reader.nextString()
                        JsonToken.BEGIN_OBJECT -> {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "name" -> file = reader.nextString()
                                    "size" -> size = safeLong(reader)
                                    "sha256" -> sha256 = safeString(reader) ?: ""
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                        }

                        else -> reader.skipValue()
                    }
                }

                "apkName" -> file = reader.nextString() // v1 alias
                "size" -> size = runCatching { reader.nextLong() }.getOrElse { reader.skipValue(); 0L }
                "sha256", "sha256sum" -> sha256 = safeString(reader) ?: ""
                "manifest", "uses" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "versionCode" -> versionCode =
                                runCatching { reader.nextInt() }.getOrElse { reader.skipValue(); 0 }

                            "versionName" -> versionName = safeString(reader) ?: "1.0"
                            "usesSdk", "sdk" -> {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "minSdkVersion", "min" -> minSdk = safeInt(reader)
                                        "targetSdkVersion", "target" -> targetSdk = safeInt(reader)
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                            }

                            "nativecode" -> nativecode = parseStringArray(reader)
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }

                "versionCode" -> versionCode = runCatching { reader.nextInt() }.getOrElse { reader.skipValue(); 0 }
                "versionName" -> versionName = safeString(reader) ?: "1.0"
                "nativecode" -> nativecode = parseStringArray(reader)
                "whatsNew" -> {
                    whatsNew = when (reader.peek()) {
                        JsonToken.STRING -> reader.nextString()
                        JsonToken.BEGIN_OBJECT -> pickLocalized(parseLocalizedStrings(reader))
                        else -> {
                            reader.skipValue(); null
                        }
                    }
                }

                "antiFeatures" -> antiFeatures = parseAntiFeaturesKeys(reader)
                "reproducible" -> reproducible = runCatching { reader.nextBoolean() }.getOrDefault(false)
                "releaseChannels" -> releaseChannels = parseStringArray(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return Version(
            versionCode,
            versionName,
            file,
            size,
            sha256,
            minSdk,
            targetSdk,
            nativecode,
            whatsNew,
            antiFeatures,
            reproducible,
            releaseChannels
        )
    }

    private fun parseAntiFeaturesKeys(reader: JsonReader): List<String> {
        val out = mutableListOf<String>()
        when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    out += key
                    reader.skipValue()              // ignored LocalizedTextV2 (reasons), since most ppl might have already used other fdroid stores
                }
                reader.endObject()
            }

            JsonToken.BEGIN_ARRAY -> {
                out += parseStringArray(reader)
            }

            JsonToken.STRING -> out += reader.nextString()
            else -> reader.skipValue()
        }
        return out.distinct().sorted()
    }

    private fun parseLocalizedStrings(reader: JsonReader): Map<String, String> {
        val map = mutableMapOf<String, String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val locale = reader.nextName()
            if (reader.peek() == JsonToken.STRING) {
                map[locale] = reader.nextString()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
        return map
    }

    private fun parseMetadata(reader: JsonReader): Metadata {
        var name: MutableMap<String, String>? = null
        var summary: MutableMap<String, String>? = null
        var description: MutableMap<String, String>? = null
        var icon: MutableMap<String, IconInfo>? = null
        var categories = emptyList<String>()
        var antiFeatures = emptyList<String>()
        var license: String? = null
        var authorName: String? = null
        var webSite: String? = null
        var sourceCode: String? = null
        var added = 0L
        var lastUpdated = 0L
        var screenshots: List<String>? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "name" -> name = (name ?: mutableMapOf()).apply { putAll(parseLocalizedStrings(reader)) }
                "summary" -> summary = (summary ?: mutableMapOf()).apply { putAll(parseLocalizedStrings(reader)) }
                "description" -> description =
                    (description ?: mutableMapOf()).apply { putAll(parseLocalizedStrings(reader)) }

                "icon" -> {
                    icon = mutableMapOf()
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val locale = reader.nextName()
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "name" -> icon[locale] = IconInfo(safeString(reader) ?: "")
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                    reader.endObject()
                }

                "categories" -> categories = parseStringArray(reader)
                "antiFeatures" -> antiFeatures = parseStringArray(reader)
                "license" -> license = safeString(reader)
                "authorName" -> authorName = safeString(reader)
                "webSite" -> webSite = safeString(reader)
                "sourceCode" -> sourceCode = safeString(reader)
                "added" -> added = safeLong(reader)
                "lastUpdated" -> lastUpdated = safeLong(reader)
                "screenshots" -> screenshots = parseScreenshotsFlexible(reader)
                "localized" -> {
                    val loc = parseLocalizedBlock(reader)
                    name = (name ?: mutableMapOf()).apply { putAll(loc.names) }
                    summary = (summary ?: mutableMapOf()).apply { putAll(loc.summaries) }
                    description = (description ?: mutableMapOf()).apply { putAll(loc.descriptions) }
                    icon = (icon ?: mutableMapOf()).apply { putAll(loc.icons) }
                    if (screenshots.isNullOrEmpty()) {
                        screenshots = pickLocalizedObj(loc.screenshots)
                            ?: loc.screenshots.values.firstOrNull { it.isNotEmpty() }
                    }
                }

                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return Metadata(
            name, summary, description, icon, categories, antiFeatures,
            license, authorName, webSite, sourceCode, added, lastUpdated, screenshots
        )
    }

    private data class LocalizedMeta(
        val names: MutableMap<String, String> = mutableMapOf(),
        val summaries: MutableMap<String, String> = mutableMapOf(),
        val descriptions: MutableMap<String, String> = mutableMapOf(),
        val icons: MutableMap<String, IconInfo> = mutableMapOf(),
        val screenshots: MutableMap<String, List<String>> = mutableMapOf()
    )

    private fun parseLocalizedBlock(reader: JsonReader): LocalizedMeta {
        val out = LocalizedMeta()
        reader.beginObject()
        while (reader.hasNext()) {
            val locale = reader.nextName()
            reader.beginObject()
            var locName: String? = null
            var locSummary: String? = null
            var locDescription: String? = null
            var locIcon: IconInfo? = null
            var locShots: List<String>? = null

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "name" -> if (reader.peek() == JsonToken.STRING) locName =
                        reader.nextString() else reader.skipValue()

                    "summary" -> if (reader.peek() == JsonToken.STRING) locSummary =
                        reader.nextString() else reader.skipValue()

                    "description" -> if (reader.peek() == JsonToken.STRING) locDescription =
                        reader.nextString() else reader.skipValue()

                    "icon" -> {
                        when (reader.peek()) {
                            JsonToken.STRING -> locIcon = IconInfo(reader.nextString())
                            JsonToken.BEGIN_OBJECT -> {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "name" -> locIcon = IconInfo(safeString(reader) ?: "")
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                            }

                            else -> reader.skipValue()
                        }
                    }

                    "screenshots" -> locShots = parseScreenshotsFlexible(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            locName?.let { out.names[locale] = it }
            locSummary?.let { out.summaries[locale] = it }
            locDescription?.let { out.descriptions[locale] = it }
            locIcon?.let { out.icons[locale] = it }
            locShots?.let { out.screenshots[locale] = it }
        }
        reader.endObject()
        return out
    }

    private fun parseScreenshotsFlexible(reader: JsonReader): List<String> {
        return when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> parseScreenshotsArray(reader)
            JsonToken.BEGIN_OBJECT -> parseScreenshotsObject(reader)
            JsonToken.STRING -> listOf(reader.nextString())
            else -> {
                reader.skipValue(); emptyList()
            }
        }
    }

    private fun parseScreenshotsArray(reader: JsonReader): List<String> {
        val list = mutableListOf<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonToken.STRING -> list.add(reader.nextString())
                JsonToken.BEGIN_OBJECT -> {
                    var name: String? = null
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "name" -> name = safeString(reader)
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    name?.let { list.add(it) }
                }

                JsonToken.BEGIN_ARRAY -> list.addAll(parseScreenshotsArray(reader))
                else -> reader.skipValue()
            }
        }
        reader.endArray()
        return list
    }

    private fun parseScreenshotsObject(reader: JsonReader): List<String> {
        val list = mutableListOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            reader.nextName() // key
            when (reader.peek()) {
                JsonToken.STRING -> list.add(reader.nextString())
                JsonToken.BEGIN_ARRAY -> list.addAll(parseScreenshotsArray(reader))
                JsonToken.BEGIN_OBJECT -> list.addAll(parseScreenshotsObject(reader))
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return list
    }

    private fun currentLocaleList(): LocaleListCompat = LocaleListCompat.getDefault()

    private fun pickLocalized(map: Map<String, String>?): String? = pickLocalizedObj(map)

    /**
     * https://gitlab.com/fdroid/fdroidclient LocaleChooser ref.
     */
    private fun <T> pickLocalizedObj(map: Map<String, T>?): T? {
        if (map.isNullOrEmpty()) return null
        if (map.size == 1) return map.values.first()
        return map.getBestLocale(currentLocaleList())
    }

    private fun <T> Map<String, T>.getBestLocale(localeList: LocaleListCompat): T? {
        val firstMatch = when (localeList.size()) {
            0 -> null
            1 -> localeList[0]
            else -> localeList.getFirstMatch(keys.toTypedArray())
        } ?: return get("en-US") ?: get("en") ?: values.firstOrNull()

        // Exact BCP-47 tag (e.g. de-DE)
        get(firstMatch.toLanguageTag())?.let { return it }

        // Ranking by script/country when no exact match
        val tried =
            (if (firstMatch.script.isNullOrEmpty()) 0 else 1) +
                (if (firstMatch.country.isNullOrEmpty()) 0 else 2)

        val ranked = if (firstMatch.script.isNullOrEmpty()) {
            ICUCompat.maximizeAndGetScript(firstMatch)
                ?.takeUnless { it.isEmpty() }
                ?.let { script -> getInRankingOrder(firstMatch, tried + 1, script, tried) }
        } else {
            if (tried > 1) getInRankingOrder(firstMatch, tried - 1, firstMatch.script, tried)
            else null
        }

        ranked?.let { return it }

        // Language-only if script matches
        if (tried != 0) {
            get(firstMatch.language)
                ?.takeIf {
                    LocaleListCompat.matchesLanguageAndScript(
                        localeOf(firstMatch.language),
                        firstMatch
                    )
                }
                ?.let { return it }
        }

        getFirstSameScript(firstMatch)?.let { return it }

        return get("en-US") ?: get("en") ?: values.firstOrNull()
    }

    private tailrec fun <T> Map<String, T>.getInRankingOrder(
        locale: Locale,
        rank: Int,
        script: String?,
        tried: Int,
    ): T? {
        if (rank <= 0) return null
        if (rank != tried) {
            getRankingTag(locale, rank, script)?.let { tag -> get(tag) }?.let { return it }
        }
        return getInRankingOrder(locale, rank - 1, script, tried)
    }

    private fun <T> Map<String, T>.getFirstSameScript(locale: Locale): T? {
        val langLen = locale.language.length
        for ((key, value) in entries) {
            if (key.length > langLen &&
                key.startsWith(locale.language) &&
                key[langLen] == '-' &&
                LocaleListCompat.matchesLanguageAndScript(Locale.forLanguageTag(key), locale)
            ) {
                return value
            }
        }
        return null
    }

    private fun getRankingTag(locale: Locale, rank: Int, script: String?): String? {
        if (rank >= 2 && locale.country.isNullOrEmpty()) return null
        if (rank != 2 && script.isNullOrEmpty()) return null
        return when (rank) {
            3 -> "${locale.language}-$script-${locale.country}"
            2 -> {
                val ok = script.isNullOrEmpty() ||
                    script.equals(
                        ICUCompat.maximizeAndGetScript(localeOf(locale.language, locale.country)),
                        ignoreCase = true
                    )
                if (ok) "${locale.language}-${locale.country}" else null
            }

            1 -> "${locale.language}-$script"
            else -> null
        }
    }

    @SuppressLint("NewApi")
    private fun localeOf(language: String, country: String = ""): Locale =
        if (Build.VERSION.SDK_INT >= 36) {
            Locale.of(language, country)
        } else {
            @Suppress("DEPRECATION")
            Locale(language, country)
        }
}
