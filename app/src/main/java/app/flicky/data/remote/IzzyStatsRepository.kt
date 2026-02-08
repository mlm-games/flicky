package app.flicky.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class IzzyDownloadStats(
    val monthlyRolling: Long? = null,
    val yearlyRolling: Long? = null,
    val fetchedAtMs: Long,
)

class IzzyStatsRepository(
    private val httpClientProvider: HttpClientProvider,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val mutex = Mutex()
    private var cacheAtMs: Long = 0L
    private var monthly: Map<String, Long> = emptyMap()
    private var yearly: Map<String, Long> = emptyMap()

    private val ttlMs = 24L * 60L * 60L * 1000L

    private val monthlyUrl =
        "https://dlstats.izzyondroid.org/iod-stats-collector/stats/basic/monthly/rolling.json"
    private val yearlyUrl =
        "https://dlstats.izzyondroid.org/iod-stats-collector/stats/basic/yearly/rolling.json"

    suspend fun statsFor(packageName: String): IzzyDownloadStats? = mutex.withLock {
        val now = System.currentTimeMillis()
        if (now - cacheAtMs > ttlMs || monthly.isEmpty() || yearly.isEmpty()) {
            val (m, y) = fetchBoth()
            monthly = m
            yearly = y
            cacheAtMs = now
        }

        val m = monthly[packageName]
        val y = yearly[packageName]
        if (m == null && y == null) return null

        IzzyDownloadStats(
            monthlyRolling = m,
            yearlyRolling = y,
            fetchedAtMs = cacheAtMs
        )
    }

    private suspend fun fetchBoth(): Pair<Map<String, Long>, Map<String, Long>> = withContext(Dispatchers.IO) {
        val m = fetchMap(monthlyUrl)
        val y = fetchMap(yearlyUrl)
        m to y
    }

    private fun fetchMap(url: String): Map<String, Long> {
        val okHttp: OkHttpClient = httpClientProvider.clientForSync(url)
        val req = Request.Builder().url(url).get().build()
        okHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyMap()
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return emptyMap()

            return runCatching { json.decodeFromString<Map<String, Long>>(body) }
                .getOrElse { emptyMap() }
        }
    }
}
