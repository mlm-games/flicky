package app.flicky.data.repository

import android.util.Log
import androidx.room.withTransaction
import app.flicky.AppGraph
import app.flicky.data.local.AppDao
import app.flicky.data.model.FDroidApp
import app.flicky.data.remote.FDroidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RepositorySyncManager(
    private val api: FDroidApi,
    private val dao: AppDao,
    private val settings: SettingsRepository,
    private val headersStore: RepoHeadersStore
) {
    private val syncMutex = Mutex()

    suspend fun syncAll(
        force: Boolean = false,
        onProgress: ((current: Int, total: Int, repoName: String) -> Unit)? = null,
        onRepoError: ((repoName: String, message: String) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val repos = settings.repositoriesFlow.first().filter { it.enabled }
            if (repos.isEmpty()) throw IllegalStateException("No enabled repositories")

            val total = repos.size
            var totalApps = 0
            val allApps = mutableListOf<FDroidApp>()

            repos.forEachIndexed { index, repo ->
                onProgress?.invoke(index, total, repo.name)

                try {
                    val prev = if (force) RepoHeader(null, null) else headersStore.get(repo.url)
                    val batch = mutableListOf<FDroidApp>()

                    val headers = api.fetchWithCache(
                        repo = repo,
                        previous = FDroidApi.RepoHeaders(prev.etag, prev.lastModified),
                        force = force
                    ) { app ->
                        batch.add(app)
                        // Smaller batch size to reduce memory pressure
                        if (batch.size >= 100) {
                            allApps.addAll(batch)
                            totalApps += batch.size
                            batch.clear()
                        }
                    }

                    // Flush remaining
                    if (batch.isNotEmpty()) {
                        allApps.addAll(batch)
                        totalApps += batch.size
                    }

                    if (headers != null) {
                        headersStore.put(repo.url, RepoHeader(headers.etag, headers.lastModified))
                    }

                } catch (e: Exception) {
                    Log.e("Sync", "Error syncing ${repo.name}", e)
                    onRepoError?.invoke(repo.name, e.message ?: "Unknown error")
                }

                onProgress?.invoke(index + 1, total, repo.name)
            }

            // Insert all apps in a single transaction
            if (allApps.isNotEmpty()) {
                try {
                    AppGraph.db.withTransaction {
                        if (force) {
                            dao.clear() // Clear old data if force sync
                        }
                        // Insert in smaller chunks to avoid transaction size limits
                        allApps.chunked(500).forEach { chunk ->
                            dao.upsertAll(chunk)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Sync", "Database transaction failed", e)
                    throw e
                }
            }

            settings.setLastSync(System.currentTimeMillis())
            Log.d("Sync", "Sync complete: $totalApps apps")
            totalApps
        }
    }
}