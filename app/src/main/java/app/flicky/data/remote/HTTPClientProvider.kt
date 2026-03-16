package app.flicky.data.remote

import app.flicky.data.local.RepoConfig
import app.flicky.data.local.RepoConfigDao
import app.flicky.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.net.Authenticator
import java.net.InetAddress
import java.net.PasswordAuthentication
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

interface HttpClientProvider {
    suspend fun clientFor(baseUrl: String): OkHttpClient
    fun clientForSync(baseUrl: String): OkHttpClient
}

class DbHttpClientProvider(
    private val repoConfigDao: RepoConfigDao,
    private val settingsRepository: SettingsRepository,
) : HttpClientProvider {

    private data class CacheKey(
        val base: String,
        val trustMode: String,
        val pins: String,
        val caPem: String,
        val proxyKey: String,
    )

    private val cache = ConcurrentHashMap<CacheKey, OkHttpClient>()
    private val baseClients = ConcurrentHashMap<String, OkHttpClient>()

    private val jvmProxyAuthenticator = object : Authenticator() {
        @Volatile
        var current: ProxyConfig? = null

        override fun getPasswordAuthentication(): PasswordAuthentication? {
            val cfg = current ?: return null
            if (requestorType != RequestorType.PROXY) return null
            if (!requestingHost.equals(cfg.host, ignoreCase = true)) return null
            if (requestingPort != cfg.port) return null
            val user = cfg.username ?: return null
            return PasswordAuthentication(user, (cfg.password ?: "").toCharArray())
        }
    }.also {
        Authenticator.setDefault(it)
    }

    override suspend fun clientFor(baseUrl: String): OkHttpClient = withContext(Dispatchers.IO) {
        val normalizedUrl = baseUrl.trim().trimEnd('/')
        val cfg = repoConfigDao.get(normalizedUrl) ?: RepoConfig(baseUrl = normalizedUrl)

        val settings = settingsRepository.settingsFlow.first()
        val proxyConfig = parseProxyConfig(settings)
        val proxyKey = proxyConfig?.cacheKey ?: "DIRECT"

        jvmProxyAuthenticator.current = proxyConfig

        val baseClient = baseClients.getOrPut(proxyKey) {
            buildBaseClient(proxyConfig)
        }

        val key = CacheKey(
            base = cfg.baseUrl,
            trustMode = cfg.trustMode,
            pins = cfg.pins,
            caPem = cfg.caPem,
            proxyKey = proxyKey,
        )

        cache.getOrPut(key) {
            buildClient(cfg, baseClient)
        }
    }

    override fun clientForSync(baseUrl: String): OkHttpClient = runBlocking {
        clientFor(baseUrl)
    }

    private fun buildBaseClient(proxyConfig: ProxyConfig?): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply {
                if (proxyConfig != null) {
                    proxy(proxyConfig.proxy)

                    if (proxyConfig.scheme == "http" && !proxyConfig.username.isNullOrBlank()) {
                        proxyAuthenticator { _, response ->
                            if (response.request.header("Proxy-Authorization") != null) {
                                null
                            } else {
                                response.request.newBuilder()
                                    .header(
                                        "Proxy-Authorization",
                                        Credentials.basic(
                                            proxyConfig.username,
                                            proxyConfig.password.orEmpty()
                                        )
                                    )
                                    .build()
                            }
                        }
                    }
                }
            }
            .build()
    }

    private fun buildClient(cfg: RepoConfig, baseClient: OkHttpClient): OkHttpClient {
        val url = cfg.baseUrl.toHttpUrlOrNull()
            ?: return baseClient

        return when (cfg.trustMode.lowercase()) {
            "httpsonly" -> {
                if (!url.isHttps) {
                    throw TrustPolicyException("Repo ${cfg.baseUrl} requires HTTPS (trustMode=HttpsOnly)")
                }
                baseClient
            }

            "insecurehttp" -> {
                if (url.isHttps) {
                    baseClient
                } else {
                    if (!isPrivateOrLocalHost(url.host)) {
                        throw TrustPolicyException(
                            "Insecure HTTP is only for localhost/LAN repos: ${cfg.baseUrl}"
                        )
                    }
                    baseClient
                }
            }

            "pinned" -> {
                if (!url.isHttps) {
                    throw TrustPolicyException("Pinned mode requires HTTPS: ${cfg.baseUrl}")
                }
                val host = url.host
                val pins = parsePins(cfg.pins)
                if (pins.isEmpty()) baseClient
                else {
                    val pinner = CertificatePinner.Builder().apply {
                        pins.forEach { pin ->
                            if (isValidPin(pin)) {
                                add(host, pin)
                            }
                        }
                    }.build()

                    baseClient.newBuilder()
                        .certificatePinner(pinner)
                        .build()
                }
            }

            "customca" -> {
                if (!url.isHttps) {
                    throw TrustPolicyException("CustomCA mode requires HTTPS: ${cfg.baseUrl}")
                }
                val trust = buildTrustFromPem(cfg.caPem)
                baseClient.newBuilder()
                    .sslSocketFactory(trust.first, trust.second)
                    .build()
            }

            else -> baseClient
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