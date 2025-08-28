package app.flicky.data.repository

import android.util.Log
import androidx.room.withTransaction
import app.flicky.AppGraph
import app.flicky.data.local.AppDao
import app.flicky.data.model.FDroidApp
import app.flicky.data.remote.FDroidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
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
    @Volatile private var cancelRequested = false

    fun cancelCurrentSync() {
        cancelRequested = true
        api.cancelOngoing()
    }

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
            if (force) {
                AppGraph.headersStore.clear()
                dao.clear()
            }

            cancelRequested = false
            updateState { SyncState(active = true, total = repos.size, message = "Starting sync...") }

            var totalApps = 0
            var anySuccess = false

            val appSettings = settings.settingsFlow.first()
            val includeIncompatible = true // ingest all; UI filters by isCompatible
            val differential = appSettings.differentialSync

            suspend fun insertChunk(chunk: MutableList<FDroidApp>) {
                if (chunk.isEmpty()) return
                dao.upsertAll(chunk)
                totalApps += chunk.size
                chunk.clear()
            }

            repos.forEachIndexed { index, repo ->
                val buffer = mutableListOf<FDroidApp>()
                var repoCount = 0
                if (cancelRequested || !kotlin.coroutines.coroutineContext.isActive) return@withLock totalApps

                suspend fun flush() = insertChunk(buffer)

                try {
                    updateState {
                        it.copy(
                            repoName = repo.name,
                            current = index,
                            total = repos.size,
                            progress = index.toFloat() / repos.size,
                            message = "Syncing ${repo.name} (${index + 1}/${repos.size})"
                        )
                    }

                    val prevHeader = headersStore.get(repo.url)

                    val result: FDroidApi.FetchResult? = api.fetchWithCache(
                        repo = repo,
                        previous = FDroidApi.RepoHeaders(prevHeader.etag, prevHeader.lastModified),
                        force = force,
                        enableDifferential = differential,
                        includeIncompatible = includeIncompatible
                    ) { app ->
                        buffer.add(app)
                        repoCount++
                        if (buffer.size >= DB_CHUNK) {
                            dao.upsertAll(buffer)
                            totalApps += buffer.size
                            buffer.clear()
                        }
                    }

                    // If result is null => failure; if modified=false => no DB write
                    when {
                        result == null -> {
                            // Commit remainder even on failure
                            flush()
                            onRepoError?.invoke(repo.name, "Network or parse error")
                            updateState { it.copy(message = "Error: ${repo.name}: failed to fetch") }
                        }
                        !result.modified && !force -> {
                            // Not modified — skip DB writes for this repo
                            anySuccess = true
                            updateState { it.copy(message = "Unchanged: ${repo.name}") }
                        }
                        else -> {
                            // Modified or force refresh — replace this repo’s data atomically
                            AppGraph.db.withTransaction {
                                dao.deleteByRepositoryUrl(repo.url)
                                flush()
                            }
                            anySuccess = true
                            result.headers?.let { headersStore.put(repo.url, RepoHeader(it.etag, it.lastModified)) }
                        }
                    }
                } catch (e: Exception) {
                    // commit remainder even on failure
                    flush()
                    updateState { it.copy(message = "Error: ${repo.name}: ${e.message ?: "Unknown error"}") }
                    onRepoError?.invoke(repo.name, e.message ?: "Unknown error")
                } finally {
                    updateState {
                        it.copy(
                            current = index + 1,
                            progress = (index + 1).toFloat() / repos.size,
                            message = "Finished ${repo.name} (${index + 1}/${repos.size})"
                        )
                    }
                }
            }

            if (anySuccess) {
                settings.setLastSync(System.currentTimeMillis())
                updateState { it.copy(active = false, progress = 1f, message = "Sync complete: $totalApps apps") }
                Log.d(TAG, "Sync complete: $totalApps apps from ${repos.size} repositories")
            } else {
                updateState { it.copy(active = false, message = "Sync failed") }
                Log.w(TAG, "Sync finished with no successful repositories")
            }
            totalApps
        }
    }
}
