package app.flicky.data.repository

import android.util.Log
import androidx.room.withTransaction
import app.flicky.AppGraph
import app.flicky.data.local.AppDao
import app.flicky.data.model.FDroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RepositorySyncManager(
    private val api: app.flicky.data.remote.FDroidApi,
    private val dao: AppDao,
    private val settings: SettingsRepository,
    private val headersStore: RepoHeadersStore
) {
    companion object {
        private const val TAG = "RepositorySyncManager"
        private const val DB_CHUNK = 500 // size per insert chunk to keep memory low
    }

    private val syncMutex = Mutex()

    suspend fun syncAll(
        force: Boolean = false,
        onProgress: ((current: Int, total: Int, repoName: String) -> Unit)? = null,
        onRepoError: ((repoName: String, message: String) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            Log.d(TAG, "Starting sync (force=$force)")

            val repos = settings.repositoriesFlow.first().filter { it.enabled }
            if (repos.isEmpty()) {
                Log.e(TAG, "No enabled repositories")
                throw IllegalStateException("No enabled repositories")
            }

            Log.d(TAG, "Found ${repos.size} enabled repositories")

            val totalRepos = repos.size
            var totalApps = 0
            var didAnySuccess = false
            var didClear = false // clear DB once before first successful insert if force==true

            // Helper to insert a chunk, clearing once if needed
            suspend fun insertChunk(chunk: MutableList<FDroidApp>) {
                if (chunk.isEmpty()) return
                if (force && !didClear) {
                    // Clear once, then insert first chunk in a single transaction
                    AppGraph.db.withTransaction {
                        dao.clear()
                        dao.upsertAll(chunk)
                    }
                    didClear = true
                } else {
                    dao.upsertAll(chunk)
                }
                totalApps += chunk.size
                chunk.clear()
            }

            repos.forEachIndexed { index, repo ->
                Log.d(TAG, "Syncing repository ${index + 1}/$totalRepos: ${repo.name}")
                onProgress?.invoke(index, totalRepos, repo.name)

                try {
                    val prevHeader = if (force) {
                        RepoHeader(null, null)
                    } else {
                        headersStore.get(repo.url)
                    }

                    val buffer = mutableListOf<FDroidApp>()
                    var repoCount = 0

                    val headers = api.fetchWithCache(
                        repo = repo,
                        previous = app.flicky.data.remote.FDroidApi.RepoHeaders(prevHeader.etag, prevHeader.lastModified),
                        force = force
                    ) { app ->
                        // Stream into DB in small chunks; do not keep whole repo in memory
                        buffer.add(app)
                        repoCount++
                        if (buffer.size >= DB_CHUNK) {
                            insertChunk(buffer)
                            didAnySuccess = true
                        }
                    }

                    // Flush remainder
                    if (buffer.isNotEmpty()) {
                        insertChunk(buffer)
                        didAnySuccess = true
                    }

                    Log.d(TAG, "Repository ${repo.name} processed $repoCount apps")

                    if (headers != null) {
                        headersStore.put(repo.url, RepoHeader(headers.etag, headers.lastModified))
                        Log.d(TAG, "Updated cache headers for ${repo.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing ${repo.name}", e)
                    onRepoError?.invoke(repo.name, e.message ?: "Unknown error")
                    // Continue with next repository
                }

                onProgress?.invoke(index + 1, totalRepos, repo.name)
            }

            // If force but nothing was inserted successfully, do not clear (we didn’t)
            settings.setLastSync(System.currentTimeMillis())
            Log.d(TAG, "Sync complete: $totalApps apps from ${repos.size} repositories (cleared=$didClear)")
            totalApps
        }
    }
}