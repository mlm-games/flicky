package app.flicky.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import app.flicky.BuildConfig
import app.flicky.data.local.AppVariant
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.RepositoryInfo
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class FDroidApi(context: Context) {
    companion object {
        private const val TAG = "FDroidApi"
        private const val BATCH_SIZE = 50
        private const val MAX_RETRIES = 2
        private const val RETRY_BACKOFF_MS = 1200L
    }

    private val client = OkHttpClient.Builder()
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
        enableEntryJson: Boolean = true,
        includeIncompatible: Boolean = true,
        onApp: (FDroidApp) -> Unit,
        onVariant: (AppVariant) -> Unit = {}
    ): FetchResult? = withContext(Dispatchers.IO) {
        val baseUrl = repo.url.trimEnd('/')

        fun buildRequest(url: String, method: String): Request {
            val builder = Request.Builder()
                .url(url)
                .method(method, null)
                .header(
                    "User-Agent",
                    "Flicky/${BuildConfig.VERSION_NAME} (${Build.MODEL}; ${Build.SUPPORTED_ABIS.joinToString()})"
                )
                .header("Accept", "application/json")
            if (!force) {
                previous.etag?.let { builder.header("If-None-Match", it) }
                previous.lastModified?.let { builder.header("If-Modified-Since", it) }
            }
            return builder.build()
        }

        fun resolveIndexUrl(): String {
            if (!enableEntryJson) return "$baseUrl/index-v2.json"
            return try {
                val call = client.newCall(buildRequest("$baseUrl/entry.json", "GET"))
                currentCall.set(call)
                call.execute().use { resp ->
                    if (!resp.isSuccessful) return@use "$baseUrl/index-v2.json"
                    val body = resp.body ?: return@use "$baseUrl/index-v2.json"
                    InputStreamReader(body.byteStream(), Charsets.UTF_8).use { isr ->
                        JsonReader(isr).use { reader ->
                            reader.isLenient = true
                            var found: String? = null
                            fun scan(r: JsonReader) {
                                when (r.peek()) {
                                    JsonToken.BEGIN_OBJECT -> {
                                        r.beginObject()
                                        while (r.hasNext()) { r.nextName(); scan(r) }
                                        r.endObject()
                                    }
                                    JsonToken.BEGIN_ARRAY -> {
                                        r.beginArray()
                                        while (r.hasNext()) scan(r)
                                        r.endArray()
                                    }
                                    JsonToken.STRING -> {
                                        val s = r.nextString()
                                        if (found == null) {
                                            if (s.contains("index-v2.json")) found = s
                                            else if (s.contains("index-v1.json")) found = s
                                        }
                                    }
                                    else -> r.skipValue()
                                }
                            }
                            scan(reader)
                            val rel = found ?: "index-v2.json"
                            if (rel.startsWith("http")) rel else "$baseUrl/${rel.trimStart('/')}"
                        }
                    }
                }
            } catch (_: Exception) {
                "$baseUrl/index-v2.json"
            } finally {
                currentCall.set(null)
            }
        }

        val indexUrl = resolveIndexUrl()

        if (!force && enableDifferential) {
            var headCall: okhttp3.Call? = null
            try {
                headCall = client.newCall(buildRequest(indexUrl, "HEAD"))
                currentCall.set(headCall)
                headCall.execute().use { head ->
                    when (head.code) {
                        304 -> {
                            Log.d(TAG, "HEAD 304 Not Modified for ${repo.name}")
                            return@withContext FetchResult(previous, modified = false)
                        }
                        405, 501 -> { /* common for S3/CDN: proceed quietly */ }
                        else -> { /* proceed to GET */ }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "HEAD skipped for ${repo.name}: ${e.message}")
            } finally {
                currentCall.compareAndSet(headCall, null)
            }
        }

        var attempt = 0
        var lastException: Exception? = null
        while (attempt <= MAX_RETRIES) {
            var getCall: okhttp3.Call? = null
            try {
                getCall = client.newCall(buildRequest(indexUrl, "GET"))
                currentCall.set(getCall)
                getCall.execute().use { resp ->
                    if (resp.code == 304) {
                        Log.d(TAG, "GET 304 Not Modified for ${repo.name}")
                        return@withContext FetchResult(previous, modified = false)
                    }

                    if (!resp.isSuccessful) {
                        val code = resp.code
                        Log.w(TAG, "Non-success $code for ${repo.name}")
                        if (code >= 500 && attempt < MAX_RETRIES) {
                            attempt++
                            delay(RETRY_BACKOFF_MS * attempt)
                            return@use
                        }
                        // fallback: try index-v1 if v2 path failed/404
                        if (indexUrl.endsWith("index-v2.json")) {
                            val altReq = client.newCall(buildRequest("$baseUrl/index-v1.json", "GET"))
                            altReq.execute().use { alt ->
                                if (alt.isSuccessful) {
                                    parseIndexV1(alt, baseUrl, repo.name, onApp, includeIncompatible, onVariant)
                                    val etag = alt.header("ETag")
                                    val lastMod = alt.header("Last-Modified")
                                    return@withContext FetchResult(RepoHeaders(etag, lastMod), modified = true)
                                }
                            }
                        }
                        return@withContext null
                    }

                    if (indexUrl.contains("index-v1.json")) {
                        parseIndexV1(resp, baseUrl, repo.name, onApp, includeIncompatible, onVariant)
                    } else {
                        parseIndexV2(resp, baseUrl, repo.name, onApp, includeIncompatible, onVariant)
                    }

                    val etag = resp.header("ETag")
                    val lastMod = resp.header("Last-Modified")
                    return@withContext FetchResult(RepoHeaders(etag, lastMod), modified = true)
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Error fetching ${repo.name} (attempt $attempt): ${e.message}")
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
        Log.e(TAG, "Failed to fetch ${repo.name}", lastException)
        null
    }

    private suspend fun parseIndexV2(
        resp: Response,
        baseUrl: String,
        repoName: String,
        onApp: (FDroidApp) -> Unit,
        includeIncompatible: Boolean = true,
        onVariant: (AppVariant) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        var totalApps = 0
        val batch = mutableListOf<FDroidApp>()

        val body = resp.body ?: return@withContext
        InputStreamReader(body.byteStream(), Charsets.UTF_8).use { isr ->
            JsonReader(isr).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "repo" -> parseRepoBlock(reader)
                        "packages" -> {
                            when (reader.peek()) {
                                JsonToken.BEGIN_OBJECT -> {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        val packageName = reader.nextName()
                                        when (reader.peek()) {
                                            JsonToken.BEGIN_OBJECT -> {
                                                val app = parsePackageStreamingBest(
                                                    reader, packageName, baseUrl, repoName, includeIncompatible, onVariant
                                                )
                                                if (app != null) {
                                                    batch.add(app); totalApps++
                                                    if (batch.size >= BATCH_SIZE) { batch.forEach(onApp); batch.clear() }
                                                }
                                            }
                                            JsonToken.BEGIN_ARRAY -> {
                                                val app = parsePackageV1ArrayForSinglePackage(
                                                    reader, packageName, baseUrl, repoName, null, includeIncompatible, onVariant
                                                )
                                                if (app != null) {
                                                    batch.add(app); totalApps++
                                                    if (batch.size >= BATCH_SIZE) { batch.forEach(onApp); batch.clear() }
                                                }
                                            }
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                }
                                JsonToken.BEGIN_ARRAY -> {
                                    // some repos expose array
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        val app = parsePackageV1Item(reader, baseUrl, repoName, includeIncompatible, onVariant)
                                        app?.let {
                                            batch.add(it); totalApps++
                                            if (batch.size >= BATCH_SIZE) { batch.forEach(onApp); batch.clear() }
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
            }
        }

        if (batch.isNotEmpty()) batch.forEach(onApp)
        Log.d(TAG, "Parsed $totalApps apps from $repoName (v2)")
    }

    private suspend fun parseIndexV1(
        resp: Response,
        baseUrl: String,
        repoName: String,
        onApp: (FDroidApp) -> Unit,
        includeIncompatible: Boolean,
        onVariant: (AppVariant) -> Unit
    ) = withContext(Dispatchers.IO) {
        var total = 0
        val batch = mutableListOf<FDroidApp>()
        val metaMap = mutableMapOf<String, Metadata>()

        val body = resp.body ?: return@withContext
        InputStreamReader(body.byteStream(), Charsets.UTF_8).use { isr ->
            JsonReader(isr).use { reader ->
                reader.isLenient = true
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "repo" -> parseRepoBlock(reader)
                        "apps" -> { 
                            reader.beginArray()
                            while (reader.hasNext()) {
                                parseAppMetaV1(reader)?.let { (pkg, meta) -> metaMap[pkg] = meta }
                            }
                            reader.endArray()
                        }
                        "packages" -> {
                            when (reader.peek()) {
                                JsonToken.BEGIN_ARRAY -> {
                                    // v1 style: array of app entries (each with packageName + versions)
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        val app = parsePackageV1Item(reader, baseUrl, repoName, includeIncompatible, onVariant)
                                        app?.let {
                                            batch.add(it); total++
                                            if (batch.size >= BATCH_SIZE) { batch.forEach(onApp); batch.clear() }
                                        }
                                    }
                                    reader.endArray()
                                }
                                JsonToken.BEGIN_OBJECT -> {
                                    // Some repos: packages is an object; value may be OBJECT (v2-like) or ARRAY (v1 variants-only)
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        val pkg = reader.nextName()
                                        when (reader.peek()) {
                                            JsonToken.BEGIN_OBJECT -> {
                                                val app = parsePackageStreamingBest(
                                                    reader, pkg, baseUrl, repoName, includeIncompatible, onVariant
                                                )
                                                app?.let {
                                                    batch.add(it); total++
                                                    if (batch.size >= BATCH_SIZE) { batch.forEach(onApp); batch.clear() }
                                                }
                                            }
                                            JsonToken.BEGIN_ARRAY -> {
                                                val app = parsePackageV1ArrayForSinglePackage(
                                                    reader, pkg, baseUrl, repoName, metaMap[pkg], includeIncompatible, onVariant
                                                )
                                                app?.let {
                                                    batch.add(it); total++
                                                    if (batch.size >= BATCH_SIZE) { batch.forEach(onApp); batch.clear() }
                                                }
                                            }
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                }
                                else -> reader.skipValue()
                            }
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
        }

        if (batch.isNotEmpty()) batch.forEach(onApp)
        Log.d(TAG, "Parsed $total apps from $repoName (v1)")
    }

    /**
     * v1: packages as array item:
     * { "packageName":"...", "metadata":{...}, "versions":[ {...}, ... ] }
     */
    private fun parsePackageV1Item(
        reader: JsonReader,
        baseUrl: String,
        repoName: String,
        includeIncompatible: Boolean,
        onVariant: (AppVariant) -> Unit
    ): FDroidApp? {
        var pkg: String? = null
        var metadata: Metadata? = null
        val versions = mutableListOf<Version>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "packageName" -> pkg = reader.nextString()
                "metadata" -> metadata = parseMetadata(reader)
                "versions" -> {
                    when (reader.peek()) {
                        JsonToken.BEGIN_ARRAY -> {
                            reader.beginArray()
                            while (reader.hasNext()) versions.add(parseVersionLenient(reader))
                            reader.endArray()
                        }
                        JsonToken.BEGIN_OBJECT -> {
                            reader.beginObject()
                            while (reader.hasNext()) { reader.nextName(); versions.add(parseVersionLenient(reader)) }
                            reader.endObject()
                        }
                        else -> reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val packageName = pkg ?: return null
        val meta = metadata ?: Metadata()
        if (versions.isEmpty()) return null

        // emit variants
        versions.forEach { v ->
            val variant = AppVariant(
                packageName = packageName,
                repositoryUrl = baseUrl,
                repositoryName = repoName,
                versionName = v.versionName,
                versionCode = v.versionCode,
                apkUrl = if (v.file.startsWith("http")) v.file else "$baseUrl/${v.file}",
                sha256 = v.sha256,
                size = v.size,
                isCompatible = isCompatible(v)
            )
            runCatching { onVariant(variant) }
        }

        // pick best
        val best = versions.maxWith(compareBy<Version> { it.versionCode }.thenBy { it.size.takeIf { s -> s > 0 } ?: Long.MAX_VALUE })!!

        val iconUrl = when {
            meta.icon != null -> {
                val iconName = meta.icon["en-US"]?.name ?: meta.icon.entries.firstOrNull()?.value?.name
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
                s.startsWith("http") -> s
                s.startsWith("/") -> "$baseUrl$s"
                else -> "$baseUrl/$s"
            }
        }

        val compatible = isCompatible(best)
        if (!compatible && !includeIncompatible) return null

        return FDroidApp(
            packageName = packageName,
            name = meta.name?.get("en-US") ?: meta.name?.values?.firstOrNull() ?: packageName,
            summary = meta.summary?.get("en-US") ?: meta.summary?.values?.firstOrNull() ?: "",
            description = meta.description?.get("en-US") ?: meta.description?.values?.firstOrNull() ?: "",
            iconUrl = iconUrl,
            version = best.versionName,
            versionCode = best.versionCode,
            size = best.size,
            apkUrl = if (best.file.startsWith("http")) best.file else "$baseUrl/${best.file}",
            license = meta.license ?: "Unknown",
            category = meta.categories.firstOrNull() ?: "Other",
            author = meta.authorName ?: "Unknown",
            website = meta.webSite ?: "",
            sourceCode = meta.sourceCode ?: "",
            added = meta.added,
            lastUpdated = meta.lastUpdated,
            screenshots = shotUrls,
            antiFeatures = meta.antiFeatures,
            repository = repoName,
            repositoryUrl = baseUrl,
            sha256 = best.sha256,
            whatsNew = best.whatsNew ?: "",
            isCompatible = compatible
        )
    }

    /**
     * Use metadata from "apps" array if available.
     */
    private fun parsePackageV1ArrayForSinglePackage(
        reader: JsonReader,
        packageName: String,
        baseUrl: String,
        repoName: String,
        metaFromApps: Metadata?,
        includeIncompatible: Boolean,
        onVariant: (AppVariant) -> Unit
    ): FDroidApp? {
        val versions = mutableListOf<Version>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                versions.add(parseVersionLenient(reader))
            } else {
                reader.skipValue()
            }
        }
        reader.endArray()
        if (versions.isEmpty()) return null

        versions.forEach { v ->
            val variant = AppVariant(
                packageName = packageName,
                repositoryUrl = baseUrl,
                repositoryName = repoName,
                versionName = v.versionName,
                versionCode = v.versionCode,
                apkUrl = if (v.file.startsWith("http")) v.file else "$baseUrl/${v.file}",
                sha256 = v.sha256,
                size = v.size,
                isCompatible = isCompatible(v)
            )
            runCatching { onVariant(variant) }
        }

        val best = versions.maxWith(compareBy<Version> { it.versionCode }.thenBy { it.size.takeIf { s -> s > 0 } ?: Long.MAX_VALUE })!!
        val meta = metaFromApps ?: Metadata()

        val iconUrl = when {
            meta.icon != null -> {
                val iconName = meta.icon["en-US"]?.name ?: meta.icon.entries.firstOrNull()?.value?.name
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
                s.startsWith("http") -> s
                s.startsWith("/") -> "$baseUrl$s"
                else -> "$baseUrl/$s"
            }
        }

        val compatible = isCompatible(best)
        if (!compatible && !includeIncompatible) return null

        return FDroidApp(
            packageName = packageName,
            name = meta.name?.get("en-US") ?: meta.name?.values?.firstOrNull() ?: packageName,
            summary = meta.summary?.get("en-US") ?: meta.summary?.values?.firstOrNull() ?: "",
            description = meta.description?.get("en-US") ?: meta.description?.values?.firstOrNull() ?: "",
            iconUrl = iconUrl,
            version = best.versionName,
            versionCode = best.versionCode,
            size = best.size,
            apkUrl = if (best.file.startsWith("http")) best.file else "$baseUrl/${best.file}",
            license = meta.license ?: "Unknown",
            category = meta.categories.firstOrNull() ?: "Other",
            author = meta.authorName ?: "Unknown",
            website = meta.webSite ?: "",
            sourceCode = meta.sourceCode ?: "",
            added = meta.added,
            lastUpdated = meta.lastUpdated,
            screenshots = shotUrls,
            antiFeatures = meta.antiFeatures,
            repository = repoName,
            repositoryUrl = baseUrl,
            sha256 = best.sha256,
            whatsNew = best.whatsNew ?: "",
            isCompatible = compatible
        )
    }

    private fun parseAppMetaV1(reader: JsonReader): Pair<String, Metadata>? {
        var pkg: String? = null
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
                "packageName" -> pkg = reader.nextString()
                "name" -> name = (name ?: mutableMapOf()).apply { putAll(parseLocalizedStrings(reader)) }
                "summary" -> summary = (summary ?: mutableMapOf()).apply { putAll(parseLocalizedStrings(reader)) }
                "description" -> description = (description ?: mutableMapOf()).apply { putAll(parseLocalizedStrings(reader)) }
                "icon" -> icon = parseLocalizedIcons(reader).toMutableMap()
                "categories" -> categories = parseStringArray(reader)
                "antiFeatures" -> antiFeatures = parseStringArray(reader)
                "license" -> license = when (reader.peek()) {
                    JsonToken.STRING -> reader.nextString()
                    else -> { reader.skipValue(); null }
                }
                "authorName" -> authorName = when (reader.peek()) {
                    JsonToken.STRING -> reader.nextString()
                    else -> { reader.skipValue(); null }
                }
                "webSite" -> webSite = when (reader.peek()) {
                    JsonToken.STRING -> reader.nextString()
                    else -> { reader.skipValue(); null }
                }
                "sourceCode" -> sourceCode = when (reader.peek()) {
                    JsonToken.STRING -> reader.nextString()
                    else -> { reader.skipValue(); null }
                }
                "added" -> added = runCatching { reader.nextLong() }.getOrElse { reader.skipValue(); 0L }
                "lastUpdated" -> lastUpdated = runCatching { reader.nextLong() }.getOrElse { reader.skipValue(); 0L }
                "screenshots" -> screenshots = parseScreenshotsFlexible(reader)
                "localized" -> {
                    // some v1 repos also carry localized block similar to v2
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val loc = reader.nextName()
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "name" -> (name ?: mutableMapOf()).also { name = it }[loc] =
                                    if (reader.peek() == JsonToken.STRING) reader.nextString() else run { reader.skipValue(); "" }
                                "summary" -> (summary ?: mutableMapOf()).also { summary = it }[loc] =
                                    if (reader.peek() == JsonToken.STRING) reader.nextString() else run { reader.skipValue(); "" }
                                "description" -> (description ?: mutableMapOf()).also { description = it }[loc] =
                                    if (reader.peek() == JsonToken.STRING) reader.nextString() else run { reader.skipValue(); "" }
                                "icon" -> {
                                    when (reader.peek()) {
                                        JsonToken.STRING -> (icon ?: mutableMapOf()).also { icon = it }[loc] = IconInfo(reader.nextString())
                                        JsonToken.BEGIN_OBJECT -> {
                                            reader.beginObject()
                                            while (reader.hasNext()) when (reader.nextName()) {
                                                "name" -> (icon ?: mutableMapOf()).also { icon = it }[loc] = IconInfo(reader.nextString())
                                                else -> reader.skipValue()
                                            }
                                            reader.endObject()
                                        }
                                        else -> reader.skipValue()
                                    }
                                }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val p = pkg ?: return null
        return p to Metadata(
            name = name, summary = summary, description = description, icon = icon, categories = categories,
            antiFeatures = antiFeatures, license = license, authorName = authorName, webSite = webSite,
            sourceCode = sourceCode, added = added, lastUpdated = lastUpdated, screenshots = screenshots
        )
    }

    // Version parser tolerant of v1/v2 forms
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
                                    "size" -> size = reader.nextLong()
                                    "sha256" -> sha256 = reader.nextString()
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
                "sha256", "sha256sum" -> sha256 = reader.nextString()
                "manifest", "uses" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "versionCode" -> versionCode = runCatching { reader.nextInt() }.getOrElse { reader.skipValue(); 0 }
                            "versionName" -> versionName = runCatching { reader.nextString() }.getOrElse { reader.skipValue(); "1.0" }
                            "usesSdk", "sdk" -> {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "minSdkVersion", "min" -> minSdk = runCatching { reader.nextInt() }.getOrElse { reader.skipValue(); 1 }
                                        "targetSdkVersion", "target" -> targetSdk = runCatching { reader.nextInt() }.getOrElse { reader.skipValue(); 1 }
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
                "versionName" -> versionName = runCatching { reader.nextString() }.getOrElse { reader.skipValue(); "1.0" }
                "nativecode" -> nativecode = parseStringArray(reader)
                "whatsNew" -> {
                    whatsNew = when (reader.peek()) {
                        JsonToken.STRING -> reader.nextString()
                        JsonToken.BEGIN_OBJECT -> {
                            val map = parseLocalizedStrings(reader)
                            map["en-US"] ?: map.values.firstOrNull()
                        }
                        else -> { reader.skipValue(); null }
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return Version(versionCode, versionName, file, size, sha256, minSdk, targetSdk, nativecode, whatsNew)
    }

    private fun parseRepoBlock(reader: JsonReader) {
        var address: String? = null
        val urls = mutableListOf<String>()
        var primaryUrl: String? = null

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
                                    // Some repos set this; harmless if absent
                                    if (reader.peek() == JsonToken.BOOLEAN) isPrimary = reader.nextBoolean()
                                    else reader.skipValue()
                                }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        url?.let {
                            urls.add(it)
                            if (isPrimary) primaryUrl = it
                        }
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (!address.isNullOrBlank()) {
            val base = address.trim().trimEnd('/')
            // Treat repo.address as primary if index didn’t mark any mirror explicitly
            val declaredPrimary = primaryUrl ?: base
            MirrorRegistry.register(base, listOf(base) + urls, declaredPrimary)
        }
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
        val whatsNew: String? = null
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
                isCompatible = isCompatible(v)
            )
            runCatching { onVariant(variant) }
        }

        val meta = metadata ?: Metadata()
        val bestVersion = best ?: return null

        val resolvedIconUrl = when {
            meta.icon != null -> {
                val iconName = meta.icon["en-US"]?.name
                    ?: meta.icon.entries.firstOrNull()?.value?.name
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

        val compatible = isCompatible(bestVersion)
        if (!compatible && !includeIncompatible) return null

        return FDroidApp(
            packageName = packageName,
            name = meta.name?.get("en-US") ?: meta.name?.values?.firstOrNull() ?: packageName,
            summary = meta.summary?.get("en-US") ?: meta.summary?.values?.firstOrNull() ?: "",
            description = meta.description?.get("en-US") ?: meta.description?.values?.firstOrNull() ?: "",
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
            antiFeatures = meta.antiFeatures,
            repository = repoName,
            repositoryUrl = baseUrl,
            sha256 = bestVersion.sha256,
            whatsNew = bestVersion.whatsNew ?: "",
            isCompatible = compatible
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
                    "name" -> if (reader.peek() == JsonToken.STRING) locName = reader.nextString() else reader.skipValue()
                    "summary" -> if (reader.peek() == JsonToken.STRING) locSummary = reader.nextString() else reader.skipValue()
                    "description" -> if (reader.peek() == JsonToken.STRING) locDescription = reader.nextString() else reader.skipValue()
                    "icon" -> {
                        when (reader.peek()) {
                            JsonToken.STRING -> locIcon = IconInfo(reader.nextString())
                            JsonToken.BEGIN_OBJECT -> {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "name" -> locIcon = IconInfo(reader.nextString())
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

    private fun isCompatible(version: Version): Boolean {
        val sdkOk = Build.VERSION.SDK_INT >= version.minSdkVersion
        val abiOk = version.nativecode.isEmpty() ||
                version.nativecode.any { repoAbi ->
                    Build.SUPPORTED_ABIS.any { deviceAbi -> deviceAbi.equals(repoAbi, ignoreCase = true) }
                }
        return sdkOk && abiOk
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

    private fun parseLocalizedIcons(reader: JsonReader): Map<String, IconInfo> {
        val map = mutableMapOf<String, IconInfo>()
        reader.beginObject()
        while (reader.hasNext()) {
            val locale = reader.nextName()
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "name" -> map[locale] = IconInfo(reader.nextString())
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        reader.endObject()
        return map
    }

    private fun parseStringArray(reader: JsonReader): List<String> {
        val list = mutableListOf<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() == JsonToken.STRING) {
                list.add(reader.nextString())
            } else {
                reader.skipValue()
            }
        }
        reader.endArray()
        return list
    }

    private fun parseScreenshotsFlexible(reader: JsonReader): List<String> {
        return when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> parseScreenshotsArray(reader)
            JsonToken.BEGIN_OBJECT -> parseScreenshotsObject(reader)
            JsonToken.STRING -> listOf(reader.nextString())
            else -> {
                reader.skipValue()
                emptyList()
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
                            "name" -> name = reader.nextString()
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
                "description" -> description = (description ?: mutableMapOf()).apply { putAll(parseLocalizedStrings(reader)) }
                "icon" -> icon = parseLocalizedIcons(reader).toMutableMap()
                "categories" -> categories = parseStringArray(reader)
                "antiFeatures" -> antiFeatures = parseStringArray(reader)
                "license" -> license = when (reader.peek()) {
                    JsonToken.STRING -> reader.nextString()
                    else -> { reader.skipValue(); null }
                }
                "authorName" -> authorName = when (reader.peek()) {
                    JsonToken.STRING -> reader.nextString()
                    else -> { reader.skipValue(); null }
                }
                "webSite" -> webSite = when (reader.peek()) {
                    JsonToken.STRING -> reader.nextString()
                    else -> { reader.skipValue(); null }
                }
                "sourceCode" -> sourceCode = when (reader.peek()) {
                    JsonToken.STRING -> reader.nextString()
                    else -> { reader.skipValue(); null }
                }
                "added" -> added = runCatching { reader.nextLong() }.getOrElse { reader.skipValue(); 0L }
                "lastUpdated" -> lastUpdated = runCatching { reader.nextLong() }.getOrElse { reader.skipValue(); 0L }
                "screenshots" -> screenshots = parseScreenshotsFlexible(reader)
                "localized" -> {
                    val loc = parseLocalizedBlock(reader)
                    name = (name ?: mutableMapOf()).apply { putAll(loc.names) }
                    summary = (summary ?: mutableMapOf()).apply { putAll(loc.summaries) }
                    description = (description ?: mutableMapOf()).apply { putAll(loc.descriptions) }
                    icon = (icon ?: mutableMapOf()).apply { putAll(loc.icons) }
                    if (screenshots.isNullOrEmpty()) {
                        screenshots = loc.screenshots["en-US"] ?: loc.screenshots.values.firstOrNull { it.isNotEmpty() }
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
}