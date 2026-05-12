package app.flicky.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.flicky.data.local.AppDao
import app.flicky.data.local.AppVariant
import app.flicky.data.model.FDroidApp
import app.flicky.data.model.ReproducibleBuildInfo
import app.flicky.data.remote.ReproducibleBuildRepository
import app.flicky.data.repository.InstalledAppsRepository
import app.flicky.data.repository.SettingsRepository
import app.flicky.data.repository.PreferredRepo
import app.flicky.data.repository.VariantSelector
import app.flicky.install.Installer
import app.flicky.install.TaskStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DetailUiState(
    val app: FDroidApp? = null,
    val installedVersionCode: Long? = null,
    val isInstalling: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val stage: TaskStage? = null,
    val variants: List<AppVariant> = emptyList(),
    val isFavorite: Boolean = false,
    val appNotFound: Boolean = false,
    val reproducibleBuildInfo: ReproducibleBuildInfo? = null,
    val showReproducibleBadges: Boolean = false,
)

class AppDetailViewModel(
    private val dao: AppDao,
    private val installedRepo: InstalledAppsRepository,
    private val installer: Installer,
    private val settings: SettingsRepository,
    private val rbRepo: ReproducibleBuildRepository,
    private val packageName: String
) : ViewModel() {

    private val _ui = MutableStateFlow(DetailUiState())
    val ui: StateFlow<DetailUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeOne(packageName).collect { app ->
                val installed = installedRepo.getVersionCode(packageName)
                val variants = runCatching { dao.variantsFor(packageName) }.getOrElse { emptyList() }
                _ui.update {
                    it.copy(
                        app = app,
                        appNotFound = app == null,
                        installedVersionCode = installed,
                        variants = variants.sortedByDescending { v -> v.versionCode }
                    )
                }
            }
        }

        viewModelScope.launch {
            settings.observeIsFavorite(packageName).collect { isFav ->
                _ui.update { it.copy(isFavorite = isFav) }
            }
        }

        viewModelScope.launch {
            installer.errors.collect { map ->
                val msg = map[packageName]
                if (!msg.isNullOrBlank()) {
                    _ui.update { it.copy(error = msg) }
                }
            }
        }
        viewModelScope.launch {
            installer.tasks
                .map { it[packageName] }
                .distinctUntilChanged()
                .onStart { emit(installer.tasks.value[packageName]) }
                .collect { stage ->
                    when (stage) {
                        is TaskStage.Downloading -> _ui.update {
                            it.copy(
                                isInstalling = true,
                                stage = stage,
                                progress = (0.99f * stage.progress).coerceIn(0f, 0.99f),
                                error = null
                            )
                        }
                        is TaskStage.Verifying -> _ui.update {
                            it.copy(isInstalling = true, stage = stage, progress = 0.995f, error = null)
                        }
                        is TaskStage.Installing -> _ui.update {
                            it.copy(
                                isInstalling = true,
                                stage = stage,
                                progress = (0.99f + 0.01f * stage.progress).coerceIn(0.99f, 1f),
                                error = null
                            )
                        }
                        is TaskStage.Cancelled -> _ui.update {
                            it.copy(isInstalling = false, stage = stage, error = null)
                        }
                        is TaskStage.Finished -> {
                            _ui.update {
                                it.copy(
                                    isInstalling = false,
                                    stage = stage,
                                    progress = if (stage.success) 1f else it.progress,
                                    error = if (stage.success) null else it.error
                                )
                            }
                            val newInstalled = installedRepo.getVersionCode(packageName)
                            _ui.update { it.copy(installedVersionCode = newInstalled) }
                        }
                        else -> { /* no-op */ }
                    }
                }
        }

        viewModelScope.launch {
            installedRepo.packageNameChangesFlow().collect { changed ->
                if (changed == packageName) {
                    val newInstalled = installedRepo.getVersionCode(packageName)
                    _ui.update {
                        it.copy(
                            installedVersionCode = newInstalled,
                            isInstalling = false,
                            progress = if (newInstalled != null) 1f else it.progress,
                            error = null
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            settings.settingsFlow
                .map { it.showReproducibleBadges }
                .distinctUntilChanged()
                .collect { enabled ->
                    _ui.update { it.copy(showReproducibleBadges = enabled) }
                    if (enabled && _ui.value.reproducibleBuildInfo == null) {
                        val info = rbRepo.fetchReproducibleBuildInfo(packageName)
                        _ui.update { it.copy(reproducibleBuildInfo = info) }
                    }
                }
        }
    }

    fun install() {
        val app = _ui.value.app ?: return

        viewModelScope.launch {
            _ui.update { it.copy(isInstalling = true, progress = 0f, error = null, stage = TaskStage.Downloading(0f)) }

            try {
                Log.d("AppDetailViewModel", "Starting install for ${app.packageName}")

                val prefIdx = settings.settingsFlow.first().preferredRepo
                val globalPref = PreferredRepo.fromIndex(prefIdx)
                val globalIgnoreUnstable = settings.settingsFlow.first().ignoreUnstable
                val perAppPref = settings.getAppUpdatePreference(packageName)
                val variants = dao.variantsFor(app.packageName)
                val effectiveIgnoreUnstable = perAppPref.ignoreUnstable ?: globalIgnoreUnstable

                val chosen = VariantSelector.pick(
                    variants = variants,
                    preferred = globalPref,
                    preferredRepoUrl = perAppPref.preferredRepoUrl,
                    strict = perAppPref.lockToRepo,
                    ignoreUnstable = effectiveIgnoreUnstable
                )

                val success = if (chosen != null) {
                    Log.d("AppDetailViewModel", "Installing via variant from ${chosen.repositoryName}")
                    installer.install(chosen)
                } else {
                    Log.d("AppDetailViewModel", "No variant match; installing via app metadata URL")
                    installer.install(app)
                }

                if (success) {
                    _ui.update { it.copy(isInstalling = false, progress = 1f, stage = TaskStage.Finished(true)) }
                } else {
                    _ui.update { it.copy(isInstalling = false, error = "Installation failed", stage = TaskStage.Finished(false)) }
                }

                delay(1000)
                val newInstalled = installedRepo.getVersionCode(packageName)
                _ui.update { it.copy(installedVersionCode = newInstalled) }

            } catch (e: Exception) {
                _ui.update { it.copy(isInstalling = false, error = "Install failed: ${e.message}", stage = TaskStage.Finished(false)) }
            }
        }
    }

    fun openApp() = installer.open(packageName)
    fun cancel() = installer.cancel(packageName)

    fun uninstall() {
        installer.uninstall(packageName)
        viewModelScope.launch {
            delay(1000)
            val newInstalled = installedRepo.getVersionCode(packageName)
            _ui.update { it.copy(installedVersionCode = newInstalled) }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch(Dispatchers.IO) {
            settings.toggleFavorite(packageName)
        }
    }

    fun installVariant(variant: AppVariant) {
        viewModelScope.launch {
            _ui.update { it.copy(isInstalling = true, progress = 0f, error = null, stage = TaskStage.Downloading(0f)) }
            try {
                settings.updateAppUpdatePreference(packageName) {
                    it.copy(
                        preferredRepoUrl = variant.repositoryUrl.trim().trimEnd('/'),
                        lockToRepo = true
                    )
                }
                val ok = installer.install(variant)
                _ui.update {
                    it.copy(
                        isInstalling = false,
                        stage = TaskStage.Finished(ok),
                        progress = if (ok) 1f else it.progress,
                        error = if (ok) null else "Installation failed"
                    )
                }
                delay(1000)
                val newInstalled = installedRepo.getVersionCode(packageName)
                _ui.update { it.copy(installedVersionCode = newInstalled) }
            } catch (e: Exception) {
                _ui.update { it.copy(isInstalling = false, error = "Install failed: ${e.message}", stage = TaskStage.Finished(false)) }
            }
        }
    }
}