package app.flicky.data.remote

import android.content.Context
import android.os.Build
import android.util.Log
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.RepositoryInfo
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

class FDroidApi(context: Context) {
    companion object {
        private const val TAG = "FDroidApi"
        private const val BATCH_SIZE = 50 // for TV memory constraints
    }

    private val client = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000L
            connectTimeoutMillis = 30_000L
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
        }
        defaultRequest {
            header("User-Agent", "Flicky/1.1 TV")
            header("Accept", "application/json")
        }
    }

    data class RepoHeaders(val etag: String?, val lastModified: String?)

    suspend fun fetchWithCache(
        repo: RepositoryInfo,
        previous: RepoHeaders,
        force: Boolean = false,
        onApp: suspend (FDroidApp) -> Unit
    ): RepoHeaders? = withContext(Dispatchers.IO) {
        val baseUrl = repo.url.trimEnd('/')

        try {
            // Try index-v2.json directly (most repos use this now)
            val resp = client.get("$baseUrl/index-v2.json") {
                if (!force) {
                    previous.etag?.let { header(HttpHeaders.IfNoneMatch, it) }
                    previous.lastModified?.let { header(HttpHeaders.IfModifiedSince, it) }
                }
            }

            if (resp.status.value == 304) {
                Log.d(TAG, "Repository ${repo.name} has not changed (304)")
                return@withContext previous
            }

            if (resp.status.isSuccess()) {
                Log.d(TAG, "Parsing index for ${repo.name}")
                parseIndexV2(resp, baseUrl, repo.name, onApp)

                val etag = resp.headers[HttpHeaders.ETag]
                val lastMod = resp.headers[HttpHeaders.LastModified]
                return@withContext RepoHeaders(etag, lastMod)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching ${repo.name}: ${e.message}", e)
        }

        null
    }

    private suspend fun parseIndexV2(
        resp: HttpResponse,
        baseUrl: String,
        repoName: String,
        onApp: suspend (FDroidApp) -> Unit
    ) = withContext(Dispatchers.IO) {
        val batch = mutableListOf<FDroidApp>()
        var totalApps = 0

        JsonReader(InputStreamReader(resp.bodyAsChannel().toInputStream())).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "packages" -> {
                        Log.d(TAG, "Found packages section")
                        reader.beginObject() // packages object
                        while (reader.hasNext()) {
                            val packageName = reader.nextName()
                            val app = parsePackage(reader, packageName, baseUrl, repoName)
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

        // Emit remaining apps
        batch.forEach { onApp(it) }
        Log.d(TAG, "Parsed $totalApps apps from $repoName")
    }

    private fun parsePackage(
        reader: JsonReader,
        packageName: String,
        baseUrl: String,
        repoName: String
    ): FDroidApp? {
        var metadata: Metadata? = null
        val versions = mutableListOf<Version>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "metadata" -> metadata = parseMetadata(reader, baseUrl)
                "versions" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        reader.nextName() // version hash
                        versions.add(parseVersion(reader, baseUrl))
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val meta = metadata ?: return null
        val bestVersion = versions
            .filter { isCompatible(it) }
            .maxByOrNull { it.versionCode }
            ?: return null

        return FDroidApp(
            packageName = packageName,
            name = meta.name?.get("en-US") ?: packageName,
            summary = meta.summary?.get("en-US") ?: "",
            description = meta.description?.get("en-US") ?: "",
            iconUrl = meta.icon?.let { "$baseUrl/${it.get("en-US")?.name ?: "icons/$packageName.png"}" }
                ?: "$baseUrl/icons/$packageName.png",
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
            screenshots = meta.screenshots ?: emptyList(),
            antiFeatures = meta.antiFeatures,
            repository = repoName,
            sha256 = bestVersion.sha256
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
        val nativecode: List<String> = emptyList()
    )

    private fun parseMetadata(reader: JsonReader, baseUrl: String): Metadata {
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
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return Metadata(
            name, summary, description, icon, categories, antiFeatures,
            license, authorName, webSite, sourceCode, added, lastUpdated
        )
    }

    private fun parseVersion(reader: JsonReader, baseUrl: String): Version {
        var versionCode = 0
        var versionName = "1.0"
        var file = ""
        var size = 0L
        var sha256 = ""
        var minSdk = 1
        var targetSdk = 1
        var nativecode = emptyList<String>()

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
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return Version(versionCode, versionName, file, size, sha256, minSdk, targetSdk, nativecode)
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
}