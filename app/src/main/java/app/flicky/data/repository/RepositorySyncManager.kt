package app.flicky.data.repository

import android.util.Log
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
    suspend fun syncAll(
        force: Boolean = false,
        onProgress: ((current: Int, total: Int, repoName: String) -> Unit)? = null,
        onRepoError: ((repoName: String, message: String) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val repos = settings.repositoriesFlow.first().filter { it.enabled }
        if (repos.isEmpty()) throw IllegalStateException("No enabled repositories")

        val total = repos.size
        var processed = 0
        var totalApps = 0

        repos.forEachIndexed { index, repo ->
            onProgress?.invoke(index, total, repo.name)
            try {
                val prev = if (force) RepoHeader(null, null) else headersStore.get(repo.url)
                val batch = ArrayList<FDroidApp>(1000)

                val headers = api.fetchWithCache(
                    repo = repo,
                    previous = FDroidApi.RepoHeaders(prev.etag, prev.lastModified),
                    force = force
                ) { app ->
                    batch += app
                    if (batch.size >= 500) {
                        dao.upsertAll(batch)
                        totalApps += batch.size
                        batch.clear()
                    }
                }
                // flush remaining
                if (batch.isNotEmpty()) {
                    dao.upsertAll(batch)
                    totalApps += batch.size
                    batch.clear()
                }
                if (headers != null) {
                    headersStore.put(repo.url, RepoHeader(etag = headers.etag, lastModified = headers.lastModified))
                }
                val inserted = dao.count()
                Log.d("Sync", "Repo ${repo.name}: total rows now = $inserted")
            } catch (e: Exception) {
                onRepoError?.invoke(repo.name, e.message ?: "Unknown error")
            }
            processed = index + 1
            onProgress?.invoke(processed, total, repo.name)
        }

        settings.setLastSync(System.currentTimeMillis())
        totalApps
    }
}