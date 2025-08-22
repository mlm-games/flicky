package app.flicky.data.repository

import android.util.Log
import androidx.room.withTransaction
import app.flicky.AppGraph
import app.flicky.data.local.AppDao
import app.flicky.data.model.FDroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    data class SyncState(
        val active: Boolean = false,
        val repoName: String = "",
        val current: Int = 0,
        val total: Int = 0,
        val progress: Float = 0f,
        val message: String = ""
    )

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private fun updateState(block: (SyncState) -> SyncState) {
        _state.value = block(_state.value)
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
                updateState { it.copy(active = false, progress = 0f, message = "No repositories enabled") }
                throw IllegalStateException("No enabled repositories")
            }

            updateState { SyncState(active = true, repoName = "", current = 0, total = repos.size, progress = 0f, message = "Starting sync...") }

            val totalRepos = repos.size
            var totalApps = 0
            var didAnySuccess = false
            var didClear = false // clear DB once before first successful insert if force==true

            suspend fun insertChunk(chunk: MutableList<FDroidApp>) {
                if (chunk.isEmpty()) return
                if (force && !didClear) {
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
                val startProgress = index.toFloat() / totalRepos.toFloat()
                updateState {
                    it.copy(
                        active = true,
                        repoName = repo.name,
                        current = index,
                        total = totalRepos,
                        progress = startProgress,
                        message = "Syncing ${repo.name} (${index}/${totalRepos})..."
                    )
                }
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
                        buffer.add(app)
                        repoCount++
                        if (buffer.size >= DB_CHUNK) {
                            insertChunk(buffer)
                            didAnySuccess = true
                        }
                    }

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
                    updateState {
                        it.copy(
                            message = "Error: ${repo.name}: ${e.message ?: "Unknown error"}"
                        )
                    }
                }

                val endProgress = (index + 1).toFloat() / totalRepos.toFloat()
                updateState {
                    it.copy(
                        current = index + 1,
                        progress = endProgress,
                        message = "Finished ${repo.name} (${index + 1}/${totalRepos})"
                    )
                }
                onProgress?.invoke(index + 1, totalRepos, repo.name)
            }

            settings.setLastSync(System.currentTimeMillis())
            updateState { it.copy(active = false, progress = 1f, message = "Sync complete: $totalApps apps") }
            Log.d(TAG, "Sync complete: $totalApps apps from ${repos.size} repositories (cleared=$didClear)")
            totalApps
        }
    }
}