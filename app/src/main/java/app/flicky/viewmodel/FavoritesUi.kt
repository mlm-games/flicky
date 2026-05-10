package app.flicky.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import app.flicky.data.repository.AppRepository
import app.flicky.data.repository.InstalledAppsRepository
import app.flicky.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FavoritesUi(
    val favorites: List<FDroidApp> = emptyList(),
    val installedVersions: Map<String, Long> = emptyMap(),
    val isLoading: Boolean = true
)

class FavoritesViewModel(
    private val repo: AppRepository,
    private val installedRepo: InstalledAppsRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(FavoritesUi())
    val ui: StateFlow<FavoritesUi> = _ui.asStateFlow()

    private val _sort = MutableStateFlow(SortOption.Name)
    val sort: StateFlow<SortOption> = _sort.asStateFlow()

    val reverseSort: StateFlow<Boolean> = settings.settingsFlow
        .map { it.reverseSort }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            combine(
                settings.observeFavorites(),
                repo.appsFlow("", SortOption.Updated, false, hideAnti = false, showIncompatible = true),
                installedRepo.packageChangesFlow().onStart { emit(Unit) },
                _sort,
                settings.settingsFlow.map { it.reverseSort }.distinctUntilChanged()
            ) { favSet, allApps, _, currentSort, reverse ->
                val favApps = allApps.filter { it.packageName in favSet }
                val sorted = when (currentSort) {
                    SortOption.Name -> {
                        if (reverse) favApps.sortedByDescending { it.name.lowercase() }
                        else favApps.sortedBy { it.name.lowercase() }
                    }
                    SortOption.Updated -> {
                        if (reverse) favApps.sortedBy { it.lastUpdated }
                        else favApps.sortedByDescending { it.lastUpdated }
                    }
                    SortOption.Size -> {
                        if (reverse) favApps.sortedByDescending { it.size }
                        else favApps.sortedBy { it.size }
                    }
                    SortOption.Added -> {
                        if (reverse) favApps.sortedBy { it.added }
                        else favApps.sortedByDescending { it.added }
                    }
                }
                val installed = installedRepo.getInstalled().associate { it.packageName to it.versionCode }
                FavoritesUi(
                    favorites = sorted,
                    installedVersions = installed,
                    isLoading = false
                )
            }
                .catch { _ui.update { it.copy(isLoading = false) } }
                .collect { _ui.value = it }
        }
    }

    fun setSort(option: SortOption) {
        _sort.value = option
    }

    fun setReverseSort(reverse: Boolean) {
        viewModelScope.launch {
            settings.updateSettings { it.copy(reverseSort = reverse) }
        }
    }

    fun removeFavorite(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settings.setFavorite(packageName, false)
        }
    }
}