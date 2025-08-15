package app.flicky.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import app.flicky.data.repository.AppRepository
import app.flicky.data.repository.RepositorySyncManager
import app.flicky.data.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BrowseUiState(
    val query: String = "",
    val sort: SortOption = SortOption.Updated,
    val apps: List<FDroidApp> = emptyList(),
    val categories: List<String> = emptyList(),
    val isSyncing: Boolean = false,
    val status: String = "",
    val progress: Float = 0f,
    val errorMessage: String? = null
)

data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)


class BrowseViewModel(
    private val repo: AppRepository,
    private val sync: RepositorySyncManager,
    private val settings: SettingsRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _sort = MutableStateFlow(SortOption.Updated)
    private val _isSyncing = MutableStateFlow(false)
    private val _status = MutableStateFlow("")
    private val _progress = MutableStateFlow(0f)
    private val _error = MutableStateFlow<String?>(null)

    private val hideAnti = settings.settingsFlow.map { it.hideAntiFeatures }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val categoriesFlow =
        repo.categories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val appsFlow: StateFlow<List<FDroidApp>> =
        combine(_query, _sort, hideAnti) { q, s, hide -> Triple(q, s, hide) }
            .flatMapLatest { (q, s, hide) -> repo.appsFlow(q, s, hide) }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val uiState: StateFlow<BrowseUiState> =
        combine(
            combine(_query, _sort, appsFlow) { q, s, apps -> Triple(q, s, apps) },
            combine(
                categoriesFlow,
                _isSyncing,
                _status,
                _progress,
                _error
            ) { cats, syncing, status, prog, err ->
                Quintuple(cats, syncing, status, prog, err)
            }
        ) { first, second ->
            BrowseUiState(
                query = first.first,
                sort = first.second,
                apps = first.third,
                categories = second.first,
                isSyncing = second.second,
                status = second.third,
                progress = second.fourth,
                errorMessage = second.fifth
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, BrowseUiState())


    init {
        // Initialize sort from settings.defaultSort
        viewModelScope.launch {
            settings.settingsFlow.collect { s ->
                val sort = when (s.defaultSort) {
                    0 -> SortOption.Name
                    1 -> SortOption.Updated
                    2 -> SortOption.Size
                    3 -> SortOption.Added
                    else -> SortOption.Updated
                }
                _sort.value = sort
            }
        }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setSort(s: SortOption) {
        _sort.value = s
    }

    fun clearError() {
        _error.value = null
    }

    fun syncRepos() = doSync(force = false)
    fun forceSyncRepos() = doSync(force = true)

    private fun doSync(force: Boolean) {
        viewModelScope.launch {
            _isSyncing.value = true
            _status.value = "Starting sync..."
            _progress.value = 0f
            _error.value = null

            val perRepoErrors = mutableListOf<String>()

            runCatching {
                val appCount = sync.syncAll(
                    force = force,
                    onProgress = { current, total, repoName ->
                        _status.value = "Syncing $repoName ($current/$total)..."
                        val p = (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        _progress.value = p
                    },
                    onRepoError = { repoName, msg ->
                        perRepoErrors.add("$repoName: $msg")
                    }
                )
                appCount
            }.onSuccess { totalApps ->
                when {
                    perRepoErrors.size == repo.categories().first().size && totalApps == 0 -> {
                        _error.value = "All repositories failed. Check your internet connection."
                    }

                    perRepoErrors.isNotEmpty() -> {
                        _error.value =
                            "Partial sync completed with errors:\n${perRepoErrors.joinToString("\n")}"
                    }

                    totalApps == 0 && !force -> {
                        _status.value = "No updates available"
                    }

                    totalApps == 0 && force -> {
                        _error.value = "No apps fetched. Repository might be empty or unreachable."
                    }

                    else -> {
                        _status.value = "Sync complete: $totalApps apps"
                    }
                }
                _progress.value = 1f
            }.onFailure { e ->
                _error.value = when {
                    e.message?.contains("No enabled repositories") == true ->
                        "No repositories enabled. Enable at least one in Settings."

                    e.message?.contains("timeout", ignoreCase = true) == true ->
                        "Connection timeout. Please check your internet connection."

                    else -> e.message ?: "Sync failed"
                }
                _status.value = "Sync failed"
                _progress.value = 0f
            }

            _isSyncing.value = false
        }
    }
}