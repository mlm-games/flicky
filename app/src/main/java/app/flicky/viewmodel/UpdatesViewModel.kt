package app.flicky.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.flicky.data.external.UpdatesPreference
import app.flicky.data.external.UpdatesPreferences
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import app.flicky.data.repository.AppRepository
import app.flicky.data.repository.InstalledAppsRepository
import app.flicky.install.Installer
import app.flicky.install.TaskStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UpdatesUiState(
    val installed: List<FDroidApp> = emptyList(),
    val updates: List<FDroidApp> = emptyList(),
    val suppressed: List<FDroidApp> = emptyList(),
    val installingPackages: Set<String> = emptySet(),
    val installProgress: Map<String, Float> = emptyMap(),
    val installedVersionsCode: Map<String, Long> = emptyMap(),
    val installedVersionsName: Map<String, String> = emptyMap(),
    val ignoredPrefs: Map<String, UpdatesPreference> = emptyMap()
)

class UpdatesViewModel(
    private val repo: AppRepository,
    private val installedRepo: InstalledAppsRepository,
    private val installer: Installer
) : ViewModel() {

    private val _ui = MutableStateFlow(UpdatesUiState())
    val ui: StateFlow<UpdatesUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.appsFlow("", sort = SortOption.Updated, hideAnti = false, showIncompatible = false),
                installedRepo.packageChangesFlow().onStart { emit(Unit) }
            ) { all, _ -> all }
                .collect { all ->
                    val installedDetails = installedRepo.getInstalledDetailed()
                    val installedMap = installedDetails.associateBy { it.packageName }
                    val installed = all.filter { installedMap.containsKey(it.packageName) }

                    val ignoreMap = withContext(Dispatchers.IO) {
                        installed.associate { app -> app.packageName to UpdatesPreferences[app.packageName] }
                    }

                    val codeMap = installedDetails.associate { it.packageName to it.versionCode }
                    val nameMap = installedDetails.associate { it.packageName to (it.versionName ?: "") }

                    val (updates, suppressed) = installed.partition { app ->
                        val cur = installedMap[app.packageName]?.versionCode ?: 0L
                        val pref = ignoreMap[app.packageName] ?: UpdatesPreference()
                        val candidate = app.versionCode.toLong() > cur
                        candidate && !(pref.ignoreUpdates || (pref.ignoreVersionCode > 0 && app.versionCode.toLong() <= pref.ignoreVersionCode))
                    }.let { (u, notU) ->
                        val suppressedList = notU.filter { app ->
                            val cur = installedMap[app.packageName]?.versionCode ?: 0L
                            app.versionCode.toLong() > cur
                        }
                        u to suppressedList
                    }

                    _ui.value = _ui.value.copy(
                        installed = installed,
                        updates = updates,
                        suppressed = suppressed,
                        installedVersionsCode = codeMap,
                        installedVersionsName = nameMap,
                        ignoredPrefs = ignoreMap
                    )
                }
        }

        viewModelScope.launch {
            installer.tasks.collect { map ->
                val installing = mutableSetOf<String>()
                val progress = mutableMapOf<String, Float>()
                map.forEach { (pkg, stage) ->
                    when (stage) {
                        is TaskStage.Downloading -> {
                            installing.add(pkg)
                            progress[pkg] = 0.5f * stage.progress
                        }
                        is TaskStage.Verifying -> {
                            installing.add(pkg)
                            progress[pkg] = 0.9f
                        }
                        is TaskStage.Installing -> {
                            installing.add(pkg)
                            progress[pkg] = 0.5f + 0.5f * stage.progress
                        }
                        is TaskStage.Finished -> {
                            if (!stage.success) {
                                // keep it out of installing set
                                progress.remove(pkg)
                            }
                        }
                        else -> {}
                    }
                }
                _ui.update {
                    it.copy(
                        installingPackages = installing,
                        installProgress = progress
                    )
                }
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
        val pref = UpdatesPreferences[packageName]
        withContext(Dispatchers.Main) {
            _ui.value = _ui.value.copy(
                ignoredPrefs = _ui.value.ignoredPrefs + (packageName to pref)
            )
        }
    }
}