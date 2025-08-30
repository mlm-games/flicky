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
        force: Boolean = false
    ): Pair<Int, List<Pair<String, String>>> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            Log.d(TAG, "Starting sync (force=$force)")
            val enabledRepos = settings.repositoriesFlow.first().filter { it.enabled }
            if (enabledRepos.isEmpty()) {
                Log.e(TAG, "No enabled repositories")
                val errorMessage = "No enabled repositories"
                updateState { it.copy(active = false, progress = 0f, message = errorMessage) }
                throw IllegalStateException(errorMessage)
            }

            val preferredRepoIdx = settings.settingsFlow.first().preferredRepo
            val preferred = PreferredRepo.fromIndex(preferredRepoIdx)

            fun matchesPreferred(name: String, url: String): Boolean = when (preferred) {
                PreferredRepo.FDroid      -> name.equals("F-Droid", true) || url.contains("f-droid", true)
                PreferredRepo.IzzyOnDroid -> name.contains("izzy", true) || url.contains("izzy", true)
                else -> false
            }

            // Process preferred repos last so they deterministically win on duplicate packages
            val (preferredList, others) = enabledRepos.partition { matchesPreferred(it.name, it.url) }
            val repos = others + preferredList

            if (force) {
                headersStore.clear()
                dao.clear()
            }

            cancelRequested = false
            updateState { SyncState(active = true, total = repos.size) }

            var totalApps = 0
            var anySuccess = false
            val repoErrors = mutableListOf<Pair<String, String>>()

            repos.forEachIndexed { index, repo ->
                if (cancelRequested || !kotlin.coroutines.coroutineContext.isActive) return@withLock totalApps to repoErrors

                val buffer = mutableListOf<FDroidApp>() // accumulate in memory; write once per repo

                try {
                    updateState {
                        it.copy(
                            repoName = repo.name,
                            current = index,
                            total = repos.size,
                            progress = index.toFloat() / repos.size
                        )
                    }

                    val prevHeader = headersStore.get(repo.url)
                    val differential = settings.settingsFlow.first().differentialSync
                    val result = api.fetchWithCache(
                        repo = repo,
                        previous = FDroidApi.RepoHeaders(prevHeader.etag, prevHeader.lastModified),
                        force = force,
                        enableDifferential = differential,
                        includeIncompatible = true
                    ) { app ->
                        buffer.add(app)
                    }

                    when {
                        result == null -> {
                            val errorMsg = "Network or parse error"
                            repoErrors.add(repo.name to errorMsg)
                        }
                        result.modified || force -> {
                            // Replace this repo's rows atomically
                            AppGraph.db.withTransaction {
                                dao.deleteByRepositoryUrl(repo.url)
                                if (buffer.isNotEmpty()) {
                                    dao.upsertAll(buffer)
                                    totalApps += buffer.size
                                }
                            }
                            anySuccess = true
                            result.headers?.let { headersStore.put(repo.url, RepoHeader(it.etag, it.lastModified)) }
                        }
                        else -> {
                            anySuccess = true
                        }
                    }
                } catch (e: Exception) {
                    val errorMsg = e.message ?: "Unknown error"
                    repoErrors.add(repo.name to errorMsg)
                } finally {
                    updateState {
                        val done = index + 1
                        it.copy(
                            current = done,
                            progress = done.toFloat() / repos.size
                        )
                    }
                }
            }

            if (anySuccess) {
                settings.setLastSync(System.currentTimeMillis())
            }

            updateState { it.copy(active = false, progress = 1f) }
            totalApps to repoErrors
        }
    }
}