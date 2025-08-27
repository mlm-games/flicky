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
    val updates: List<FDroidApp> = emptyList(),
    val installingPackages: Set<String> = emptySet(),
    val installProgress: Map<String, Float> = emptyMap(),
    val installedVersionsCode: Map<String, Long> = emptyMap(),
    val installedVersionsName: Map<String, String> = emptyMap()
)

class UpdatesViewModel(
    private val repo: AppRepository,
    private val installedRepo: InstalledAppsRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(UpdatesUiState())
    val ui: StateFlow<UpdatesUiState> = _ui.asStateFlow()

    init {
        // Recompute when either app catalog changes OR installed packages change
        viewModelScope.launch {
            combine(
                repo.appsFlow("", sort = app.flicky.data.model.SortOption.Updated, hideAnti = false),
                installedRepo.packageChangesFlow().onStart { emit(Unit) } // emit once initially
            ) { all, _ -> all }
                .collect { all ->
                    val installedDetails = installedRepo.getInstalledDetailed()
                    val installedMap = installedDetails.associateBy { it.packageName }

                    val installed = all.filter { installedMap.containsKey(it.packageName) }

                    val updates = installed.filter { app ->
                        val cur = installedMap[app.packageName]?.versionCode ?: 0L
                        app.versionCode.toLong() > cur
                    }

                    val codeMap = installedDetails.associate { it.packageName to it.versionCode }
                    val nameMap = installedDetails.associate { it.packageName to (it.versionName ?: "") }

                    _ui.value = _ui.value.copy(
                        installed = installed,
                        updates = updates,
                        installedVersionsCode = codeMap,
                        installedVersionsName = nameMap
                    )
                }
        }
    }

    fun updateInstallProgress(packageName: String, progress: Float) {
        _ui.value = _ui.value.copy(
            installProgress = _ui.value.installProgress + (packageName to progress)
        )
    }

    fun setInstalling(packageName: String, installing: Boolean) {
        _ui.value = _ui.value.copy(
            installingPackages = if (installing) {
                _ui.value.installingPackages + packageName
            } else {
                _ui.value.installingPackages - packageName
            },
            installProgress = if (!installing) {
                _ui.value.installProgress - packageName
            } else {
                _ui.value.installProgress
            }
        )
    }
}
