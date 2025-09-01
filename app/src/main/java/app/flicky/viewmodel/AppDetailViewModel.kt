package app.flicky.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.flicky.data.local.AppDao
import app.flicky.data.model.FDroidApp
import app.flicky.data.repository.InstalledAppsRepository
import app.flicky.data.repository.SettingsRepository
import app.flicky.data.repository.PreferredRepo
import app.flicky.data.repository.VariantSelector
import app.flicky.install.Installer
import app.flicky.install.TaskStage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DetailUiState(
    val app: FDroidApp? = null,
    val installedVersionCode: Long? = null,
    val isInstalling: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val stage: TaskStage? = null
)

class AppDetailViewModel(
    private val dao: AppDao,
    private val installedRepo: InstalledAppsRepository,
    private val installer: Installer,
    private val settings: SettingsRepository,
    private val packageName: String
) : ViewModel() {

    private val _ui = MutableStateFlow(DetailUiState())
    val ui: StateFlow<DetailUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeOne(packageName).collect { app ->
                val installed = installedRepo.getVersionCode(packageName)
                _ui.value = _ui.value.copy(app = app, installedVersionCode = installed)
            }
        }
        viewModelScope.launch {
            installer.tasks.collect { map ->
                when (val stage = map[packageName]) {
                    is TaskStage.Downloading -> _ui.update {
                        it.copy(
                            isInstalling = true,
                            stage = stage,
                            progress = (0.99f * stage.progress).coerceIn(0f, 0.99f),
                            error = null
                        )
                    }
                    is TaskStage.Verifying -> _ui.update {
                        it.copy(
                            isInstalling = true,
                            stage = stage,
                            progress = 0.995f,
                            error = null
                        )
                    }
                    is TaskStage.Installing -> _ui.update {
                        it.copy(
                            isInstalling = true,
                            stage = stage,
                            progress = (0.99f + 0.01f * stage.progress).coerceIn(0.99f, 1f),
                            error = null
                        )
                    }
                    is TaskStage.Finished -> {
                        _ui.update {
                            it.copy(
                                isInstalling = false,
                                stage = stage,
                                progress = if (stage.success) 1f else it.progress,
                                error = if (stage.success) null else "Installation failed"
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
    }

    fun install() {
        val app = _ui.value.app ?: return

        viewModelScope.launch {
            _ui.value = _ui.value.copy(isInstalling = true, progress = 0f, error = null, stage = TaskStage.Downloading(0f))

            try {
                Log.d("AppDetailViewModel", "Starting install for ${app.packageName}")

                val prefIdx = settings.settingsFlow.first().preferredRepo
                val pref = PreferredRepo.fromIndex(prefIdx)
                val variants = dao.variantsFor(app.packageName)
                val chosen = VariantSelector.pick(variants, pref)

                val success = if (chosen != null) {
                    Log.d("AppDetailViewModel", "Installing via variant from ${chosen.repositoryName} (${chosen.repositoryUrl}) with vercode: ${chosen.versionCode}")
                    installer.install(chosen) { p -> _ui.value = _ui.value.copy(progress = p) }
                } else {
                    Log.d("AppDetailViewModel", "No variant match; installing via app metadata URL")
                    installer.install(app) { p -> _ui.value = _ui.value.copy(progress = p) }
                }

                if (success) {
                    _ui.value = _ui.value.copy(isInstalling = false, progress = 1f, stage = TaskStage.Finished(true))
                } else {
                    _ui.value = _ui.value.copy(isInstalling = false, error = "Installation failed", stage = TaskStage.Finished(false))
                }

                delay(1000)
                val newInstalled = installedRepo.getVersionCode(packageName)
                _ui.value = _ui.value.copy(installedVersionCode = newInstalled)

            } catch (e: Exception) {
                _ui.value = _ui.value.copy(isInstalling = false, error = "Install failed: ${e.message}", stage = TaskStage.Finished(false))
            }
        }
    }

    fun openApp() = installer.open(packageName)

    fun uninstall() {
        installer.uninstall(packageName)
        viewModelScope.launch {
            delay(1000)
            val newInstalled = installedRepo.getVersionCode(packageName)
            _ui.value = _ui.value.copy(installedVersionCode = newInstalled)
        }
    }
}