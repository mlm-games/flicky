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

    init {
        viewModelScope.launch {
            combine(
                settings.observeFavorites(),
                repo.appsFlow("", SortOption.Updated, hideAnti = false, showIncompatible = true),
                installedRepo.packageChangesFlow().onStart { emit(Unit) },
                _sort
            ) { favSet, allApps, _, currentSort ->
                val favApps = allApps.filter { it.packageName in favSet }
                val sorted = when (currentSort) {
                    SortOption.Name -> favApps.sortedBy { it.name.lowercase() }
                    SortOption.Updated -> favApps.sortedByDescending { it.lastUpdated }
                    SortOption.Size -> favApps.sortedBy { it.size }
                    SortOption.Added -> favApps.sortedByDescending { it.added }
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

    fun removeFavorite(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settings.setFavorite(packageName, false)
        }
    }
}