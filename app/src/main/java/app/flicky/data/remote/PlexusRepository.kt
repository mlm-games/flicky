package app.flicky.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class PlexusRepository(
    private val httpClientProvider: HttpClientProvider,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CachedPlexus>()

    private data class CachedPlexus(
        val data: PlexusApp?,
        val fetchedAt: Long,
    )

    suspend fun scoresFor(packageName: String): PlexusApp? = mutex.withLock {
        val now = System.currentTimeMillis()
        cache[packageName]?.takeIf { now - it.fetchedAt < ttlMs }?.data?.let { return it }

        val result = withContext(Dispatchers.IO) { fetchScores(packageName) }
        cache[packageName] = CachedPlexus(result, now)
        result
    }

    private fun fetchScores(packageName: String): PlexusApp? {
        val url = "https://plexus.techlore.tech/api/v1/apps/$packageName?scores=true"
        val okHttp: OkHttpClient = httpClientProvider.clientForSync(url)
        val req = Request.Builder().url(url).get().header("Accept", "application/json").build()
        okHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.d("PlexusRepo", "HTTP ${resp.code} for $packageName")
                return null
            }
            val body = resp.body?.string() ?: return null
            if (body.isBlank()) return null
            val result = runCatching { json.decodeFromString<PlexusAppResponse>(body).data }
            if (result.isFailure) {
                Log.d("PlexusRepo", "Deserialization failed for $packageName: ${result.exceptionOrNull()?.message}")
            }
            return result.getOrNull()
        }
    }

    fun clearCache() {
        cache.clear()
    }

    companion object {
        private val ttlMs = 6L * 60L * 60L * 1000L
    }
}
