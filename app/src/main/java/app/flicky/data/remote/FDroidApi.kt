package app.flicky.data.remote

import android.content.Context
import android.os.Build
import android.util.Log
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.RepositoryInfo
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class FDroidApi(context: Context) {
    companion object {
        private const val TAG = "FDroidApi"
        private const val BATCH_SIZE = 50 // for TV memory constraints
        private const val MAX_RETRIES = 2
        private const val RETRY_BACKOFF_MS = 1200L
    }

    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    data class RepoHeaders(val etag: String?, val lastModified: String?)

    suspend fun fetchWithCache(
        repo: RepositoryInfo,
        previous: RepoHeaders,
        force: Boolean = false,
        onApp: suspend (FDroidApp) -> Unit
    ): RepoHeaders? = withContext(Dispatchers.IO) {
        val baseUrl = repo.url.trimEnd('/')

        fun buildRequest(): Request {
            val builder = Request.Builder()
                .url("$baseUrl/index-v2.json")
                .get()
                .header("User-Agent", "Flicky/1.1 TV")
                .header("Accept", "application/json")
            if (!force) {
                previous.etag?.let { builder.header("If-None-Match", it) }
                previous.lastModified?.let { builder.header("If-Modified-Since", it) }
            }
            return builder.build()
        }

        var attempt = 0
        var lastException: Exception? = null
        while (attempt <= MAX_RETRIES) {
            try {
                client.newCall(buildRequest()).execute().use { resp ->
                    if (resp.code == 304) {
                        Log.d(TAG, "Repository ${repo.name} has not changed (304)")
                        return@withContext previous
                    }

                    if (!resp.isSuccessful) {
                        val code = resp.code
                        Log.w(TAG, "Non-success ${code} for ${repo.name}")
                        if (code >= 500 && attempt < MAX_RETRIES) {
                            attempt++
                            delay(RETRY_BACKOFF_MS * attempt)
                            return@use
                        }
                        return@withContext null
                    }

                    Log.d(TAG, "Parsing index for ${repo.name}")
                    parseIndexV2(resp, baseUrl, repo.name, onApp)

                    val etag = resp.header("ETag")
                    val lastMod = resp.header("Last-Modified")
                    return@withContext RepoHeaders(etag, lastMod)
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
            }
        }
        Log.e(TAG, "Failed to fetch ${repo.name}", lastException)
        null
    }

    private suspend fun parseIndexV2(
        resp: Response,
        baseUrl: String,
        repoName: String,
        onApp: suspend (FDroidApp) -> Unit
    ) = withContext(Dispatchers.IO) {
        var totalApps = 0
        val batch = mutableListOf<FDroidApp>()

        val body = resp.body ?: return@withContext
        InputStreamReader(body.byteStream()).use { isr ->
            JsonReader(isr).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "packages" -> {
                            reader.beginObject() // packages object
                            while (reader.hasNext()) {
                                val packageName = reader.nextName()
                                val app = parsePackageStreamingBest(reader, packageName, baseUrl, repoName)
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

    /**
     * Best-version selection with size tie-breaker for equal versionCode.
     * Also extracts screenshots and what's new (localized if available).
     */
    private fun parsePackageStreamingBest(
        reader: JsonReader,
        packageName: String,
        baseUrl: String,
        repoName: String
    ): FDroidApp? {
        var metadata: Metadata? = null
        var best: Version? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "metadata" -> metadata = parseMetadata(reader)
                "versions" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        reader.nextName() // version hash
                        val v = parseVersion(reader)
                        if (!isCompatible(v)) continue
                        best = when {
                            best == null -> v
                            v.versionCode > best!!.versionCode -> v
                            v.versionCode == best!!.versionCode &&
                                    v.size in 1..Long.MAX_VALUE &&
                                    best!!.size in 1..Long.MAX_VALUE &&
                                    v.size < best!!.size -> v
                            else -> best
                        }
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val meta = metadata ?: return null
        val bestVersion = best ?: return null

        val resolvedIconUrl = when {
            meta.icon != null -> {
                val iconName = meta.icon["en-US"]?.name
                if (!iconName.isNullOrBlank()) "$baseUrl/$iconName"
                else "$baseUrl/icons/$packageName.png"
            }
            // archive fallback
            repoName == "F-Droid Archive" -> "https://f-droid.org/repo/icons/$packageName.png"
            else -> "$baseUrl/icons/$packageName.png"
        }

        val shotUrls = (meta.screenshots ?: emptyList()).map { s ->
            if (s.startsWith("http://") || s.startsWith("https://")) s else "$baseUrl/$s"
        }

        return FDroidApp(
            packageName = packageName,
            name = meta.name?.get("en-US") ?: packageName,
            summary = meta.summary?.get("en-US") ?: "",
            description = meta.description?.get("en-US") ?: "",
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
            sha256 = bestVersion.sha256,
            whatsNew = bestVersion.whatsNew ?: ""
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
        var name: Map<String, String>? = null
        var summary: Map<String, String>? = null
        var description: Map<String, String>? = null
        var icon: Map<String, IconInfo>? = null
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
                "name" -> name = parseLocalizedStrings(reader)
                "summary" -> summary = parseLocalizedStrings(reader)
                "description" -> description = parseLocalizedStrings(reader)
                "icon" -> icon = parseLocalizedIcons(reader)
                "categories" -> categories = parseStringArray(reader)
                "antiFeatures" -> antiFeatures = parseStringArray(reader)
                "license" -> license = reader.nextString()
                "authorName" -> authorName = reader.nextString()
                "webSite" -> webSite = reader.nextString()
                "sourceCode" -> sourceCode = reader.nextString()
                "added" -> added = reader.nextLong()
                "lastUpdated" -> lastUpdated = reader.nextLong()
                "screenshots" -> screenshots = parseScreenshotsArray(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return Metadata(
            name, summary, description, icon, categories, antiFeatures,
            license, authorName, webSite, sourceCode, added, lastUpdated, screenshots
        )
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
                            // prefer en-US, then any first
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
                version.nativecode.any { abi ->
                    Build.SUPPORTED_ABIS.any { it.contains(abi, ignoreCase = true) }
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

    /**
     * Screenshots can be strings or objects with a "name" field; accept both.
     */
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
                else -> reader.skipValue()
            }
        }
        reader.endArray()
        return list
    }
}