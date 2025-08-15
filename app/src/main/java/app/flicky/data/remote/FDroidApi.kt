package app.flicky.data.remote

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.RepositoryInfo
import app.flicky.data.trust.HttpsOnlyVerifier
import app.flicky.data.trust.RepoSignatureVerifier
import com.google.gson.stream.JsonReader
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.InputStreamReader

class FDroidApi(
    context: Context,
    private val verifier: RepoSignatureVerifier = HttpsOnlyVerifier()
) {
    private val client = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 30_000
        }
        defaultRequest {
            header("User-Agent", "Flicky/1.1 (Android TV)")
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

    data class RepoHeaders(val etag: String?, val lastModified: String?)

    companion object {
        val F_DROID_MIRRORS = listOf(
            "https://f-droid.org/repo",
            "https://mirror.f-droid.org/repo"
        )
    }

    private fun normalizeUrl(u: String): String {
        var url = u.trimEnd('/')
        if (!url.startsWith("http")) url = "https://$url"
        return url
    }

    suspend fun fetchWithCache(
        repo: RepositoryInfo,
        previous: RepoHeaders,
        force: Boolean = false,
        onApp: ((FDroidApp) -> Unit)
    ): RepoHeaders? {
        val bases = if (repo.url.contains("f-droid.org")) F_DROID_MIRRORS else listOf(repo.url)
        for (base0 in bases) {
            val base = normalizeUrl(base0)
            if (!verifier.isTrustedRepoUrl(base)) continue

            // Try v2 entry.json -> index
            try {
                val entry = get("$base/entry.json", if (force) null else previous.etag, if (force) null else previous.lastModified)
                if (entry.status304) return RepoHeaders(previous.etag, previous.lastModified)
                if (entry.res?.status?.isSuccess() == true) {
                    // Remove .use here - HttpResponse doesn't need it
                    val resp = entry.res
                    val (indexName) = parseEntryIndexName(resp)
                    val indexUrl = if (!indexName.isNullOrBlank()) "$base/$indexName" else "$base/index-v1.json"
                    val idx = get(indexUrl, if (force) null else previous.etag, if (force) null else previous.lastModified)
                    if (idx.status304) return RepoHeaders(previous.etag, previous.lastModified)
                    if (idx.res?.status?.isSuccess() == true) {
                        // Remove .use here too
                        val r = idx.res
                        parseIndexStreaming(r, base, repoName = repo.name, onApp = onApp)
                        val etag = r.headers[HttpHeaders.ETag] ?: entry.res.headers[HttpHeaders.ETag]
                        val last = r.headers[HttpHeaders.LastModified] ?: entry.res.headers[HttpHeaders.LastModified]
                        return RepoHeaders(etag, last)
                    }
                }
            } catch (_: Exception) {
                // fallback to v1 or next mirror
            }

            // Try v1 directly
            try {
                val v1 = get("$base/index-v1.json", if (force) null else previous.etag, if (force) null else previous.lastModified)
                if (v1.status304) return RepoHeaders(previous.etag, previous.lastModified)
                if (v1.res?.status?.isSuccess() == true) {
                    // Remove .use here as well
                    val resp = v1.res
                    parseIndexStreaming(resp, base, repoName = repo.name, onApp = onApp)
                    val etag = resp.headers[HttpHeaders.ETag]
                    val last = resp.headers[HttpHeaders.LastModified]
                    return RepoHeaders(etag, last)
                }
            } catch (_: Exception) {
                // next mirror
            }
        }
        return null
    }

    private data class Resp(val res: HttpResponse?, val status304: Boolean)
    private suspend fun get(url: String, etag: String?, lastModified: String?): Resp {
        val resp = client.get(url) {
            etag?.let { header(HttpHeaders.IfNoneMatch, it) }
            lastModified?.let { header(HttpHeaders.IfModifiedSince, it) }
        }
        return Resp(resp, resp.status.value == 304)
    }

    private suspend fun parseEntryIndexName(resp: HttpResponse): Pair<String?, String?> {
        val inputStream = resp.bodyAsChannel().toInputStream()
        inputStream.use { stream ->
            JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { r ->
                var name: String? = null
                r.beginObject()
                while (r.hasNext()) {
                    val n = r.nextName()
                    if (n == "index" && r.peek() == com.google.gson.stream.JsonToken.BEGIN_OBJECT) {
                        r.beginObject()
                        while (r.hasNext()) {
                            val k = r.nextName()
                            if (k == "name" && r.peek() == com.google.gson.stream.JsonToken.STRING) name = r.nextString()
                            else r.skipValue()
                        }
                        r.endObject()
                    } else r.skipValue()
                }
                r.endObject()
                return name to null
            }
        }
    }

    // Streaming: read packages -> compute best variant per pkg; read apps -> emit app with best
    private suspend fun parseIndexStreaming(resp: HttpResponse, base: String, repoName: String, onApp: (FDroidApp) -> Unit) {
        val bestByPackage = HashMap<String, ApkVariant>(64_000)
        val inputStream = resp.bodyAsChannel().toInputStream()
        inputStream.use { isr ->
            JsonReader(InputStreamReader(isr, Charsets.UTF_8)).use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "packages" -> parsePackagesObject(r, base, bestByPackage)
                        "apps" -> parseAppsArray(r, base, repoName, bestByPackage, onApp)
                        else -> r.skipValue()
                    }
                }
                r.endObject()
            }
        }
    }

    private fun parsePackagesObject(r: JsonReader, base: String, bestByPackage: MutableMap<String, ApkVariant>) {
        r.beginObject()
        while (r.hasNext()) {
            val pkg = r.nextName()
            if (r.peek() == com.google.gson.stream.JsonToken.BEGIN_ARRAY) {
                var best: ApkVariant? = null
                r.beginArray()
                while (r.hasNext()) {
                    val v = parseVersionObject(r, base)
                    best = pickBetter(best, v)
                }
                r.endArray()
                if (best != null) bestByPackage[pkg] = best
            } else r.skipValue()
        }
        r.endObject()
    }

    private fun parseVersionObject(r: JsonReader, base: String): ApkVariant {
        var url = ""
        var size = 0L
        var sha = ""
        var versionCode = 0L
        var versionName = "1.0"
        var minSdk = 1
        var maxSdk = Int.MAX_VALUE
        var abis: List<String> = emptyList()
        var densities: List<String> = emptyList()

        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "file" -> {
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "name" -> {
                                val name = safeString(r)
                                url = if (name.startsWith("http")) name else "$base/$name"
                            }
                            "size" -> size = safeLong(r)
                            "sha256" -> sha = safeString(r)
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                }
                "manifest" -> {
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "minSdkVersion" -> minSdk = safeInt(r, 1)
                            "maxSdkVersion" -> maxSdk = safeInt(r, Int.MAX_VALUE)
                            "nativecode" -> abis = readStringArray(r)
                            "supportsScreens" -> densities = readStringArray(r)
                            "versionCode" -> versionCode = safeLong(r)
                            "versionName" -> versionName = safeString(r)
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                }
                "versionCode" -> versionCode = safeLong(r)
                "versionName" -> versionName = safeString(r)
                else -> r.skipValue()
            }
        }
        r.endObject()
        return ApkVariant(url, size, sha, abis, minSdk, maxSdk, densities, versionCode, versionName)
    }

    private fun parseAppsArray(
        r: JsonReader,
        base: String,
        repoName: String,
        bestByPackage: Map<String, ApkVariant>,
        onApp: (FDroidApp) -> Unit
    ) {
        r.beginArray()
        while (r.hasNext()) {
            parseSingleAppObject(r, base, repoName, bestByPackage)?.let(onApp)
        }
        r.endArray()
    }

    private fun parseSingleAppObject(
        r: JsonReader,
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

        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "packageName" -> pkg = safeString(r)
                "name" -> name = readLocalizedString(r)
                "summary" -> summary = readLocalizedString(r)
                "description" -> desc = readLocalizedString(r)
                "icon" -> {
                    val ic = safeString(r)
                    iconUrl = when {
                        ic.startsWith("http") -> ic
                        ic.isNotBlank() -> "$base/icons-640/$ic"
                        else -> iconUrl
                    }
                }
                "license" -> license = safeString(r, "Unknown")
                "categories" -> category = readFirstStringFromArray(r)?.let { normalizeCategory(it) } ?: "Other"
                "authorName" -> author = safeString(r, "Unknown")
                "webSite" -> website = safeString(r)
                "sourceCode" -> source = safeString(r)
                "added" -> added = safeLong(r)
                "lastUpdated" -> updated = safeLong(r)
                "screenshots" -> screenshots = readScreenshotsAny(r, base)
                else -> r.skipValue()
            }
        }
        r.endObject()

        val v = bestByPackage[pkg] ?: return null
        if (pkg.isBlank() || v.url.isBlank()) return null

        return FDroidApp(
            packageName = pkg,
            name = if (name.isNotBlank()) name else pkg,
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
            antiFeatures = emptyList(),
            downloads = 0L,
            isInstalled = false,
            repository = repoName,
            sha256 = v.sha256
        )
    }

    // Util: safer reading
    private fun safeString(r: JsonReader, def: String = ""): String = when (r.peek()) {
        com.google.gson.stream.JsonToken.STRING -> r.nextString()
        com.google.gson.stream.JsonToken.NULL -> { r.nextNull(); def }
        else -> { r.skipValue(); def }
    }
    private fun safeLong(r: JsonReader, def: Long = 0L): Long = when (r.peek()) {
        com.google.gson.stream.JsonToken.NUMBER -> try { r.nextLong() } catch (_: Exception) { r.nextString().toLongOrNull() ?: def }
        com.google.gson.stream.JsonToken.STRING -> r.nextString().toLongOrNull() ?: def
        com.google.gson.stream.JsonToken.NULL -> { r.nextNull(); def }
        else -> { r.skipValue(); def }
    }
    private fun safeInt(r: JsonReader, def: Int = 0): Int = when (r.peek()) {
        com.google.gson.stream.JsonToken.NUMBER -> try { r.nextInt() } catch (_: Exception) { r.nextString().toIntOrNull() ?: def }
        com.google.gson.stream.JsonToken.STRING -> r.nextString().toIntOrNull() ?: def
        com.google.gson.stream.JsonToken.NULL -> { r.nextNull(); def }
        else -> { r.skipValue(); def }
    }
    private fun readStringArray(r: JsonReader): List<String> {
        if (r.peek() != com.google.gson.stream.JsonToken.BEGIN_ARRAY) { r.skipValue(); return emptyList() }
        val out = ArrayList<String>()
        r.beginArray()
        while (r.hasNext()) {
            if (r.peek() == com.google.gson.stream.JsonToken.STRING) out += r.nextString() else r.skipValue()
        }
        r.endArray()
        return out
    }
    private fun readFirstStringFromArray(r: JsonReader): String? {
        if (r.peek() != com.google.gson.stream.JsonToken.BEGIN_ARRAY) { r.skipValue(); return null }
        var res: String? = null
        r.beginArray()
        if (r.hasNext() && r.peek() == com.google.gson.stream.JsonToken.STRING) res = r.nextString() else if (r.hasNext()) r.skipValue()
        while (r.hasNext()) r.skipValue()
        r.endArray()
        return res
    }
    private fun readScreenshotsAny(r: JsonReader, base: String): List<String> {
        return when (r.peek()) {
            com.google.gson.stream.JsonToken.BEGIN_ARRAY -> {
                val list = ArrayList<String>()
                r.beginArray()
                while (r.hasNext()) if (r.peek() == com.google.gson.stream.JsonToken.STRING) {
                    val s = r.nextString()
                    list += if (s.startsWith("http")) s else "$base/$s"
                } else r.skipValue()
                r.endArray()
                list
            }
            com.google.gson.stream.JsonToken.BEGIN_OBJECT -> {
                var out: List<String> = emptyList()
                r.beginObject()
                while (r.hasNext()) {
                    val k = r.nextName()
                    if (r.peek() == com.google.gson.stream.JsonToken.BEGIN_ARRAY && (k == "en" || k == "en-US" || k == "en_US" || k == "en_GB")) {
                        out = readScreenshotsAny(r, base)
                    } else r.skipValue()
                }
                r.endObject()
                out
            }
            else -> { r.skipValue(); emptyList() }
        }
    }
    private fun readLocalizedString(r: JsonReader): String {
        return when (r.peek()) {
            com.google.gson.stream.JsonToken.STRING -> r.nextString()
            com.google.gson.stream.JsonToken.BEGIN_OBJECT -> {
                var res = ""
                r.beginObject()
                while (r.hasNext()) {
                    val name = r.nextName()
                    if (r.peek() == com.google.gson.stream.JsonToken.STRING &&
                        (name == "en" || name == "en-US" || name == "en_US" || name == "en_GB")) {
                        res = r.nextString()
                    } else r.skipValue()
                }
                r.endObject()
                res
            }
            else -> { r.skipValue(); "" }
        }
    }
    private fun pickBetter(a: ApkVariant?, b: ApkVariant): ApkVariant {
        if (a == null) return b
        return if (b.versionCode > a.versionCode) b else if (b.versionCode == a.versionCode && b.size > a.size) b else a
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
}