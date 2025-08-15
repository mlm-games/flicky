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
    companion object {
        private const val TAG = "RepositorySyncManager"
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

            val total = repos.size
            var totalApps = 0
            val allApps = mutableListOf<FDroidApp>()
            var hasAnySuccess = false

            repos.forEachIndexed { index, repo ->
                Log.d(TAG, "Syncing repository ${index + 1}/$total: ${repo.name}")
                onProgress?.invoke(index, total, repo.name)

                try {
                    val prev = if (force) {
                        Log.d(TAG, "Force sync - ignoring cache headers")
                        RepoHeader(null, null)
                    } else {
                        headersStore.get(repo.url)
                    }

                    val repoApps = mutableListOf<FDroidApp>()

                    val headers = api.fetchWithCache(
                        repo = repo,
                        previous = FDroidApi.RepoHeaders(prev.etag, prev.lastModified),
                        force = force
                    ) { app ->
                        repoApps.add(app)
                    }

                    Log.d(TAG, "Repository ${repo.name} returned ${repoApps.size} apps")

                    if (repoApps.isNotEmpty()) {
                        allApps.addAll(repoApps)
                        totalApps += repoApps.size
                        hasAnySuccess = true
                    }

                    if (headers != null) {
                        headersStore.put(repo.url, RepoHeader(headers.etag, headers.lastModified))
                        Log.d(TAG, "Updated cache headers for ${repo.name}")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing ${repo.name}", e)
                    onRepoError?.invoke(repo.name, e.message ?: "Unknown error")
                    // Continue with next repository instead of failing entirely
                }

                onProgress?.invoke(index + 1, total, repo.name)
            }

            // Only update database if we have apps
            if (allApps.isNotEmpty()) {
                Log.d(TAG, "Updating database with $totalApps apps")
                try {
                    AppGraph.db.withTransaction {
                        if (force && hasAnySuccess) {
                            Log.d(TAG, "Force sync - clearing old data")
                            dao.clear()
                        }
                        // Insert in chunks to avoid transaction size limits
                        allApps.chunked(500).forEachIndexed { chunkIndex, chunk ->
                            Log.d(TAG, "Inserting chunk ${chunkIndex + 1} with ${chunk.size} apps")
                            dao.upsertAll(chunk)
                        }
                    }
                    Log.d(TAG, "Database update complete")
                } catch (e: Exception) {
                    Log.e(TAG, "Database transaction failed", e)
                    throw e
                }
            } else if (force) {
                Log.w(TAG, "Force sync returned no apps - not clearing database")
            }

            settings.setLastSync(System.currentTimeMillis())
            Log.d(TAG, "Sync complete: $totalApps apps from ${repos.size} repositories")
            totalApps
        }
    }
}