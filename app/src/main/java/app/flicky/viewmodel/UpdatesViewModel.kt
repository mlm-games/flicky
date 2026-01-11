package app.flicky.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import app.flicky.data.repository.AppRepository
import app.flicky.data.repository.AppUpdatePreference
import app.flicky.data.repository.InstalledAppsRepository
import app.flicky.data.repository.PreferredRepo
import app.flicky.data.repository.SettingsRepository
import app.flicky.data.repository.VariantSelector
import app.flicky.di.AppDependencies
import app.flicky.install.Installer
import app.flicky.install.TaskStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UpdatesUi(
    val installed: List<FDroidApp> = emptyList(),
    val suppressed: List<FDroidApp> = emptyList(),
    val updates: List<FDroidApp> = emptyList(),
    val installingPackages: Set<String> = emptySet(),
    val installedVersionsCode: Map<String, Long> = emptyMap(),
    val installedVersionsName: Map<String, String> = emptyMap(),
    val ignoredPrefs: Map<String, AppUpdatePreference> = emptyMap(),
    val ignored: Set<String> = emptySet(),
    val message: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@OptIn(FlowPreview::class)
class UpdatesViewModel(
    private val repo: AppRepository,
    private val installedRepo: InstalledAppsRepository,
    private val installer: Installer,
    private val settings: SettingsRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(UpdatesUi())
    val ui: StateFlow<UpdatesUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.appsFlow("", sort = SortOption.Updated, hideAnti = false, showIncompatible = false),
                installedRepo.packageChangesFlow().onStart { emit(Unit) },
                settings.observeAppUpdatePreferences()
            ) { allApps, _, allPrefs ->
                Triple(allApps, allPrefs, installedRepo.getInstalledDetailed())
            }
                .distinctUntilChanged()
                .debounce(150)
                .collect { (allApps, allPrefs, installedDetails) ->
                    _ui.update { it.copy(isLoading = true, error = null) }
                    try {
                        recalc(allApps, installedDetails, allPrefs.prefs)
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
                .map { tasks -> tasks.values.count { it is TaskStage.Finished && it.success } }
                .distinctUntilChanged()
                .filter { it > 0 }
                .collect { delay(500) }
        }

        viewModelScope.launch {
            installer.tasks.collect { map ->
                val installing = map.keys.filter { pkg ->
                    val stage = map[pkg]
                    stage != null && stage !is TaskStage.Finished && stage !is TaskStage.Cancelled
                }.toSet()
                _ui.update { it.copy(installingPackages = installing) }
            }
        }
    }

    private suspend fun recalc(
        allApps: List<FDroidApp>,
        installedDetails: List<InstalledAppsRepository.InstalledDetailed>,
        allPrefs: Map<String, AppUpdatePreference>
    ) = withContext(Dispatchers.IO) {
        val installedMap = installedDetails.associateBy { it.packageName }
        val installedFDroidApps = allApps.filter { installedMap.containsKey(it.packageName) }
        val installedPackageNames = installedFDroidApps.map { it.packageName }

        val allVariantsByPackage = AppDependencies.db.appDao()
            .variantsForPackages(installedPackageNames)
            .groupBy { it.packageName }

        val latestCompatByPkg = installedFDroidApps.associate { app ->
            val pref = allPrefs[app.packageName] ?: AppUpdatePreference()
            val variantsForApp = allVariantsByPackage[app.packageName] ?: emptyList()

            val chosen = if (variantsForApp.isNotEmpty()) {
                VariantSelector.pick(
                    variants = variantsForApp,
                    preferred = PreferredRepo.Auto,
                    preferredRepoUrl = pref.preferredRepoUrl,
                    strict = pref.lockToRepo
                )
            } else null

            val latestCompat = chosen?.takeIf { it.isCompatible }?.versionCode?.toLong()
                ?: variantsForApp.filter { it.isCompatible }.maxOfOrNull { it.versionCode.toLong() }
                ?: 0L

            app.packageName to latestCompat
        }

        val (updates, suppressed, upToDate) = partitionUpdates(
            installedFDroidApps,
            installedMap,
            latestCompatByPkg,
            allPrefs
        )

        _ui.update {
            it.copy(
                installed = upToDate,
                updates = updates,
                suppressed = suppressed,
                installedVersionsCode = installedDetails.associate { p -> p.packageName to p.versionCode },
                installedVersionsName = installedDetails.associate { p -> p.packageName to (p.versionName ?: "") },
                ignoredPrefs = allPrefs
            )
        }
    }

    private fun partitionUpdates(
        installedApps: List<FDroidApp>,
        installedMap: Map<String, InstalledAppsRepository.InstalledDetailed>,
        latestCompatMap: Map<String, Long>,
        prefsMap: Map<String, AppUpdatePreference>
    ): Triple<List<FDroidApp>, List<FDroidApp>, List<FDroidApp>> {
        val updates = mutableListOf<FDroidApp>()
        val suppressed = mutableListOf<FDroidApp>()
        val upToDate = mutableListOf<FDroidApp>()

        for (app in installedApps) {
            val currentVersion = installedMap[app.packageName]?.versionCode ?: 0L
            val latestVersion = latestCompatMap[app.packageName] ?: 0L

            if (latestVersion > currentVersion) {
                val pref = prefsMap[app.packageName] ?: AppUpdatePreference()
                val isIgnored = pref.ignoreUpdates ||
                        (pref.ignoreVersionCode != 0L && latestVersion <= pref.ignoreVersionCode)
                if (isIgnored) {
                    suppressed.add(app)
                } else {
                    updates.add(app)
                }
            } else {
                upToDate.add(app)
            }
        }
        return Triple(
            updates.sortedByDescending { it.lastUpdated },
            suppressed.sortedByDescending { it.lastUpdated },
            upToDate
        )
    }

    fun ignoreThisVersion(app: FDroidApp) {
        viewModelScope.launch(Dispatchers.IO) {
            settings.updateAppUpdatePreference(app.packageName) {
                it.copy(ignoreVersionCode = app.versionCode.toLong())
            }
        }
    }

    fun ignoreAllUpdates(app: FDroidApp) {
        viewModelScope.launch(Dispatchers.IO) {
            settings.updateAppUpdatePreference(app.packageName) {
                it.copy(ignoreUpdates = true)
            }
        }
    }

    fun stopIgnoring(app: FDroidApp) {
        viewModelScope.launch(Dispatchers.IO) {
            settings.updateAppUpdatePreference(app.packageName) {
                it.copy(ignoreUpdates = false, ignoreVersionCode = 0)
            }
        }
    }
}