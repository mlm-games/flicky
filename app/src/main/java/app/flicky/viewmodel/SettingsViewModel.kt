package app.flicky.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.flicky.AppGraph
import app.flicky.R
import app.flicky.data.model.RepositoryInfo
import app.flicky.data.repository.AppSettings
import app.flicky.data.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        repo.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val repositories: StateFlow<List<RepositoryInfo>> =
        repo.repositoriesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
                AppGraph.clearAllCaches()
                _events.emit(UiEvent.Toast(R.string.cache_cleared))
                // immediately resync fresh
                runCatching { AppGraph.syncManager.syncAll(force = true) }
            }
            "exportSettings" -> _events.emit(UiEvent.RequestExport)
            else -> _events.emit(UiEvent.Toast(R.string.no_action_attached))
        }
    }

    fun resetRepositoriesToDefaults() = viewModelScope.launch {
        repo.resetRepositoriesToDefaults()
        _events.emit(UiEvent.Toast(R.string.repositories_reset))
    }

    sealed class UiEvent {
        data object RequestExport : UiEvent()
        data class Toast(@param:StringRes val messageResId: Int) : UiEvent()
    }
}