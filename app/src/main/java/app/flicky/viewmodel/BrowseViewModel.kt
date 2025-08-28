package app.flicky.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import app.flicky.data.repository.AppRepository
import app.flicky.data.repository.RepositorySyncManager
import app.flicky.data.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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

@OptIn(FlowPreview::class)
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
    private val showIncompat = settings.settingsFlow.map { it.showIncompatible }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val paged: StateFlow<PagingData<FDroidApp>> =
        combine(_query, _sort, hideAnti, showIncompat) { q, s, h, si -> arrayOf(q, s, h, si) }
            .flatMapLatest { (q, s, h, si) ->
                @Suppress("UNCHECKED_CAST")
                repo.pagedAppsFlow(q as String, s as SortOption, h as Boolean, si as Boolean)
            }
            .cachedIn(viewModelScope)
            .stateIn(viewModelScope, SharingStarted.Lazily, PagingData.empty())

    private val categoriesFlow =
        repo.categories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val appsFlow: StateFlow<List<FDroidApp>> =
        combine(_query, _sort, hideAnti, showIncompat) { q, s, hide, si -> arrayOf(q, s, hide, si) }
            .flatMapLatest { (q, s, hide, si) ->
                @Suppress("UNCHECKED_CAST")
                repo.appsFlow(q as String, s as SortOption, hide as Boolean, si as Boolean)
            }
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

        // Trigger resync when repositories change
        viewModelScope.launch {
            settings.repositoriesFlow
                .map { repos -> repos.filter { it.enabled }.map { it.url }.sorted() }
                .distinctUntilChanged()
                .drop(1)
                .debounce(350)
                .collect {
                    sync.cancelCurrentSync()
                    forceSyncRepos()
                }
        }

        // Collect global sync state so UI reflects WorkManager-driven syncs too
        viewModelScope.launch {
            sync.state.collect { st ->
                _isSyncing.value = st.active
                _progress.value = st.progress
                _status.value = if (st.active) {
                    "Syncing ${st.repoName} (${st.current}/${st.total})"
                } else {
                    st.message
                }
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
            _error.value = null
            val perRepoErrors = mutableListOf<String>()

            runCatching {
                val appCount = sync.syncAll(
                    force = force,
                    onProgress = { current, total, repoName ->
                        _status.value = "Syncing $repoName ($current/$total)..."
                    },
                    onRepoError = { repoName, msg ->
                        perRepoErrors.add("$repoName: $msg")
                    }
                )
                appCount
            }.onSuccess { totalApps ->
                when {
                    perRepoErrors.isNotEmpty() && totalApps == 0 ->
                        _error.value = "Sync completed with errors:\n${perRepoErrors.joinToString("\n")}"
                    perRepoErrors.isNotEmpty() ->
                        _error.value = "Partial sync completed:\n${perRepoErrors.joinToString("\n")}"
                }
            }.onFailure { e ->
                _error.value = when {
                    e.message?.contains("No enabled repositories") == true ->
                        "No repositories enabled. Enable at least one in Settings."
                    e.message?.contains("timeout", ignoreCase = true) == true ->
                        "Connection timeout. Please check your internet connection."
                    else -> e.message ?: "Sync failed"
                }
            }
        }
    }
}