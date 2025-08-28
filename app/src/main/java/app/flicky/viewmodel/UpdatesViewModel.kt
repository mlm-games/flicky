package app.flicky.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.flicky.data.external.UpdatesPreference
import app.flicky.data.external.UpdatesPreferences
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import app.flicky.data.repository.AppRepository
import app.flicky.data.repository.InstalledAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UpdatesUiState(
    val installed: List<FDroidApp> = emptyList(),
    val updates: List<FDroidApp> = emptyList(),
    val installingPackages: Set<String> = emptySet(),
    val installProgress: Map<String, Float> = emptyMap(),
    val installedVersionsCode: Map<String, Long> = emptyMap(),
    val installedVersionsName: Map<String, String> = emptyMap(),
    val ignoredPrefs: Map<String, UpdatesPreference> = emptyMap()
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
                repo.appsFlow("", sort = SortOption.Updated, hideAnti = false, showIncompatible = false),
                installedRepo.packageChangesFlow().onStart { emit(Unit) } // emit once initially
            ) { all, _ -> all }
                .collect { all ->
                    val installedDetails = installedRepo.getInstalledDetailed()
                    val installedMap = installedDetails.associateBy { it.packageName }

                    val installed = all.filter { installedMap.containsKey(it.packageName) }

                    // Read ignore prefs on background thread
                    val ignoreMap = withContext(Dispatchers.IO) {
                        installed.associate { app -> app.packageName to UpdatesPreferences[app.packageName] }
                    }

                    val updates = installed.filter { app ->
                        val cur = installedMap[app.packageName]?.versionCode ?: 0L
                        val pref = ignoreMap[app.packageName] ?: UpdatesPreference()
                        val candidate = app.versionCode.toLong() > cur
                        if (!candidate) {
                            false
                        } else {
                            !pref.ignoreUpdates && (pref.ignoreVersionCode <= 0L || app.versionCode.toLong() > pref.ignoreVersionCode)
                        }
                    }

                    val codeMap = installedDetails.associate { it.packageName to it.versionCode }
                    val nameMap = installedDetails.associate { it.packageName to (it.versionName ?: "") }

                    _ui.value = _ui.value.copy(
                        installed = installed,
                        updates = updates,
                        installedVersionsCode = codeMap,
                        installedVersionsName = nameMap,
                        ignoredPrefs = ignoreMap
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

    fun ignoreThisVersion(packageName: String, versionCode: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = UpdatesPreferences[packageName]
            UpdatesPreferences[packageName] = current.copy(ignoreVersionCode = versionCode)
            refreshIgnoredFor(packageName)
        }
    }

    fun ignoreAllUpdates(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = UpdatesPreferences[packageName]
            UpdatesPreferences[packageName] = current.copy(ignoreUpdates = true)
            refreshIgnoredFor(packageName)
        }
    }

    fun stopIgnoring(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            UpdatesPreferences[packageName] = UpdatesPreference(ignoreUpdates = false, ignoreVersionCode = 0)
            refreshIgnoredFor(packageName)
        }
    }

    private suspend fun refreshIgnoredFor(packageName: String) {
        // Trigger UI recomputation by updating ignoredPrefs map entry
        val pref = UpdatesPreferences[packageName]
        withContext(Dispatchers.Main) {
            _ui.value = _ui.value.copy(
                ignoredPrefs = _ui.value.ignoredPrefs + (packageName to pref)
            )
        }
    }
}
