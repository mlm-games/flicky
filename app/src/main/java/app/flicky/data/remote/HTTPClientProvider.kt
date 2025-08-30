package app.flicky.data.remote

import android.util.Base64
import app.flicky.data.local.RepoConfig
import app.flicky.data.local.RepoConfigDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.internal.tls.OkHostnameVerifier
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

interface HttpClientProvider {
    fun clientFor(baseUrl: String): OkHttpClient
}

/**
 * Builds per-repo OkHttp clients based on RepoConfig (trustMode, pins, caPem).
 * Caches clients by (baseUrl, config snapshot).
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
    private val defaultClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    override fun clientFor(baseUrl: String): OkHttpClient {
        val cfg = runBlocking(Dispatchers.IO) {
            repoConfigDao.get(baseUrl.trim().trimEnd('/')) ?: RepoConfig(baseUrl = baseUrl.trim().trimEnd('/'))
        }
        val key = CacheKey(cfg.baseUrl, cfg.trustMode, cfg.pins, cfg.caPem)
        return cache.getOrPut(key) { buildClient(cfg) }
    }

    private fun buildClient(cfg: RepoConfig): OkHttpClient {
        val url = cfg.baseUrl.toHttpUrlOrNull()
            ?: return defaultClient

        if (cfg.trustMode.equals("HttpsOnly", ignoreCase = true)) {
            if (!url.isHttps) {
                // Enforce https-only
                throw IllegalStateException("Repo ${cfg.baseUrl} requires HTTPS (trustMode=HttpsOnly)")
            }
            return defaultClient
        }

        if (cfg.trustMode.equals("Pinned", ignoreCase = true)) {
            if (!url.isHttps) {
                throw IllegalStateException("Pinned mode requires HTTPS: ${cfg.baseUrl}")
            }
            val host = url.host
            val pins = parsePins(cfg.pins)
            if (pins.isEmpty()) return defaultClient

            val pinner = CertificatePinner.Builder().apply {
                pins.forEach { add(host, it) }
            }.build()

            return defaultClient.newBuilder()
                .certificatePinner(pinner)
                .build()
        }

        if (cfg.trustMode.equals("CustomCA", ignoreCase = true)) {
            if (!url.isHttps) {
                throw IllegalStateException("CustomCA mode requires HTTPS: ${cfg.baseUrl}")
            }
            val trust = buildTrustFromPem(cfg.caPem)
            return defaultClient.newBuilder()
                .sslSocketFactory(trust.first, trust.second)
                .build()
        }

        return defaultClient
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