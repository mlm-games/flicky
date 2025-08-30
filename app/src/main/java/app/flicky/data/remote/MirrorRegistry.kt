package app.flicky.data.remote

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object MirrorRegistry {

    private data class Mirrors(
        val canonicalBase: String,
        val https: List<String>,
        val onion: List<String>,
        val index: AtomicInteger = AtomicInteger(0)
    )

    private val map = ConcurrentHashMap<String, Mirrors>()

    private fun norm(url: String) = url.trim().trimEnd('/')

    fun register(canonicalBase: String, urls: List<String>) {
        val base = norm(canonicalBase)
        if (base.isBlank()) return
        // Normalize and split into https and onion/http
        val normalized = urls.mapNotNull { it?.trim() }.map { norm(it) }.distinct()
        val https = normalized.filter { it.startsWith("https://", ignoreCase = true) }
        val onion = normalized.filter { it.contains(".onion") || it.startsWith("http://", ignoreCase = true) }
        map[base] = Mirrors(base, https = if (https.isNotEmpty()) https else listOf(base), onion = onion)
    }

    fun hasMirrors(base: String): Boolean {
        val m = map[norm(base)] ?: return false
        return m.https.isNotEmpty() || m.onion.isNotEmpty()
    }

    fun pick(base: String, includeOnion: Boolean): String {
        val m = map[norm(base)] ?: return norm(base)
        val list = if (includeOnion && m.onion.isNotEmpty()) m.https + m.onion else m.https
        val size = list.size
        if (size == 0) return m.canonicalBase
        val i = (m.index.getAndIncrement() % size + size) % size
        return list[i]
    }

    fun candidates(base: String, includeOnion: Boolean): List<String> {
        val m = map[norm(base)] ?: return listOf(norm(base))
        val list = if (includeOnion && m.onion.isNotEmpty()) m.https + m.onion else m.https
        if (list.isEmpty()) return listOf(m.canonicalBase)
        // rotate starting point
        val i = (m.index.getAndIncrement() % list.size + list.size) % list.size
        return (list.subList(i, list.size) + list.subList(0, i))
    }
}