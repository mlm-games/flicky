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
import okhttp3.Interceptor
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

    private companion object {
        private val jvmProxyAuthenticator = object : Authenticator() {
            @Volatile
            var current: ProxyConfig? = null

            override fun getPasswordAuthentication(): PasswordAuthentication? {
                val cfg = current ?: return null
                val user = cfg.username ?: return null

                return when (cfg.scheme) {
                    "socks5" -> authenticateSocksProxy(cfg, user)
                    else -> null
                }
            }

            private fun authenticateSocksProxy(
                cfg: ProxyConfig,
                user: String,
            ): PasswordAuthentication? {
                val protocol = requestingProtocol
                    ?.substringBefore('/')
                    ?.lowercase()

                if (protocol != null && protocol != "socks" && protocol != "socks5") return null
                if (requestingPort != -1 && requestingPort != cfg.port) return null
                if (!matchesSocksHost(cfg)) return null

                return PasswordAuthentication(user, (cfg.password ?: "").toCharArray())
            }

            private fun matchesSocksHost(cfg: ProxyConfig): Boolean {
                val host = requestingHost
                if (host != null && host.equals(cfg.host, ignoreCase = true)) return true

                val siteAddress = requestingSite?.hostAddress
                if (siteAddress != null) {
                    val cfgAddresses = runCatching { InetAddress.getAllByName(cfg.host) }.getOrNull()
                    if (cfgAddresses != null && cfgAddresses.any { it.hostAddress == siteAddress }) {
                        return true
                    }
                }

                return host == null && requestingSite == null
            }
        }.also {
            Authenticator.setDefault(it)
        }
    }

    private data class CacheKey(
        val base: String,
        val trustMode: String,
        val pins: String,
        val caPem: String,
        val proxyKey: String,
        val authUser: String,
        val authPassword: String,
    )

    private val cache = ConcurrentHashMap<CacheKey, OkHttpClient>()
    private val baseClients = ConcurrentHashMap<String, OkHttpClient>()

    override suspend fun clientFor(baseUrl: String): OkHttpClient = withContext(Dispatchers.IO) {
        val normalizedUrl = baseUrl.trim().trimEnd('/')

        val settings = settingsRepository.settingsFlow.first()
        val proxyConfig = try {
            parseProxyConfig(settings)
        } catch (e: ProxyConfigurationException) {
            throw e
        }
        val proxyEnabled = proxyConfig != null
        val proxyKey = proxyConfig?.cacheKey ?: "DIRECT"

        try {
            val cfg = configFor(normalizedUrl) ?: RepoConfig(baseUrl = normalizedUrl)

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
                authUser = cfg.basicAuthUser,
                authPassword = cfg.basicAuthPassword,
            )

            cache.getOrPut(key) {
                buildClient(cfg, baseClient)
            }
        } catch (e: ClientConfigurationException) {
            throw e
        } catch (e: Exception) {
            if (proxyEnabled) {
                throw ProxyConfigurationException("Failed to create proxy-aware client: ${e.message}", e)
            } else {
                throw e
            }
        }
    }

    override fun clientForSync(baseUrl: String): OkHttpClient = runBlocking {
        clientFor(baseUrl)
    }

    /**
     * Exact config for a repo base URL, otherwise a config whose base URL contains
     * the requested host (Coil asks for icons with a host-only URL).
     */
    private suspend fun configFor(normalizedUrl: String): RepoConfig? {
        repoConfigDao.get(normalizedUrl)?.let { return it }

        val target = normalizedUrl.toHttpUrlOrNull() ?: return null
        if (target.encodedPath.isNotEmpty() && target.encodedPath != "/") return null

        return runCatching {
            repoConfigDao.all().firstOrNull { cfg ->
                if (cfg.baseUrl == normalizedUrl) return@firstOrNull false
                if (cfg.basicAuthUser.isBlank() && cfg.trustMode.equals("HttpsOnly", true)) {
                    return@firstOrNull false
                }
                val url = cfg.baseUrl.toHttpUrlOrNull() ?: return@firstOrNull false
                url.host == target.host && cfg.baseUrl.startsWith(normalizedUrl)
            }
        }.getOrNull()
    }

    private fun buildBaseClient(proxyConfig: ProxyConfig?): OkHttpClient {
        val httpProxyAuthorization = proxyConfig
            ?.takeIf { it.scheme == "http" && !it.username.isNullOrBlank() }
            ?.let { Credentials.basic(it.username!!, it.password.orEmpty()) }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply {
                if (proxyConfig != null) {
                    proxy(proxyConfig.proxy)

                    if (proxyConfig.scheme == "socks5" && !proxyConfig.username.isNullOrBlank()) {
                        addInterceptor { chain ->
                            jvmProxyAuthenticator.current = proxyConfig
                            chain.proceed(chain.request())
                        }
                    }

                    if (httpProxyAuthorization != null) {
                        addInterceptor { chain ->
                            val request = chain.request()
                            val proxiedRequest =
                                if (!request.url.isHttps && request.header("Proxy-Authorization") == null) {
                                    request.newBuilder()
                                        .header("Proxy-Authorization", httpProxyAuthorization)
                                        .build()
                                } else {
                                    request
                                }

                            chain.proceed(proxiedRequest)
                        }

                        proxyAuthenticator { _, response ->
                            if (response.request.header("Proxy-Authorization") != null) {
                                null
                            } else {
                                response.request.newBuilder()
                                    .header(
                                        "Proxy-Authorization",
                                        httpProxyAuthorization
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

        val client = when (cfg.trustMode.lowercase()) {
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

        val authInterceptor = basicAuthInterceptor(cfg) ?: return client
        return client.newBuilder()
            .addInterceptor(authInterceptor)
            .build()
    }

    private fun basicAuthInterceptor(cfg: RepoConfig): Interceptor? {
        val user = cfg.basicAuthUser.trim()
        if (user.isEmpty()) return null

        val header = Credentials.basic(user, cfg.basicAuthPassword)
        val allowedHosts = buildSet {
            cfg.baseUrl.toHttpUrlOrNull()?.host?.let { add(it) }
            cfg.downloadBase.toHttpUrlOrNull()?.host?.let { add(it) }
            cfg.customMirrors.forEach { mirror -> mirror.toHttpUrlOrNull()?.host?.let { add(it) } }
        }

        return Interceptor { chain ->
            val request = chain.request()
            val host = request.url.host
            if (request.header("Authorization") != null || !isRepoHost(host, cfg, allowedHosts)) {
                chain.proceed(request)
            } else {
                chain.proceed(
                    request.newBuilder()
                        .header("Authorization", header)
                        .build()
                )
            }
        }
    }

    private fun isRepoHost(host: String, cfg: RepoConfig, allowedHosts: Set<String>): Boolean {
        if (host in allowedHosts) return true
        return MirrorRegistry.mirrorUrls(cfg.baseUrl).any { mirror ->
            mirror.toHttpUrlOrNull()?.host == host
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
        ctx.init(null, arrayOf(x509), SecureRandom())

        return Pair(ctx.socketFactory, x509)
    }
}