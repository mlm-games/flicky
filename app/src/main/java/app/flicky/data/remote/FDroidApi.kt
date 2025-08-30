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
        includeIncompatible: Boolean = true,
        onApp: suspend (FDroidApp) -> Unit,
        onVariant: (AppVariant) -> Unit = {}
    ): FetchResult? = withContext(Dispatchers.IO) {
        val baseUrl = repo.url.trimEnd('/')

        fun buildRequest(method: String): Request {
            val builder = Request.Builder()
                .url("$baseUrl/index-v2.json")
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

        if (!force && enableDifferential) {
            var headCall: okhttp3.Call? = null
            try {
                headCall = client.newCall(buildRequest("HEAD"))
                currentCall.set(headCall)
                headCall.execute().use { head ->
                    when (head.code) {
                        304 -> {
                            Log.d(TAG, "HEAD 304 Not Modified for ${repo.name}")
                            return@withContext FetchResult(previous, modified = false)
                        }
                        405, 501 -> {
                            // Some servers/CDNs do not support HEAD or conditional headers on HEAD
                        }
                        else -> {
                            // Proceed to GET
                        }
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
                getCall = client.newCall(buildRequest("GET"))
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
                        return@withContext null
                    }

                    Log.d(TAG, "Parsing index for ${repo.name}")
                    parseIndexV2(resp, baseUrl, repo.name, onApp, includeIncompatible, onVariant)

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
        onApp: suspend (FDroidApp) -> Unit,
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
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val packageName = reader.nextName()
                                val app = parsePackageStreamingBest(reader, packageName, baseUrl, repoName, includeIncompatible, onVariant)
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
        Log.d(TAG, "Parsed $totalApps apps from $repoName")
    }

    private fun parseRepoBlock(reader: JsonReader) {
        var address: String? = null
        val mirrors = mutableListOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "address" -> address = reader.nextString()
                "mirrors" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        var url: String? = null
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "url" -> url = reader.nextString()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        url?.let { mirrors.add(it) }
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (!address.isNullOrBlank()) {
            val all = listOf(address) + mirrors
            MirrorRegistry.register(address, all)
        }
    }


    /**
     * Emits all version variants via onVariant and returns the "best" FDroidApp for lists.
     */
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
                    reader.beginObject()
                    while (reader.hasNext()) {
                        reader.nextName() // version hash
                        val v = parseVersion(reader)
                        variants.add(v)
                        // Compute best
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
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        // Emit all variants
        variants.forEach { v ->
            val variant = AppVariant(
                packageName = packageName,
                repositoryUrl = baseUrl,
                repositoryName = repoName,
                versionName = v.versionName,
                versionCode = v.versionCode,
                apkUrl = "$baseUrl/${v.file}",
                sha256 = v.sha256,
                size = v.size,
                isCompatible = isCompatible(v)
            )
            runCatching { onVariant(variant) }
        }

        val meta = metadata ?: return null
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
        if (!compatible && !includeIncompatible) {
            return null
        }

        return FDroidApp(
            packageName = packageName,
            name = meta.name?.get("en-US") ?: meta.name?.values?.firstOrNull() ?: packageName,
            summary = meta.summary?.get("en-US") ?: meta.summary?.values?.firstOrNull() ?: "",
            description = meta.description?.get("en-US") ?: meta.description?.values?.firstOrNull() ?: "",
            iconUrl = resolvedIconUrl,
            version = bestVersion.versionName,
            versionCode = bestVersion.versionCode,
            size = bestVersion.size,
            apkUrl = "$baseUrl/${bestVersion.file}",
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
                "license" -> license = reader.nextString()
                "authorName" -> authorName = reader.nextString()
                "webSite" -> webSite = reader.nextString()
                "sourceCode" -> sourceCode = reader.nextString()
                "added" -> added = reader.nextLong()
                "lastUpdated" -> lastUpdated = reader.nextLong()
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

    private fun parseVersion(reader: JsonReader): Version {
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
                "manifest" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "versionCode" -> versionCode = reader.nextInt()
                            "versionName" -> versionName = reader.nextString()
                            "usesSdk" -> {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "minSdkVersion" -> minSdk = reader.nextInt()
                                        "targetSdkVersion" -> targetSdk = reader.nextInt()
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
                "whatsNew" -> {
                    whatsNew = when (reader.peek()) {
                        JsonToken.STRING -> reader.nextString()
                        JsonToken.BEGIN_OBJECT -> {
                            val map = parseLocalizedStrings(reader)
                            map["en-US"] ?: map.values.firstOrNull()
                        }
                        else -> {
                            reader.skipValue()
                            null
                        }
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return Version(versionCode, versionName, file, size, sha256, minSdk, targetSdk, nativecode, whatsNew)
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
            reader.nextName() // key (locale/bucket)
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
}