package app.flicky.data.remote

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Policy-based mirror selector with optional sticky last-good behavior.
 * Persisting lastGood is pluggable via MirrorStateStore (in-memory by default).
 */
object MirrorRegistry {

    enum class Strategy {
        StickyLastGood, // prefer last-good for this repo, then canonical, then others
        RoundRobin,     // rotate over https mirrors, then onion (if allowed)
        CanonicalFirst  // always canonical first, then others
    }

    interface MirrorStateStore {
        fun getLastGood(base: String): String?
        fun putLastGood(base: String, url: String)
        fun clear(base: String)
    }

    private class InMemoryStore : MirrorStateStore {
        private val map = ConcurrentHashMap<String, String>()
        override fun getLastGood(base: String) = map[base]
        override fun putLastGood(base: String, url: String) { map[base] = url }
        override fun clear(base: String) { map.remove(base) }
    }

    private var stateStore: MirrorStateStore = InMemoryStore()
    fun setStateStore(store: MirrorStateStore) { stateStore = store }

    private data class Mirrors(
        val canonicalBase: String,
        val https: List<String>,
        val onion: List<String>,
        val primary: String? = null,
        val rrIndex: AtomicInteger = AtomicInteger(0)
    )

    private val repos = ConcurrentHashMap<String, Mirrors>()

    private fun norm(url: String) = url.trim().trimEnd('/')

    /**
     * Register mirrors for a repository.
     * @param canonicalBase base from "repo.address" (treated as primary if [primaryUrl] is null)
     * @param urls all mirror URLs (http/https/onion)
     * @param primaryUrl optional explicit primary mirror URL from index metadata
     */
    fun register(canonicalBase: String, urls: List<String>, primaryUrl: String? = null) {
        val base = norm(canonicalBase)
        if (base.isBlank()) return

        // Normalize and uniquify
        val normalized = urls.map { it.trim() }
            .map(::norm)
            .distinct()

        val https = normalized.filter { it.startsWith("https://", ignoreCase = true) }
        val onion = normalized.filter { it.contains(".onion") || it.startsWith("http://", ignoreCase = true) }

        repos[base] = Mirrors(
            canonicalBase = base,
            https = https.ifEmpty { listOf(base) },
            onion = onion,
            primary = primaryUrl?.let(::norm)
        )
    }

    fun hasMirrors(base: String): Boolean = repos.containsKey(norm(base))

    /**
     * Compute ordered candidates based on strategy and onion preference.
     */
    fun candidates(base: String, includeOnion: Boolean, strategy: Strategy): List<String> {
        val m = repos[norm(base)] ?: return listOf(norm(base))
        // Deduplicate while preserving preference order
        fun dedup(list: List<String>) = list.asSequence().distinct().toList()

        val https = m.https
        val onion = if (includeOnion) m.onion else emptyList()
        val canonical = m.canonicalBase
        val primary = m.primary ?: canonical
        val lastGood = stateStore.getLastGood(m.canonicalBase)

        return when (strategy) {
            Strategy.StickyLastGood -> dedup(
                listOfNotNull(lastGood, primary, canonical) +
                        https + onion
            )
            Strategy.RoundRobin -> {
                val allHttps = dedup(listOf(primary, canonical) + https)
                val size = allHttps.size
                val start = if (size == 0) 0 else (m.rrIndex.getAndIncrement() % size + size) % size
                val rr = if (size == 0) emptyList() else allHttps.drop(start) + allHttps.take(start)
                dedup(rr + onion)
            }
            Strategy.CanonicalFirst -> dedup(listOf(primary, canonical) + https + onion)
        }
    }

    /**
     * Mark a mirror as healthy (successful use). Used by fetchers/downloader.
     */
    fun markHealthy(base: String, url: String) {
        val b = norm(base)
        val u = norm(url)
        val m = repos[b] ?: return
        if (u == b || m.https.contains(u) || m.onion.contains(u)) {
            stateStore.putLastGood(b, u)
        }
    }

    fun clear(base: String) {
        val b = norm(base)
        stateStore.clear(b)
    }
}