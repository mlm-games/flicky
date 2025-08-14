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
    val status: String = ""
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
    private val _hideAnti = settings.settingsFlow.map { it.hideAntiFeatures }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val categoriesFlow = repo.categories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val appsFlow: StateFlow<List<FDroidApp>> =
        combine(_query, _sort, _hideAnti) { q, s, hide -> Triple(q, s, hide) }
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

    fun syncRepos() {
        viewModelScope.launch {
            _isSyncing.value = true
            _status.value = "Syncing repositories..."
            runCatching { sync.syncAll(true) }
            _status.value = "Done"
            _isSyncing.value = false
        }
    }
}