package app.flicky.data.repository

import android.util.Log
import androidx.room.withTransaction
import app.flicky.data.local.AppDao
import app.flicky.data.local.AppDatabase
import app.flicky.data.local.AppVariant
import app.flicky.data.model.FDroidApp
import app.flicky.data.remote.FDroidApi
import app.flicky.data.remote.MirrorRegistry
import app.flicky.helper.DebugLog
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
    private val headersStore: RepoHeadersStore,
    private val db: AppDatabase
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

            val (preferredList, others) = enabledRepos.partition { matchesPreferred(it.name, it.url) }
            val repos = others + preferredList // preferred last => deterministic overwrite

            if (force) {
                headersStore.clear()
                dao.clear()
                dao.clearVariants()
                db.repositoryDao().clearAll()
                runCatching {
                    val bases = settings.repositoriesFlow.first().map { it.url }
                    bases.forEach { MirrorRegistry.clear(it) }
                }
            }

            // rm disabled repos
            val allNow = settings.repositoriesFlow.first()
            val disabled = allNow.filter { !it.enabled }
            db.withTransaction {
                disabled.forEach { r ->
                    dao.deleteByRepositoryUrl(r.url)
                    dao.deleteVariantsByRepositoryUrl(r.url)
                    MirrorRegistry.clear(r.url)
                }
            }

            cancelRequested = false
            updateState { SyncState(active = true, total = repos.size) }

            var totalApps = 0
            var anySuccess = false
            val repoErrors = mutableListOf<Pair<String, String>>()

            val s = settings.settingsFlow.first()
            val differential = s.differentialSync
            val useEntry = s.useEntryJson
            val showDebug = s.showDebugInfo

            repos.forEachIndexed { index, repo ->
                if (cancelRequested || !kotlin.coroutines.coroutineContext.isActive) return@withLock totalApps to repoErrors

                val apps = mutableListOf<FDroidApp>()
                val variants = mutableListOf<AppVariant>()

                try {
                    updateState {
                        it.copy(
                            repoName = repo.name,
                            current = index,
                            total = repos.size,
                            progress = index.toFloat() / repos.size
                        )
                    }

                    if (showDebug) DebugLog.log(TAG, "Fetching ${repo.name} (${repo.url}) (entry.json=${useEntry}, diff=${differential})")

                    val prevHeader = headersStore.get(repo.url)
                    val result = api.fetchWithCache(
                        repo = repo,
                        previous = FDroidApi.RepoHeaders(prevHeader.etag, prevHeader.lastModified),
                        force = force,
                        enableDifferential = differential,
                        includeIncompatible = true,
                        onApp = { apps.add(it) },
                        onVariant = { variants.add(it) }
                    )

                    when {
                        result == null -> {
                            val errorMsg = "Network or parse error"
                            repoErrors.add(repo.name to errorMsg)
                            if (showDebug) DebugLog.log(TAG, "${repo.name} -> $errorMsg")
                        }
                        result.modified || force -> {
                            db.withTransaction {
                                dao.deleteByRepositoryUrl(repo.url)
                                dao.deleteVariantsByRepositoryUrl(repo.url)
                                if (apps.isNotEmpty()) {
                                    dao.upsertAll(apps)
                                    totalApps += apps.size
                                }
                                if (variants.isNotEmpty()) {
                                    dao.upsertVariants(variants)
                                }
                            }
                            anySuccess = true
                            result.headers?.let { headersStore.put(repo.url, RepoHeader(it.etag, it.lastModified)) }
                            if (showDebug) DebugLog.log(TAG, "${repo.name} updated: ${apps.size} apps, ${variants.size} variants")
                        }
                        else -> {
                            anySuccess = true
                            if (showDebug) DebugLog.log(TAG, "${repo.name} not modified")
                        }
                    }
                } catch (e: Exception) {
                    val errorMsg = e.message ?: "Unknown error"
                    repoErrors.add(repo.name to errorMsg)
                    if (showDebug) DebugLog.log(TAG, "${repo.name} -> error: $errorMsg")
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