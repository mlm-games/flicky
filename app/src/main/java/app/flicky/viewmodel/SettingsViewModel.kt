package app.flicky.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.flicky.data.model.RepositoryInfo
import app.flicky.data.repository.AppSettings
import app.flicky.data.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        repo.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val repositories: StateFlow<List<RepositoryInfo>> =
        repo.repositoriesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, RepositoryInfo.defaults())

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    fun updateSetting(propertyName: String, value: Any) = viewModelScope.launch {
        repo.updateSetting(propertyName, value)
    }

    fun toggleRepository(url: String) = viewModelScope.launch { repo.toggleRepository(url) }
    fun addRepository(name: String, url: String) = viewModelScope.launch { repo.addRepository(name, url) }
    fun deleteRepository(url: String) = viewModelScope.launch { repo.deleteRepository(url) }

    fun performAction(propertyName: String) = viewModelScope.launch {
        when (propertyName) {
            "clearCache" -> {
                repo.clearCache()
                _events.emit(UiEvent.Toast("Cache cleared"))
            }
            "exportSettings" -> {
                _events.emit(UiEvent.RequestExport)
            }
            "importSettings" -> {
                _events.emit(UiEvent.RequestImport)
            }
            else -> _events.emit(UiEvent.Toast("No action attached"))
        }
    }

    sealed class UiEvent {
        data object RequestExport : UiEvent()
        data object RequestImport : UiEvent()
        data class Toast(val message: String) : UiEvent()
    }
}