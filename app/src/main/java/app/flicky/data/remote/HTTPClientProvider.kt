package app.flicky.data.remote

import app.flicky.data.local.RepoConfig
import app.flicky.data.local.RepoConfigDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class TrustPolicyException(message: String) : IllegalStateException(message)

interface HttpClientProvider {
    // suspend to avoid blocking
    suspend fun clientFor(baseUrl: String): OkHttpClient

    fun clientForSync(baseUrl: String): OkHttpClient
}

/**
 * Thread-safe HTTP client provider with proper async support.
 * Builds per-repo OkHttp clients based on RepoConfig (trustMode, pins, caPem).
 */
class DbHttpClientProvider(
    private val repoConfigDao: RepoConfigDao
) : HttpClientProvider {

    private data class CacheKey(
        val base: String,
        val trustMode: String,
        val pins: String,
        val caPem: String
    )

    private val cache = ConcurrentHashMap<CacheKey, OkHttpClient>()

    // Shared default client instance
    private val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Async version - properly handles suspension
     */
    override suspend fun clientFor(baseUrl: String): OkHttpClient = withContext(Dispatchers.IO) {
        val normalizedUrl = baseUrl.trim().trimEnd('/')
        val cfg = repoConfigDao.get(normalizedUrl) ?: RepoConfig(baseUrl = normalizedUrl)
        val key = CacheKey(cfg.baseUrl, cfg.trustMode, cfg.pins, cfg.caPem)

        cache.getOrPut(key) {
            buildClient(cfg)
        }
    }

    /**
     * Synchronous version for backward compatibility - returns default client if config not cached
     */
    override fun clientForSync(baseUrl: String): OkHttpClient {
        val normalizedUrl = baseUrl.trim().trimEnd('/')
        // Check if we have a cached client for this URL
        val cachedKey = cache.keys.find { it.base == normalizedUrl }
        return cachedKey?.let { cache[it] } ?: defaultClient
    }

    private fun buildClient(cfg: RepoConfig): OkHttpClient {
        val url = cfg.baseUrl.toHttpUrlOrNull()
            ?: return defaultClient

        return when (cfg.trustMode.lowercase()) {
            "httpsonly" -> {
                if (!url.isHttps) {
                    throw TrustPolicyException("Repo ${cfg.baseUrl} requires HTTPS (trustMode=HttpsOnly)")
                }
                defaultClient
            }

            "insecurehttp" -> {
                if (url.isHttps) {
                    defaultClient
                } else {
                    if (!isPrivateOrLocalHost(url.host)) {
                        throw TrustPolicyException(
                            "Insecure HTTP is only for localhost/LAN repos: ${cfg.baseUrl}"
                        )
                    }
                    defaultClient
                }
            }

            "pinned" -> {
                if (!url.isHttps) {
                    throw TrustPolicyException("Pinned mode requires HTTPS: ${cfg.baseUrl}")
                }
                val host = url.host
                val pins = parsePins(cfg.pins)
                if (pins.isEmpty()) defaultClient
                else {
                    val pinner = CertificatePinner.Builder().apply {
                        pins.forEach { pin ->
                            // Validate pin format
                            if (isValidPin(pin)) {
                                add(host, pin)
                            }
                        }
                    }.build()

                    defaultClient.newBuilder()
                        .certificatePinner(pinner)
                        .build()
                }
            }

            "customca" -> {
                if (!url.isHttps) {
                    throw TrustPolicyException("CustomCA mode requires HTTPS: ${cfg.baseUrl}")
                }
                val trust = buildTrustFromPem(cfg.caPem)
                defaultClient.newBuilder()
                    .sslSocketFactory(trust.first, trust.second)
                    .build()
            }

            else -> defaultClient
        }
    }

    private fun isPrivateOrLocalHost(host: String): Boolean {
        val h = host.lowercase()

        if (h == "localhost" || h == "127.0.0.1" || h == "::1") return true
        if (h.endsWith(".local") || h.endsWith(".home.arpa")) return true
        if (h == "10.0.2.2" || h == "10.0.2.3" || h == "10.0.3.1" || h == "10.0.3.2") return true

        return try {
            val addr = InetAddress.getByName(host)
            addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress
        } catch (e: UnknownHostException) {
            e.printStackTrace()
            false
        }
    }

    private fun parsePins(raw: String): List<String> {
        // Accept "sha256/BASE64" or "BASE64" (we'll prefix sha256/)
        return raw.split(',', ' ', '\n', '\t')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map {
                if (it.startsWith("sha256/", true)) it else "sha256/$it"
            }
    }

    /**
     * Validate certificate pin format
     */
    private fun isValidPin(pin: String): Boolean {
        val pattern = "^sha256/[A-Za-z0-9+/]{43}=$".toRegex()
        return pattern.matches(pin)
    }

    /**
     * Builds an SSLSocketFactory + X509TrustManager from a PEM (single or multi certs).
     */
    private fun buildTrustFromPem(pem: String): Pair<SSLSocketFactory, X509TrustManager> {
        val cf = CertificateFactory.getInstance("X.509")
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)

        // Split by "-----END CERTIFICATE-----"
        val blocks = pem.split("-----END CERTIFICATE-----")
            .map { it.trim() }
            .filter { it.contains("-----BEGIN CERTIFICATE-----") }
            .map { "$it\n-----END CERTIFICATE-----\n" }

        if (blocks.isEmpty()) {
            throw IllegalArgumentException("CustomCA PEM is empty or invalid")
        }

        var idx = 0
        for (block in blocks) {
            val cert = cf.generateCertificate(block.byteInputStream())
            ks.setCertificateEntry("ca_${idx++}", cert)
        }

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(ks)
        val tms = tmf.trustManagers
        val x509 = tms.firstOrNull { it is X509TrustManager } as? X509TrustManager
            ?: throw IllegalStateException("No X509TrustManager from custom CA")

        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(x509), SecureRandom())

        return Pair(ctx.socketFactory, x509)
    }
}