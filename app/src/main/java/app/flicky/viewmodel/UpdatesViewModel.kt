package app.flicky.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.flicky.data.model.FDroidApp
import app.flicky.data.repository.AppRepository
import app.flicky.data.repository.InstalledAppsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UpdatesUiState(
    val installed: List<FDroidApp> = emptyList(),
    val updates: List<FDroidApp> = emptyList()
)

class UpdatesViewModel(
    private val repo: AppRepository,
    private val installedRepo: InstalledAppsRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(UpdatesUiState())
    val ui: StateFlow<UpdatesUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repo.appsFlow("", sort = app.flicky.data.model.SortOption.Updated, hideAnti = false).collect { all ->
                val installedMap = installedRepo.getInstalled().associateBy { it.packageName }
                val installed = all.filter { installedMap.containsKey(it.packageName) }

                val updates = installed.filter { app ->
                    val cur = installedMap[app.packageName]?.versionCode ?: 0L
                    app.versionCode > cur
                }

                _ui.value = UpdatesUiState(installed = installed, updates = updates)
            }
        }
    }
}