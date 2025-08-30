package app.flicky.network

import app.flicky.data.remote.HttpClientProvider
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Call.Factory that chooses an OkHttpClient per-host using your HttpClientProvider,
 * so Coil image requests (icons/screenshots) reuse repo trust/proxy settings.
 */
class CoilCallFactory(
    private val clients: HttpClientProvider
) : Call.Factory {

    private val cache = ConcurrentHashMap<String, OkHttpClient>()

    override fun newCall(request: Request): Call {
        val url = request.url
        val hostBase = "${url.scheme}://${url.host}"
        val client = cache.getOrPut(hostBase) {
            runCatching { clients.clientFor(hostBase) }.getOrElse {
                // fallback, build a vanilla OkHttpClient
                OkHttpClient.Builder()
                    .retryOnConnectionFailure(true)
                    .build()
            }
        }
        return client.newCall(request)
    }
}