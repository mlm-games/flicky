package app.flicky.viewmodel

import android.util.Log
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.flicky.AppGraph
import app.flicky.data.external.UpdatesPreference
import app.flicky.data.external.UpdatesPreferences
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import app.flicky.data.repository.AppRepository
import app.flicky.data.repository.InstalledAppsRepository
import app.flicky.data.repository.PreferredRepo
import app.flicky.data.repository.VariantSelector
import app.flicky.install.Installer
import app.flicky.install.TaskStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

data class UpdatesUi(
    val installed: List<FDroidApp> = emptyList(),
    val suppressed: List<FDroidApp> = emptyList(),
    val updates: List<FDroidApp> = emptyList(),
    val installingPackages: Set<String> = emptySet(),
    val installedVersionsCode: Map<String, Long> = emptyMap(),
    val installedVersionsName: Map<String, String> = emptyMap(),
    val ignoredPrefs: Map<String, UpdatesPreference> = emptyMap(),
    val ignored: Set<String> = emptySet(),
    val message: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

class UpdatesViewModel(
    private val repo: AppRepository,
    private val installedRepo: InstalledAppsRepository,
    private val installer: Installer
) : ViewModel() {

    private val _ui = MutableStateFlow(UpdatesUi())
    val ui: StateFlow<UpdatesUi> = _ui.asStateFlow()

    private val prefsVersion = AtomicInteger(0)

    init {
        viewModelScope.launch {
            combine(
                repo.appsFlow("", sort = SortOption.Updated, hideAnti = false, showIncompatible = false),
                installedRepo.packageChangesFlow().onStart { emit(Unit) },
                snapshotFlow { prefsVersion.get() } // React to preference changes
            ) { all, _, _ -> all }
                .distinctUntilChanged()
                .collect { all ->
                    _ui.update { it.copy(isLoading = true, error = null) }
                    try {
                        recalc(all)
                        _ui.update { it.copy(isLoading = false) }
                    } catch (e: Exception) {
                        Log.e("UpdatesViewModel", "Failed to calculate updates", e)
                        _ui.update {
                            it.copy(
                                isLoading = false,
                                error = "Failed to load updates: ${e.message}"
                            )
                        }
                    }
                }
        }

        viewModelScope.launch {
            installer.tasks
                .map { tasks ->
                    tasks.values.count { stage ->
                        stage is TaskStage.Finished && stage.success
                    }
                }
                .distinctUntilChanged()
                .filter { it > 0 } // Only when something successfully finished
                .collect {
                    kotlinx.coroutines.delay(500) // Brief delay for system to update
                    prefsVersion.incrementAndGet()
                }
        }

        viewModelScope.launch {
            installer.tasks.collect { map ->
                val installing = map.keys.filter { pkg ->
                    val stage = map[pkg]
                    stage != null &&
                            stage !is TaskStage.Finished &&
                            stage !is TaskStage.Cancelled
                }.toSet()

                _ui.update { it.copy(installingPackages = installing) }
            }
        }
    }

    private suspend fun recalc(all: List<FDroidApp>) = withContext(Dispatchers.IO) {
        val installedDetails = installedRepo.getInstalledDetailed()
        val installedMap = installedDetails.associateBy { it.packageName }
        val installed = all.filter { installedMap.containsKey(it.packageName) }

        val ignoreMap = installed.associate { app ->
            app.packageName to UpdatesPreferences[app.packageName]
        }

        val codeMap = installedDetails.associate { it.packageName to it.versionCode }
        val nameMap = installedDetails.associate { it.packageName to (it.versionName ?: "") }

        val latestCompatByPkg = installed.associate { app ->
            val pref = UpdatesPreferences[app.packageName]
            val allVariants = try {
                AppGraph.db.appDao().variantsFor(app.packageName)
            } catch (e: Exception) {
                Log.e("UpdatesViewModel", "Failed to load variants for ${app.packageName}", e)
                emptyList()
            }

            val chosen = if (allVariants.isNotEmpty()) {
                VariantSelector.pick(
                    variants = allVariants,
                    preferred = PreferredRepo.Auto,
                    preferredRepoUrl = pref.preferredRepoUrl,
                    strict = pref.lockToRepo
                )
            } else null

            val latestCompat = chosen?.takeIf { it.isCompatible }?.versionCode?.toLong()
                ?: allVariants
                    .filter { it.isCompatible }
                    .maxOfOrNull { it.versionCode.toLong() }
                ?: 0L // No compatible variants found

            app.packageName to latestCompat
        }

        val (updates, allNotUpdates) = installed.partition { app ->
            val cur = installedMap[app.packageName]?.versionCode ?: 0L
            val latestCompat = latestCompatByPkg[app.packageName] ?: 0L
            val hasUpdate = latestCompat > cur
            val pref = ignoreMap[app.packageName] ?: UpdatesPreference()

            hasUpdate && !pref.ignoreUpdates &&
                    (pref.ignoreVersionCode == 0L || latestCompat > pref.ignoreVersionCode)
        }

        // Suppressed = has update but ignored
        val suppressed = allNotUpdates.filter { app ->
            val cur = installedMap[app.packageName]?.versionCode ?: 0L
            val latestCompat = latestCompatByPkg[app.packageName] ?: 0L
            latestCompat > cur
        }

        _ui.value = _ui.value.copy(
            installed = installed.filter { app ->
                val cur = installedMap[app.packageName]?.versionCode ?: 0L
                val latestCompat = latestCompatByPkg[app.packageName] ?: 0L
                latestCompat <= cur // No update available
            },
            updates = updates.sortedByDescending { it.lastUpdated },
            suppressed = suppressed.sortedByDescending { it.lastUpdated },
            installedVersionsCode = codeMap,
            installedVersionsName = nameMap,
            ignoredPrefs = ignoreMap
        )
    }

    fun ignoreThisVersion(packageName: String, versionCode: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = UpdatesPreferences[packageName]
            UpdatesPreferences[packageName] = current.copy(ignoreVersionCode = versionCode)
            prefsVersion.incrementAndGet()
        }
    }

    fun ignoreAllUpdates(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = UpdatesPreferences[packageName]
            UpdatesPreferences[packageName] = current.copy(ignoreUpdates = true)
            prefsVersion.incrementAndGet()
        }
    }

    fun stopIgnoring(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            UpdatesPreferences[packageName] = UpdatesPreference(
                ignoreUpdates = false,
                ignoreVersionCode = 0
            )
            prefsVersion.incrementAndGet()
        }
    }
}