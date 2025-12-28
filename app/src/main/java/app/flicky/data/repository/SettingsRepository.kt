package app.flicky.data.repository

import android.content.Context
import app.flicky.AppGraph
import app.flicky.data.local.AppDao
import app.flicky.data.local.RepoConfig
import app.flicky.data.local.RepoConfigDao
import app.flicky.data.local.RepositoryDao
import app.flicky.data.local.RepositoryEntity
import app.flicky.data.model.RepositoryInfo
import app.flicky.data.remote.MirrorRegistry
import io.github.mlmgames.settings.core.SettingsRepository as KmpSettingsRepository
import io.github.mlmgames.settings.core.datastore.createSettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SettingsRepository(
    context: Context,
    private val repositoryDao: RepositoryDao,
    private val repoConfigDao: RepoConfigDao,
    private val appDao: AppDao,
) {
    private val dataStore = createSettingsDataStore(context, name = "flicky.settings")
    private val repo = KmpSettingsRepository(dataStore, AppSettingsSchema)

    val settingsFlow: Flow<AppSettings> = repo.flow

    /**
     * Repositories from Room join of repositories + repo_config.
     */
    val repositoriesFlow: Flow<List<RepositoryInfo>> =
        repositoryDao.observeWithConfig()
            .map { rows ->
                val defaultNames = RepositoryInfo.defaults().associate {
                    normalizeUrl(it.url) to it.name
                }
                rows.map { r ->
                    val normalizedUrl = normalizeUrl(r.baseUrl)
                    RepositoryInfo(
                        name = r.name.ifBlank { defaultNames[normalizedUrl] ?: normalizedUrl },
                        url = normalizedUrl,
                        enabled = r.enabled,
                        signingKey = r.fingerprint,
                        rotateMirrors = r.rotateMirrors
                    )
                }
            }
            .distinctUntilChanged()

    suspend fun updateSetting(propertyName: String, value: Any) {
        repo.set(propertyName, value)
    }

    suspend fun updateSettings(update: (AppSettings) -> AppSettings) {
        repo.update(update)
    }

    // Room-backed repo operations (unchanged)
    suspend fun toggleRepository(url: String) {
        AppGraph.syncManager.cancelCurrentSync()

        val base = normalizeUrl(url)
        val existing = repoConfigDao.get(base)
        val newEnabled = !(existing?.enabled ?: true)
        repoConfigDao.upsert((existing ?: RepoConfig(baseUrl = base)).copy(enabled = newEnabled))
        if (!newEnabled) {
            appDao.deleteByRepositoryUrl(base)
            appDao.deleteVariantsByRepositoryUrl(base)
            MirrorRegistry.clear(base)
        }
    }

    suspend fun addRepository(name: String, url: String) {
        val base = normalizeUrl(url)
        repositoryDao.upsert(
            RepositoryEntity(
                baseUrl = base,
                name = name.ifBlank { base }
            )
        )
        val isFDroid = base.equals("https://f-droid.org/repo", ignoreCase = true) ||
                name.equals("F-Droid", ignoreCase = true) ||
                base.contains("f-droid.org", ignoreCase = true)

        repoConfigDao.insertIgnore(
            RepoConfig(
                baseUrl = base,
                enabled = true,
                rotateMirrors = isFDroid,
                strategy = if (isFDroid) "RoundRobin" else "StickyLastGood"
            )
        )
    }

    suspend fun deleteRepository(url: String) {
        val base = normalizeUrl(url)
        repoConfigDao.upsert((repoConfigDao.get(base) ?: RepoConfig(baseUrl = base)).copy(enabled = false))
        appDao.deleteByRepositoryUrl(base)
        appDao.deleteVariantsByRepositoryUrl(base)
        MirrorRegistry.clear(base)
    }

    suspend fun resetRepositoriesToDefaults() = withContext(Dispatchers.IO) {
        repositoryDao.clearAll()
        repoConfigDao.clearAll()
        RepositoryInfo.defaults().forEach { def ->
            val base = normalizeUrl(def.url)
            repositoryDao.upsert(
                RepositoryEntity(
                    baseUrl = base,
                    name = def.name
                )
            )
            val isFDroid = base.equals("https://f-droid.org/repo", ignoreCase = true)

            repoConfigDao.insertIgnore(
                RepoConfig(
                    baseUrl = base,
                    enabled = def.enabled,
                    rotateMirrors = isFDroid,
                    strategy = if (isFDroid) "RoundRobin" else "StickyLastGood"
                )
            )
        }
    }

    suspend fun setLastSync(millis: Long) {
        repo.set("lastSync", millis)
    }

    suspend fun setRepoHeadersMap(mapJson: String) {
        repo.set("repoHeadersJson", mapJson)
    }

    suspend fun getRepoHeadersMap(): String =
        repo.get<String>("repoHeadersJson") ?: "{}"

    suspend fun setLastQuery(q: String) {
        repo.set("lastQuery", q)
    }

    suspend fun getLastQuery(): String =
        repo.get<String>("lastQuery") ?: ""

    suspend fun clearCache() {
        repo.update {
            it.copy(
                repoHeadersJson = "{}",
                lastSync = 0L
            )
        }
    }

    fun normalizeUrl(url: String): String = url.trim().trimEnd('/')
}