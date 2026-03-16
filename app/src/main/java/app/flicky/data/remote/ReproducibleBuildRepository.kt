package app.flicky.data.remote

import android.util.Log
import app.flicky.data.model.ReproducibleBuildInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class ReproducibleBuildRepository(
    private val httpClientProvider: HttpClientProvider
) {
    companion object {
        private const val TAG = "ReproducibleBuildRepo"
        private const val RBTLOG_BASE_URL = "https://codeberg.org/IzzyOnDroid/rbtlog/raw/branch/izzy/log/logs"
        private const val CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val cache = mutableMapOf<String, Pair<ReproducibleBuildInfo, Long>>()

    private fun client(): OkHttpClient = httpClientProvider.clientForSync(RBTLOG_BASE_URL)

    suspend fun fetchReproducibleBuildInfo(packageName: String): ReproducibleBuildInfo? =
        withContext(Dispatchers.IO) {
            val cached = cache[packageName]
            if (cached != null && (System.currentTimeMillis() - cached.second) < CACHE_MAX_AGE_MS) {
                return@withContext cached.first
            }

            try {
                val url = "$RBTLOG_BASE_URL/$packageName.json"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()

                client().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.d(TAG, "No reproducible build data for $packageName: ${response.code}")
                        return@withContext null
                    }

                    val body = response.body?.string()
                    if (body.isNullOrBlank()) {
                        return@withContext null
                    }

                    val info = json.decodeFromString<ReproducibleBuildInfo>(body)
                    cache[packageName] = Pair(info, System.currentTimeMillis())
                    Log.d(TAG, "Fetched reproducible build info for $packageName")
                    return@withContext info
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch reproducible build info for $packageName: ${e.message}")
                null
            }
        }

    fun clearCache() {
        cache.clear()
    }

    fun isReproducible(packageName: String, versionCode: Int): Boolean? {
        val cached = cache[packageName]?.first ?: return null
        return cached.isReproducible(versionCode)
    }
}
