package app.flicky.network

import app.flicky.data.remote.HttpClientProvider
import app.flicky.data.remote.TrustPolicyException
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Call.Factory that chooses an OkHttpClient per-host using your HttpClientProvider,
 * so Coil image requests (icons/screenshots) reuse repo trust/proxy settings.
 */
class CoilCallFactory(
    private val clients: HttpClientProvider,
    private val failOnTrustErrors: Boolean = false
) : Call.Factory {

    private val cache = ConcurrentHashMap<String, OkHttpClient>()

    override fun newCall(request: Request): Call {
        val url = request.url

        val portSuffix = when (url.scheme) {
            "http" -> if (url.port != 80) ":${url.port}" else ""
            "https" -> if (url.port != 443) ":${url.port}" else ""
            else -> if (url.port > 0) ":${url.port}" else ""
        }

        val hostBase = "${url.scheme}://${url.host}$portSuffix"

        val client = cache.getOrPut(hostBase) {
            runBlocking {
                try {
                    clients.clientFor(hostBase)
                } catch (e: TrustPolicyException) {
                    throw e
                } catch (e: Exception) {
                    if (failOnTrustErrors) throw e
                    // permissive fallback
                    OkHttpClient.Builder()
                        .retryOnConnectionFailure(true)
                        .build()
                }
            }
        }
        return client.newCall(request)
    }
}