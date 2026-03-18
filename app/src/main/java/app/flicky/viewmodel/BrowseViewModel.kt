package app.flicky.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.flicky.R
import app.flicky.data.local.AppDao
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import app.flicky.data.repository.AppRepository
import app.flicky.data.repository.RepositorySyncManager
import app.flicky.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BrowseUiState(
    val isSyncing: Boolean = false,
    val statusText: String = "",
    val statusTextRes: UiText? = null,
    val progress: Float = 0f,
    val errorMessage: UiText? = null
)

sealed class UiText {
    data class StringResource(@param:StringRes val resId: Int, val args: List<Any> = emptyList()) : UiText()
}


@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class BrowseViewModel(
    private val repo: AppRepository,
    private val sync: RepositorySyncManager,
    private val settings: SettingsRepository,
    private val appDao: AppDao
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(SortOption.Updated)
    val sort: StateFlow<SortOption> = _sort.asStateFlow()

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    val pagedApps: StateFlow<PagingData<FDroidApp>> =
        combine(
            _query.debounce(300),
            _sort,
            settings.settingsFlow.map { it.hideAntiFeatures }.distinctUntilChanged(),
            settings.settingsFlow.map { it.showIncompatible }.distinctUntilChanged()
        ) { query, sort, hideAnti, showIncompat ->
            repo.pagedAppsFlow(query, sort, hideAnti, showIncompat)
        }.flatMapLatest { it }
            .cachedIn(viewModelScope)
            .stateIn(viewModelScope, SharingStarted.Lazily, PagingData.empty())

    init {
        viewModelScope.launch {
            _query.value = runCatching { settings.getLastQuery() }.getOrDefault("")
        }
        viewModelScope.launch {
            settings.settingsFlow
                .map { it.defaultSort }
                .distinctUntilChanged()
                .collect { sortIndex ->
                    _sort.value = when (sortIndex) {
                        0 -> SortOption.Name
                        1 -> SortOption.NameDesc
                        2 -> SortOption.Updated
                        3 -> SortOption.Size
                        4 -> SortOption.Added
                        else -> SortOption.Updated
                    }
                }
        }

        viewModelScope.launch {
            sync.state.collect { st ->
                _uiState.update {
                    it.copy(
                        isSyncing = st.active,
                        progress = st.progress,
                        statusTextRes = if (st.active && st.repoName.isNotBlank()) {
                            UiText.StringResource(R.string.sync_status, listOf(st.repoName, st.current, st.total))
                        } else null
                    )
                }
            }
        }
        viewModelScope.launch {
            query.debounce(400).collect { q -> runCatching { settings.setLastQuery(q) } }
        }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setSort(s: SortOption) {
        _sort.value = s
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun syncRepos() = doSync(force = false)
    fun forceSyncRepos() = doSync(force = true)

    fun clearAllApps() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                appDao.clear()
                appDao.clearVariants()
            }
        }
    }

    private fun doSync(force: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            runCatching {
                sync.syncAll(force = force)
            }.onSuccess { (_, repoErrors) ->
                if (repoErrors.isNotEmpty()) {
                    val errorString = repoErrors.joinToString("\n") { "${it.first}: ${it.second}" }
                    _uiState.update {
                        it.copy(errorMessage = UiText.StringResource(R.string.sync_completed_with_errors, listOf(errorString)))
                    }
                }
            }.onFailure { e ->
                val errorRes = when {
                    e.message?.contains("No enabled repositories") == true -> R.string.no_repos_enabled
                    e.message?.contains("timeout", ignoreCase = true) == true -> R.string.connection_timeout
                    else -> R.string.sync_failed
                }
                _uiState.update { it.copy(errorMessage = UiText.StringResource(errorRes)) }
            }
        }
    }
}