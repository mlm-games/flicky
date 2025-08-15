package app.flicky.data.repository

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RepoHeader(val etag: String? = null, val lastModified: String? = null)

@Serializable
data class RepoHeadersMap(val map: Map<String, RepoHeader> = emptyMap())

class RepoHeadersStore(private val settings: SettingsRepository) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): RepoHeadersMap {
        val raw = settings.getRepoHeadersMap()
        return runCatching { json.decodeFromString(RepoHeadersMap.serializer(), raw) }
            .getOrElse { RepoHeadersMap() }
    }

    suspend fun save(newMap: RepoHeadersMap) {
        settings.setRepoHeadersMap(json.encodeToString(RepoHeadersMap.serializer(), newMap))
    }

    suspend fun get(url: String): RepoHeader {
        val m = load()
        return m.map[url] ?: RepoHeader()
    }

    suspend fun put(url: String, header: RepoHeader) {
        val m = load().map.toMutableMap()
        m[url] = header
        save(RepoHeadersMap(m))
    }

    suspend fun remove(url: String) {
        val m = load().map.toMutableMap()
        m.remove(url)
        save(RepoHeadersMap(m))
    }

    suspend fun clear() {
        save(RepoHeadersMap())
    }
}