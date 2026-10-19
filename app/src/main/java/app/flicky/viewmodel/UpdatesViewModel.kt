package app.flicky.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.flicky.data.local.AppDao
import app.flicky.data.local.AppVariant
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.SortOption
import app.flicky.data.repository.AppRepository
import app.flicky.data.repository.AppUpdatePreference
import app.flicky.data.repository.InstalledAppsRepository
import app.flicky.data.repository.PreferredRepo
import app.flicky.data.repository.SettingsRepository
import app.flicky.data.repository.UpdatesCalculator
import app.flicky.install.Installer
import app.flicky.install.TaskStage
import app.flicky.notify.UpdateNotifications
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
    val updateCandidates: Map<String, AppVariant> = emptyMap(),
    val installingPackages: Set<String> = emptySet(),
    val installedVersionsCode: Map<String, Long> = emptyMap(),
    val installedVersionsName: Map<String, String> = emptyMap(),
    val ignoredPrefs: Map<String, AppUpdatePreference> = emptyMap(),
    val ignored: Set<String> = emptySet(),
    val message: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

private data class UpdateCalcData(
    val allApps: List<FDroidApp>,
    val installedDetails: List<InstalledAppsRepository.InstalledDetailed>,
    val allPrefs: Map<String, AppUpdatePreference>,
    val preferredRepo: PreferredRepo,
    val ignoreUnstable: Boolean
)

@OptIn(FlowPreview::class)
class UpdatesViewModel(
    private val repo: AppRepository,
    private val installedRepo: InstalledAppsRepository,
    private val installer: Installer,
    private val settings: SettingsRepository,
    private val appDao: AppDao,
    private val context: Context
) : ViewModel() {

    private val _ui = MutableStateFlow(UpdatesUi())
    val ui: StateFlow<UpdatesUi> = _ui.asStateFlow()

    init {
        UpdateNotifications.cancelUpdatesAvailable(context)

        viewModelScope.launch {
            combine(
                repo.appsFlow("", sort = SortOption.Updated, reverseSort = false, hideAnti = false, showIncompatible = false),
                installedRepo.packageChangesFlow().onStart { emit(Unit) },
                settings.observeAppUpdatePreferences(),
                settings.settingsFlow.map { it.preferredRepo }.distinctUntilChanged(),
                settings.settingsFlow.map { it.ignoreUnstable }.distinctUntilChanged()
            ) { allApps, _, allPrefsMap, preferredRepoIdx, ignoreUnstable ->
                UpdateCalcData(allApps, installedRepo.getInstalledDetailed(), allPrefsMap.prefs, PreferredRepo.fromIndex(preferredRepoIdx), ignoreUnstable)
            }
                .distinctUntilChanged()
                .debounce(150)
                .collect { data ->
                    _ui.update { it.copy(isLoading = true, error = null) }
                    try {
                        recalc(data.allApps, data.installedDetails, data.allPrefs, data.preferredRepo, data.ignoreUnstable)
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
        allPrefs: Map<String, AppUpdatePreference>,
        preferredRepo: PreferredRepo,
        ignoreUnstable: Boolean
    ) = withContext(Dispatchers.IO) {
        val snapshot = UpdatesCalculator.compute(
            allApps = allApps,
            installedDetails = installedDetails,
            appDao = appDao,
            prefs = allPrefs,
            preferredRepo = preferredRepo,
            ignoreUnstable = ignoreUnstable
        )

        _ui.update {
            it.copy(
                installed = snapshot.upToDate,
                updates = snapshot.updates,
                suppressed = snapshot.suppressed,
                updateCandidates = snapshot.candidates,
                installedVersionsCode = installedDetails.associate { p -> p.packageName to p.versionCode },
                installedVersionsName = installedDetails.associate { p -> p.packageName to (p.versionName ?: "") },
                ignoredPrefs = allPrefs
            )
        }
    }

    fun ignoreThisVersion(packageName: String, versionCode: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            settings.updateAppUpdatePreference(packageName) {
                it.copy(ignoreVersionCode = versionCode)
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

    fun setUpdateOverMetered(packageName: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settings.updateAppUpdatePreference(packageName) {
                it.copy(updateOverMetered = enabled)
            }
        }
    }
}
