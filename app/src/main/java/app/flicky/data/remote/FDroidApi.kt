package app.flicky.data.remote

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import androidx.collection.emptyLongSet
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.RepositoryInfo
import app.flicky.data.trust.RepoSignatureVerifier
import app.flicky.data.trust.HttpsOnlyVerifier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class FDroidApi(
    context: Context,
    private val verifier: RepoSignatureVerifier = HttpsOnlyVerifier()
) {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json() }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 30_000
        }
        defaultRequest { header("User-Agent", "Flicky/1.1 (Android TV)") }
    }

    private val metrics: DisplayMetrics = context.resources.displayMetrics

    data class ApkVariant(
        val url: String,
        val size: Long,
        val sha256: String = "",
        val abis: List<String> = emptyList(),
        val minSdk: Int = 1,
        val maxSdk: Int = Int.MAX_VALUE,
        val densities: List<String> = emptyList(),
        val versionCode: Long = 0
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
        previous: RepoHeaders
    ): Pair<List<FDroidApp>, RepoHeaders?> {
        val bases = if (repo.url.contains("f-droid.org")) F_DROID_MIRRORS else listOf(repo.url)
        for (base0 in bases) {
            val base = normalizeUrl(base0)
            if (!verifier.isTrustedRepoUrl(base)) continue
            try {
                // Try entry.json -> index (v2)
                val entryResp = client.get("$base/entry.json") {
                    previous.etag?.let { header(HttpHeaders.IfNoneMatch, it) }
                    previous.lastModified?.let { header(HttpHeaders.IfModifiedSince, it) }
                }
                if (entryResp.status.value == 304) {
                    return emptyList<FDroidApp>() to RepoHeaders(previous.etag, previous.lastModified)
                }
                if (entryResp.status.isSuccess()) {
                    val entry = entryResp.body<JsonObject>()
                    val indexName = entry["index"]?.jsonObject?.get("name")?.jsonPrimitive?.content
                    val indexUrl = if (indexName != null) "$base/$indexName" else "$base/index-v1.json"
                    val indexResp = client.get(indexUrl) {
                        previous.etag?.let { header(HttpHeaders.IfNoneMatch, it) }
                        previous.lastModified?.let { header(HttpHeaders.IfModifiedSince, it) }
                    }
                    if (indexResp.status.value == 304) {
                        return emptyList<FDroidApp>() to RepoHeaders(previous.etag, previous.lastModified)
                    }
                    if (indexResp.status.isSuccess()) {
                        val nextEtag = indexResp.headers[HttpHeaders.ETag]
                        val nextLastMod = indexResp.headers[HttpHeaders.LastModified]
                        val index = indexResp.body<JsonObject>()
                        val apps = parseIndex(index, repo.name, base)
                        return apps to RepoHeaders(nextEtag, nextLastMod)
                    }
                }

                // Fallback to v1 directly
                val v1Resp = client.get("$base/index-v1.json") {
                    previous.etag?.let { header(HttpHeaders.IfNoneMatch, it) }
                    previous.lastModified?.let { header(HttpHeaders.IfModifiedSince, it) }
                }
                if (v1Resp.status.value == 304) {
                    return emptyList<FDroidApp>() to RepoHeaders(previous.etag, previous.lastModified)
                }
                if (v1Resp.status.isSuccess()) {
                    val nextEtag = v1Resp.headers[HttpHeaders.ETag]
                    val nextLastMod = v1Resp.headers[HttpHeaders.LastModified]
                    val v1 = v1Resp.body<JsonObject>()
                    val apps = parseIndex(v1, repo.name, base)
                    return apps to RepoHeaders(nextEtag, nextLastMod)
                }
            } catch (_: Exception) {
                // try next mirror
            }
        }
        return emptyList<FDroidApp>() to null
    }

    private fun parseIndex(root: JsonObject, repoName: String, base: String): List<FDroidApp> {
        val result = mutableListOf<FDroidApp>()
        val packages = root["packages"]?.jsonObject
        val appsArray = root["apps"]?.jsonArray

        if (appsArray != null && packages != null) {
            for (app in appsArray) {
                val a = app.jsonObject
                val pkg = a["packageName"]?.jsonPrimitive?.content ?: continue
                val versions = packages[pkg]?.jsonArray ?: continue

                val parsedVariants = versions.mapNotNull { v ->
                    parseVariant(v.jsonObject, base)
                }
                val best = selectBestVariant(parsedVariants)

                val icon = a["icon"]?.jsonPrimitive?.contentOrNull ?: ""
                val iconUrl = when {
                    icon.startsWith("http") -> icon
                    icon.isNotBlank() -> "$base/icons-640/$icon"
                    else -> "https://f-droid.org/assets/ic_repo_app_default.png"
                }

                val latestVersionObj = versions.firstOrNull()?.jsonObject
                val latestVersionName = latestVersionObj?.get("versionName")?.jsonPrimitive?.content ?: "1.0"
                val latestVersionCode = latestVersionObj?.get("versionCode")?.jsonPrimitive?.longOrNull ?: 1L
                val category = normalizeCategory(a["categories"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content ?: "Other")

                val screenshots = parseScreenshotsV1(a["screenshots"], base).ifEmpty {
                    parseScreenshotsV2(a["screenshots"], base)
                }

                result += FDroidApp(
                    packageName = pkg,
                    name = getLocalizedString(a["name"]) ?: pkg,
                    summary = getLocalizedString(a["summary"]) ?: "",
                    description = getLocalizedString(a["description"]) ?: "",
                    iconUrl = iconUrl,
                    version = latestVersionName,
                    versionCode = latestVersionCode.toInt(),
                    size = best?.size ?: 0L,
                    apkUrl = best?.url ?: "",
                    license = (a["license"]?.jsonPrimitive?.content ?: "Unknown"),
                    category = category,
                    author = (a["authorName"]?.jsonPrimitive?.content ?: "Unknown"),
                    website = (a["webSite"]?.jsonPrimitive?.content ?: ""),
                    sourceCode = (a["sourceCode"]?.jsonPrimitive?.content ?: ""),
                    added = (a["added"]?.jsonPrimitive?.longOrNull ?: 0L),
                    lastUpdated = (a["lastUpdated"]?.jsonPrimitive?.longOrNull ?: 0L),
                    screenshots = screenshots,
                    antiFeatures = parseAntiFeatures(latestVersionObj?.get("antiFeatures")),
                    downloads = 0L,
                    isInstalled = false,
                    repository = repoName,
                    sha256 = best?.sha256 ?: ""
                )
            }
        }
        return result
    }

    private fun parseVariant(v: JsonObject, base: String): ApkVariant? {
        val file = v["file"]?.jsonObject ?: return null
        val name = file["name"]?.jsonPrimitive?.content ?: return null
        val url = if (name.startsWith("http")) name else "$base/$name"
        val size = file["size"]?.jsonPrimitive?.longOrNull ?: 0L
        val sha = file["sha256"]?.jsonPrimitive?.content ?: ""
        val manifest = v["manifest"]?.jsonObject ?: JsonObject(emptyMap())
        val native = manifest["nativecode"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        val minSdk = manifest["minSdkVersion"]?.jsonPrimitive?.intOrNull ?: 1
        val maxSdk = manifest["maxSdkVersion"]?.jsonPrimitive?.intOrNull ?: Int.MAX_VALUE
        val densities = manifest["supportsScreens"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        val vc = manifest["versionCode"]?.jsonPrimitive?.longOrNull
            ?: v["versionCode"]?.jsonPrimitive?.longOrNull
            ?: 0L
        return ApkVariant(url, size, sha, native, minSdk, maxSdk, densities, vc)
    }

    private fun getLocalizedString(value: JsonElement?): String? {
        if (value == null) return null
        return when (value) {
            is JsonPrimitive -> value.content
            is JsonObject -> {
                val keys = listOf("en-US","en","en_US","en_GB")
                for (k in keys) {
                    value[k]?.let { v ->
                        if (v is JsonPrimitive) return v.content
                        if (v is JsonObject && v["name"] is JsonPrimitive) return v["name"]!!.jsonPrimitive.content
                    }
                }
                value.values.firstOrNull()?.let {
                    if (it is JsonPrimitive) return it.content
                    if (it is JsonObject && it["name"] is JsonPrimitive) return it["name"]!!.jsonPrimitive.content
                }
                null
            }
            else -> null
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

    private fun parseAntiFeatures(value: JsonElement?): List<String> {
        return when (value) {
            is JsonArray -> value.mapNotNull { it.jsonPrimitive.contentOrNull }
            is JsonObject -> value.keys.toList()
            else -> emptyList()
        }
    }

    private fun selectBestVariant(variants: List<ApkVariant>): ApkVariant? {
        if (variants.isEmpty()) return null
        val sdk = Build.VERSION.SDK_INT
        val abis = Build.SUPPORTED_ABIS.toList()
        val densityBucket = densityQualifier(metrics)

        val compatible = variants.filter { v ->
            sdk in v.minSdk..v.maxSdk &&
                    (v.abis.isEmpty() || v.abis.any { abis.contains(it) }) &&
                    (v.densities.isEmpty() || v.densities.any { it.equals(densityBucket, true) || it.equals("nodpi", true) })
        }
        if (compatible.isEmpty()) return null
        return compatible.maxByOrNull { it.versionCode }
            ?: compatible.maxByOrNull { it.size }
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

    private fun parseScreenshotsV1(value: JsonElement?, base: String): List<String> {
        if (value !is JsonArray) return emptyList()
        return value.mapNotNull { it.jsonPrimitive.contentOrNull }
            .map { if (it.startsWith("http")) it else "$base/$it" }
    }

    private fun parseScreenshotsV2(value: JsonElement?, base: String): List<String> {
        when (value) {
            is JsonArray -> return value.mapNotNull { it.jsonPrimitive.contentOrNull }
                .map { if (it.startsWith("http")) it else "$base/$it" }
            is JsonObject -> {
                val keys = listOf("en-US","en","en_US","en_GB")
                for (k in keys) {
                    val arr = value[k] as? JsonArray
                    if (arr != null) {
                        return arr.mapNotNull { it.jsonPrimitive.contentOrNull }
                            .map { if (it.startsWith("http")) it else "$base/$it" }
                    }
                }
                val first = value.values.firstOrNull() as? JsonArray ?: return emptyList()
                return first.mapNotNull { it.jsonPrimitive.contentOrNull }
                    .map { if (it.startsWith("http")) it else "$base/$it" }
            }

            else -> {}
        }
        return emptyList()
    }
}