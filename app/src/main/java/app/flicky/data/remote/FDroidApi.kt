package app.flicky.data.remote

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.RepositoryInfo
import app.flicky.data.trust.HttpsOnlyVerifier
import app.flicky.data.trust.RepoSignatureVerifier
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.*
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

class FDroidApi(
    context: Context,
    private val verifier: RepoSignatureVerifier = HttpsOnlyVerifier()
) {
    companion object {
        private const val TAG = "FDroidApi"
        private const val BATCH_SIZE = 100
        private const val INITIAL_MAP_CAPACITY = 1000
        private const val CONNECTION_TIMEOUT_MS = 30_000
        private const val REQUEST_TIMEOUT_MS = 60_000
        private const val MAX_RETRIES = 3

        val F_DROID_MIRRORS = listOf(
            "https://f-droid.org/repo",
            "https://mirror.f-droid.org/repo"
        )
    }

    private val client = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS.toLong()
            connectTimeoutMillis = CONNECTION_TIMEOUT_MS.toLong()
            socketTimeoutMillis = CONNECTION_TIMEOUT_MS.toLong()
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = MAX_RETRIES)
            exponentialDelay()
        }

        engine {
            connectTimeout = CONNECTION_TIMEOUT_MS
            socketTimeout = CONNECTION_TIMEOUT_MS
            // Don't use deprecated threadsCount
        }

        defaultRequest {
            header("User-Agent", "Flicky/1.1 (Android ${Build.VERSION.RELEASE})")
            header("Accept", "application/json")
        }
    }

    private val metrics: DisplayMetrics = context.applicationContext.resources.displayMetrics

    data class ApkVariant(
        val url: String,
        val size: Long,
        val sha256: String = "",
        val abis: List<String> = emptyList(),
        val minSdk: Int = 1,
        val maxSdk: Int = Int.MAX_VALUE,
        val densities: List<String> = emptyList(),
        val versionCode: Long = 0,
        val versionName: String = "1.0"
    )

    private data class AppMeta(
        val packageName: String,
        val name: String,
        val summary: String,
        val description: String,
        val iconUrl: String,
        val license: String,
        val category: String,
        val author: String,
        val website: String,
        val sourceCode: String,
        val added: Long,
        val lastUpdated: Long,
        val screenshots: List<String>,
        val repository: String,
        val antiFeatures: List<String> = emptyList()
    )

    data class RepoHeaders(val etag: String?, val lastModified: String?)

    suspend fun fetchWithCache(
        repo: RepositoryInfo,
        previous: RepoHeaders,
        force: Boolean = false,
        onApp: suspend (FDroidApp) -> Unit
    ): RepoHeaders? = withContext(Dispatchers.IO) {
        val bases = if (repo.url.contains("f-droid.org")) F_DROID_MIRRORS else listOf(repo.url)

        for (base0 in bases) {
            val base = normalizeUrl(base0)
            if (!verifier.isTrustedRepoUrl(base)) continue

            // Try v2 format first
            try {
                val entryResp = get("$base/entry.json", if (force) null else previous.etag, if (force) null else previous.lastModified)
                if (entryResp.status304) return@withContext previous

                if (entryResp.res?.status?.isSuccess() == true) {
                    val indexName = parseEntryIndexName(entryResp.res) ?: "index-v1.json"
                    val indexUrl = "$base/$indexName"

                    val indexResp = get(indexUrl, if (force) null else previous.etag, if (force) null else previous.lastModified)
                    if (indexResp.status304) return@withContext previous

                    if (indexResp.res?.status?.isSuccess() == true) {
                        parseIndexStreamingOptimized(indexResp.res, base, repo.name, onApp)

                        val etag = indexResp.res.headers[HttpHeaders.ETag] ?: entryResp.res.headers[HttpHeaders.ETag]
                        val lastMod = indexResp.res.headers[HttpHeaders.LastModified] ?: entryResp.res.headers[HttpHeaders.LastModified]
                        return@withContext RepoHeaders(etag, lastMod)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error with v2 format for ${repo.name}: ${e.message}")
                // Continue to v1 fallback
            }

            // Try v1 format as fallback
            try {
                val v1Resp = get("$base/index-v1.json", if (force) null else previous.etag, if (force) null else previous.lastModified)
                if (v1Resp.status304) return@withContext previous

                if (v1Resp.res?.status?.isSuccess() == true) {
                    parseIndexStreamingOptimized(v1Resp.res, base, repo.name, onApp)

                    val etag = v1Resp.res.headers[HttpHeaders.ETag]
                    val lastMod = v1Resp.res.headers[HttpHeaders.LastModified]
                    return@withContext RepoHeaders(etag, lastMod)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error with v1 format for ${repo.name}: ${e.message}")
                // Continue to next mirror
            }
        }
        null
    }

    private data class Resp(val res: HttpResponse?, val status304: Boolean)

    private suspend fun get(url: String, etag: String?, lastModified: String?): Resp {
        val resp = client.get(url) {
            etag?.let { header(HttpHeaders.IfNoneMatch, it) }
            lastModified?.let { header(HttpHeaders.IfModifiedSince, it) }
        }
        return Resp(
            res = if (resp.status.value == 304) null else resp,
            status304 = resp.status.value == 304
        )
    }

    private suspend fun parseEntryIndexName(resp: HttpResponse): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = resp.bodyAsChannel().toInputStream()
            inputStream.use { stream ->
                JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        if (reader.nextName() == "index" && reader.peek() == JsonToken.BEGIN_OBJECT) {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                if (reader.nextName() == "name" && reader.peek() == JsonToken.STRING) {
                                    return@withContext reader.nextString()
                                } else {
                                    reader.skipValue()
                                }
                            }
                            reader.endObject()
                        } else {
                            reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing entry.json", e)
        }
        null
    }

    private suspend fun parseIndexStreamingOptimized(
        resp: HttpResponse,
        base: String,
        repoName: String,
        onApp: suspend (FDroidApp) -> Unit
    ) = withContext(Dispatchers.IO) {
        val bestByPackage = HashMap<String, ApkVariant>(INITIAL_MAP_CAPACITY)
        val batch = mutableListOf<FDroidApp>()

        try {
            val inputStream = resp.bodyAsChannel().toInputStream()
            inputStream.use { stream ->
                JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "packages" -> parsePackagesObject(reader, base, bestByPackage)
                            "apps" -> parseAppsArray(reader, base, repoName, bestByPackage) { app ->
                                batch.add(app)
                                if (batch.size >= BATCH_SIZE) {
                                    batch.forEach { onApp(it) }
                                    batch.clear()
                                }
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            }
            // Emit remaining apps
            batch.forEach { onApp(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing index for $repoName", e)
            throw e
        }
    }

    private fun parsePackagesObject(reader: JsonReader, base: String, bestByPackage: MutableMap<String, ApkVariant>) {
        reader.beginObject()
        while (reader.hasNext()) {
            val pkg = reader.nextName()
            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                var best: ApkVariant? = null
                reader.beginArray()
                while (reader.hasNext()) {
                    val v = parseVersionObject(reader, base)
                    best = pickBetter(best, v)
                }
                reader.endArray()
                if (best != null) bestByPackage[pkg] = best
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
    }

    private inline fun parseAppsArray(
        reader: JsonReader,
        base: String,
        repoName: String,
        bestByPackage: Map<String, ApkVariant>,
        onApp: (FDroidApp) -> Unit
    ) {
        reader.beginArray()
        while (reader.hasNext()) {
            parseSingleAppObject(reader, base, repoName, bestByPackage)?.let { app ->
                onApp(app)
            }
        }
        reader.endArray()
    }

    private fun parseSingleAppObject(
        reader: JsonReader,
        base: String,
        repoName: String,
        bestByPackage: Map<String, ApkVariant>
    ): FDroidApp? {
        var pkg = ""
        var name = ""
        var summary = ""
        var desc = ""
        var iconUrl = "https://f-droid.org/assets/ic_repo_app_default.png"
        var license = "Unknown"
        var category = "Other"
        var author = "Unknown"
        var website = ""
        var source = ""
        var added = 0L
        var updated = 0L
        var screenshots: List<String> = emptyList()
        var antiFeatures: List<String> = emptyList()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "packageName" -> pkg = safeString(reader)
                "name" -> name = readLocalizedString(reader)
                "summary" -> summary = readLocalizedString(reader)
                "description" -> desc = readLocalizedString(reader)
                "icon" -> {
                    val ic = safeString(reader)
                    iconUrl = when {
                        ic.startsWith("http") -> ic
                        ic.isNotBlank() -> "$base/icons-640/$ic"
                        else -> iconUrl
                    }
                }
                "license" -> license = safeString(reader, "Unknown")
                "categories" -> category = readFirstStringFromArray(reader)?.let { normalizeCategory(it) } ?: "Other"
                "authorName" -> author = safeString(reader, "Unknown")
                "webSite" -> website = safeString(reader)
                "sourceCode" -> source = safeString(reader)
                "added" -> added = safeLong(reader)
                "lastUpdated" -> updated = safeLong(reader)
                "screenshots" -> screenshots = readScreenshotsAny(reader, base)
                "antiFeatures" -> antiFeatures = readStringArray(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val v = bestByPackage[pkg] ?: return null
        if (pkg.isBlank() || v.url.isBlank()) return null

        return FDroidApp(
            packageName = pkg,
            name = name.ifBlank { pkg },
            summary = summary,
            description = desc,
            iconUrl = iconUrl,
            version = v.versionName.ifBlank { "1.0" },
            versionCode = v.versionCode.toInt(),
            size = v.size,
            apkUrl = v.url,
            license = license,
            category = category,
            author = author,
            website = website,
            sourceCode = source,
            added = added,
            lastUpdated = updated,
            screenshots = screenshots,
            antiFeatures = antiFeatures,
            downloads = 0L,
            isInstalled = false,
            repository = repoName,
            sha256 = v.sha256
        )
    }

    private fun parseVersionObject(reader: JsonReader, base: String): ApkVariant {
        var url = ""
        var size = 0L
        var sha = ""
        var versionCode = 0L
        var versionName = "1.0"
        var minSdk = 1
        var maxSdk = Int.MAX_VALUE
        var abis: List<String> = emptyList()
        var densities: List<String> = emptyList()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "file" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "name" -> {
                                val name = safeString(reader)
                                if (name.isNotBlank()) {
                                    url = if (name.startsWith("http")) name else "$base/$name"
                                }
                            }
                            "size" -> size = safeLong(reader)
                            "sha256" -> sha = safeString(reader)
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                "apkName" -> {
                    val name = safeString(reader)
                    if (name.isNotBlank()) {
                        url = if (name.startsWith("http")) name else "$base/$name"
                    }
                }
                "apkBytes", "apkSize", "size" -> size = safeLong(reader)
                "hashes" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "sha256" -> sha = safeString(reader)
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                "manifest" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "minSdkVersion" -> minSdk = safeInt(reader, 1)
                            "maxSdkVersion" -> maxSdk = safeInt(reader, Int.MAX_VALUE)
                            "nativecode" -> abis = readStringArray(reader)
                            "supportsScreens", "densities" -> densities = readStringArray(reader)
                            "versionCode" -> versionCode = safeLong(reader)
                            "versionName" -> versionName = safeString(reader)
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                "minSdkVersion" -> minSdk = safeInt(reader, 1)
                "maxSdkVersion" -> maxSdk = safeInt(reader, Int.MAX_VALUE)
                "nativecode" -> abis = readStringArray(reader)
                "supportsScreens", "densities" -> densities = readStringArray(reader)
                "versionCode" -> versionCode = safeLong(reader)
                "versionName" -> versionName = safeString(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return ApkVariant(url, size, sha, abis, minSdk, maxSdk, densities, versionCode, versionName)
    }

    private fun isCompatible(v: ApkVariant): Boolean {
        val abiOk = v.abis.isEmpty() || v.abis.any { abi ->
            abi.equals("all", ignoreCase = true) || Build.SUPPORTED_ABIS.any { it.equals(abi, ignoreCase = true) }
        }
        val sdkOk = Build.VERSION.SDK_INT in v.minSdk..v.maxSdk
        val deviceDensity = densityQualifier(metrics)
        val densityOk = v.densities.isEmpty() ||
                v.densities.any { it.equals("any", true) || it.equals("nodpi", true) || it.equals(deviceDensity, true) }
        return abiOk && sdkOk && densityOk
    }

    private fun pickBetter(a: ApkVariant?, b: ApkVariant): ApkVariant {
        if (a == null) return b
        val ac = isCompatible(a)
        val bc = isCompatible(b)
        return when {
            ac && !bc -> a
            !ac && bc -> b
            else -> when {
                b.versionCode > a.versionCode -> b
                b.versionCode < a.versionCode -> a
                b.size > a.size -> b
                else -> a
            }
        }
    }

    // Keep all the helper methods from original
    private fun safeString(reader: JsonReader, def: String = ""): String = when (reader.peek()) {
        JsonToken.STRING -> reader.nextString()
        JsonToken.NULL -> { reader.nextNull(); def }
        else -> { reader.skipValue(); def }
    }

    private fun safeLong(reader: JsonReader, def: Long = 0L): Long = when (reader.peek()) {
        JsonToken.NUMBER -> try { reader.nextLong() } catch (_: Exception) { reader.nextString().toLongOrNull() ?: def }
        JsonToken.STRING -> reader.nextString().toLongOrNull() ?: def
        JsonToken.NULL -> { reader.nextNull(); def }
        else -> { reader.skipValue(); def }
    }

    private fun safeInt(reader: JsonReader, def: Int = 0): Int = when (reader.peek()) {
        JsonToken.NUMBER -> try { reader.nextInt() } catch (_: Exception) { reader.nextString().toIntOrNull() ?: def }
        JsonToken.STRING -> reader.nextString().toIntOrNull() ?: def
        JsonToken.NULL -> { reader.nextNull(); def }
        else -> { reader.skipValue(); def }
    }

    private fun readStringArray(reader: JsonReader): List<String> {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) { reader.skipValue(); return emptyList() }
        val out = ArrayList<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() == JsonToken.STRING) out += reader.nextString() else reader.skipValue()
        }
        reader.endArray()
        return out
    }

    private fun readFirstStringFromArray(reader: JsonReader): String? {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) { reader.skipValue(); return null }
        var res: String? = null
        reader.beginArray()
        if (reader.hasNext() && reader.peek() == JsonToken.STRING) res = reader.nextString() else if (reader.hasNext()) reader.skipValue()
        while (reader.hasNext()) reader.skipValue()
        reader.endArray()
        return res
    }

    private fun readScreenshotsAny(reader: JsonReader, base: String): List<String> {
        return when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> {
                val list = ArrayList<String>()
                reader.beginArray()
                while (reader.hasNext()) if (reader.peek() == JsonToken.STRING) {
                    val s = reader.nextString()
                    list += if (s.startsWith("http")) s else "$base/$s"
                } else reader.skipValue()
                reader.endArray()
                list
            }
            JsonToken.BEGIN_OBJECT -> {
                var out: List<String> = emptyList()
                reader.beginObject()
                while (reader.hasNext()) {
                    val k = reader.nextName()
                    if (reader.peek() == JsonToken.BEGIN_ARRAY && (k == "en" || k == "en-US" || k == "en_US" || k == "en_GB")) {
                        out = readScreenshotsAny(reader, base)
                    } else reader.skipValue()
                }
                reader.endObject()
                out
            }
            else -> { reader.skipValue(); emptyList() }
        }
    }

    private fun readLocalizedString(reader: JsonReader): String {
        return when (reader.peek()) {
            JsonToken.STRING -> reader.nextString()
            JsonToken.BEGIN_OBJECT -> {
                var res = ""
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (reader.peek() == JsonToken.STRING &&
                        (name == "en" || name == "en-US" || name == "en_US" || name == "en_GB")) {
                        res = reader.nextString()
                    } else reader.skipValue()
                }
                reader.endObject()
                res
            }
            else -> { reader.skipValue(); "" }
        }
    }

    private fun normalizeCategory(source: String): String {
        val cleaned = source.replace("_", " ").trim()
        val map = mapOf(
            "System" to "System", "Development" to "Development", "Games" to "Games", "Internet" to "Internet",
            "Multimedia" to "Multimedia", "Navigation" to "Navigation", "Phone & SMS" to "Phone & SMS",
            "Phone SMS" to "Phone & SMS", "Reading" to "Reading", "Science & Education" to "Science & Education",
            "Science Education" to "Science & Education", "Security" to "Security", "Sports & Health" to "Sports & Health",
            "Sports Health" to "Sports & Health", "Theming" to "Theming", "Time" to "Time", "Writing" to "Writing",
            "Money" to "Money", "Connectivity" to "Connectivity", "Graphics" to "Graphics"
        )
        return map[cleaned] ?: cleaned.ifBlank { "Other" }
    }

    private fun densityQualifier(m: DisplayMetrics): String {
        val dpi = m.densityDpi
        return when {
            dpi <= 120 -> "ldpi"
            dpi <= 160 -> "mdpi"
            dpi <= 240 -> "hdpi"
            dpi <= 320 -> "xhdpi"
            dpi <= 480 -> "xxhdpi"
            else -> "xxxhdpi"
        }
    }

    private fun normalizeUrl(u: String): String {
        var url = u.trimEnd('/')
        if (!url.startsWith("http")) url = "https://$url"
        return url
    }
}