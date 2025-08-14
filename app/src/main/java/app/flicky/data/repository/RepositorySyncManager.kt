package app.flicky.data.repository

import app.flicky.data.local.AppDao
import app.flicky.data.model.FDroidApp
import app.flicky.data.remote.FDroidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class RepositorySyncManager(
    private val api: FDroidApi,
    private val dao: AppDao,
    private val settings: SettingsRepository,
    private val headersStore: RepoHeadersStore
) {
    suspend fun syncAll(force: Boolean = false): List<FDroidApp> = withContext(Dispatchers.IO) {
        val repos = settings.repositoriesFlow.first()
        val all = mutableListOf<FDroidApp>()


        for (repo in repos) {
            val prev = headersStore.get(repo.url)
            val (apps, newHeaders) = api.fetchWithCache(repo, FDroidApi.RepoHeaders(prev.etag, prev.lastModified))
            if (apps.isNotEmpty()) {
                all += apps
                if (newHeaders != null) headersStore.put(repo.url, RepoHeader(newHeaders.etag, newHeaders.lastModified))
            }
        }
        if (all.isNotEmpty()) {
            dao.clear()
            dao.upsertAll(all)
            settings.setLastSync(System.currentTimeMillis())
        }
        all
    }

}