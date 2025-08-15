package app.flicky.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import app.flicky.data.repository.AppRepository
import app.flicky.data.repository.RepositorySyncManager
import app.flicky.data.repository.SettingsRepository
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

    private val hideAnti = settings.settingsFlow.map { it.hideAntiFeatures }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val categoriesFlow = repo.categories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val appsFlow: StateFlow<List<FDroidApp>> =
        combine(_query, _sort, hideAnti) { q, s, hide -> Triple(q, s, hide) }
            .flatMapLatest { (q, s, hide) -> repo.appsFlow(q, s, hide) }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val uiState: StateFlow<BrowseUiState> =
        combine(
            combine(_query, _sort, appsFlow) { q, s, apps -> Triple(q, s, apps) },
            combine(categoriesFlow, _isSyncing, _status) { cats, syncing, status -> Triple(cats, syncing, status) }
        ) { first, second ->
            BrowseUiState(
                query = first.first,
                sort = first.second,
                apps = first.third,
                categories = second.first,
                isSyncing = second.second,
                status = second.third
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

    fun setQuery(q: String) { _query.value = q }
    fun setSort(s: SortOption) { _sort.value = s }
    fun clearError() { _error.value = null }

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
                sync.syncAll(
                    force = force,
                    onProgress = { current, total, repoName ->
                        _status.value = "Syncing $repoName..."
                        val p = (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        _progress.value = p
                    },
                    onRepoError = { repoName, msg ->
                        perRepoErrors.add("$repoName: $msg")
                    }
                )
            }.onSuccess { list ->
                if (perRepoErrors.isNotEmpty()) {
                    _error.value = "Some repositories failed:\n" + perRepoErrors.joinToString("\n")
                } else if (list.isEmpty()) {
                    _error.value = if (force) "No data fetched (servers returned no changes)" else "No new changes"
                }
                _status.value = "Done"
                _progress.value = 1f
            }.onFailure { e ->
                _error.value = e.message ?: "Sync failed"
                _status.value = "Sync failed"
            }

            _isSyncing.value = false
        }
    }
}