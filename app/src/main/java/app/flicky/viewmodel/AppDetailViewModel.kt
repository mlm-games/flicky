package app.flicky.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.flicky.data.local.AppDao
import app.flicky.data.model.FDroidApp
import app.flicky.data.repository.InstalledAppsRepository
import app.flicky.install.Installer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DetailUiState(
    val app: FDroidApp? = null,
    val installedVersionCode: Long? = null,
    val isInstalling: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null
)

class AppDetailViewModel(
    private val dao: AppDao,
    private val installedRepo: InstalledAppsRepository,
    private val installer: Installer,
    private val packageName: String
) : ViewModel() {

    private val _ui = MutableStateFlow(DetailUiState())
    val ui: StateFlow<DetailUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeAll().collect { list ->
                val app = list.find { it.packageName == packageName }
                val installed = installedRepo.getVersionCode(packageName)
                _ui.value = _ui.value.copy(app = app, installedVersionCode = installed)
            }
        }
    }

    fun install() {
        val app = _ui.value.app ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isInstalling = true, progress = 0f, error = null)
            val ok = installer.install(app) { p ->
                _ui.value = _ui.value.copy(progress = p)
            }
            val newInstalled = installedRepo.getVersionCode(packageName)
            _ui.value = _ui.value.copy(isInstalling = false, installedVersionCode = newInstalled)
            if (!ok) _ui.value = _ui.value.copy(error = "Install failed")
        }
    }

    fun openApp() {
        installer.open(packageName)
    }

    fun uninstall() {
        installer.uninstall(packageName)
        val newInstalled = installedRepo.getVersionCode(packageName)
        _ui.value = _ui.value.copy(installedVersionCode = newInstalled)
    }
}