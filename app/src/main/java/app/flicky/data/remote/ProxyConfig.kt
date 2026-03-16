package app.flicky.data.remote

import app.flicky.data.repository.AppSettings
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

open class ClientConfigurationException(message: String) : IllegalStateException(message)
class ProxyConfigurationException(message: String) : ClientConfigurationException(message)
class TrustPolicyException(message: String) : ClientConfigurationException(message)

data class ProxyConfig(
    val scheme: String,
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?,
) {
    val proxyType: Proxy.Type =
        if (scheme == "http") Proxy.Type.HTTP else Proxy.Type.SOCKS

    val proxy: Proxy =
        Proxy(proxyType, InetSocketAddress(host, port))

    val cacheKey: String =
        buildString {
            append(scheme)
            append("://")
            if (username != null) {
                append(username)
                append(':')
                append(password ?: "")
                append('@')
            }
            append(host.lowercase())
            append(':')
            append(port)
        }

    val sanitizedUrl: String
        get() = buildString {
            append(scheme)
            append("://")
            if (username != null) {
                append(username)
                if (password != null) append(":***")
                append('@')
            }
            val needsBrackets = host.contains(':') && !host.startsWith("[") && !host.endsWith("]")
            append(if (needsBrackets) "[$host]" else host)
            append(':')
            append(port)
        }
}

private fun AppSettings.legacyProxyUrlOrNull(): String? {
    if (!useProxy) return null
    if (proxyHost.isBlank()) return null
    val scheme = if (proxyType == 1) "socks5" else "http"
    val port = proxyPort.coerceIn(1, 65535)
    return "$scheme://${proxyHost.trim()}:$port"
}

fun parseProxyConfig(settings: AppSettings): ProxyConfig? {
    if (!settings.useProxy) return null

    val raw = settings.proxyUrl.trim().ifBlank {
        settings.legacyProxyUrlOrNull() ?: return null
    }

    val uri = try {
        URI(raw).parseServerAuthority()
    } catch (e: Exception) {
        throw ProxyConfigurationException("Invalid proxy URL")
    }

    val scheme = uri.scheme?.lowercase()
        ?: throw ProxyConfigurationException("Proxy URL must include a scheme")

    val normalizedScheme = when (scheme) {
        "http" -> "http"
        "socks", "socks5" -> "socks5"
        else -> throw ProxyConfigurationException("Supported proxy schemes: http, socks5")
    }

    if (!uri.rawPath.isNullOrBlank() && uri.rawPath != "/") {
        throw ProxyConfigurationException("Proxy URL must not include a path")
    }
    if (uri.rawQuery != null || uri.rawFragment != null) {
        throw ProxyConfigurationException("Proxy URL must not include query or fragment")
    }

    val host = uri.host?.takeIf { it.isNotBlank() }
        ?: throw ProxyConfigurationException("Proxy URL must include a host")

    val port = uri.port.takeIf { it in 1..65535 }
        ?: throw ProxyConfigurationException("Proxy URL must include a valid port")

    val userInfo = uri.userInfo
    val username: String?
    val password: String?

    if (userInfo.isNullOrEmpty()) {
        username = null
        password = null
    } else {
        val idx = userInfo.indexOf(':')
        if (idx < 0) {
            username = userInfo
            password = ""
        } else {
            username = userInfo.substring(0, idx)
            password = userInfo.substring(idx + 1)
        }
    }

    return ProxyConfig(
        scheme = normalizedScheme,
        host = host,
        port = port,
        username = username?.takeIf { it.isNotEmpty() },
        password = password,
    )
}