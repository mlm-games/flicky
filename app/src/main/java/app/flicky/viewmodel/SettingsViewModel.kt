package app.flicky.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.flicky.data.model.RepositoryInfo
import app.flicky.data.repository.SettingsRepository
import app.flicky.data.settings.AppSettings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        repo.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val repositories: StateFlow<List<RepositoryInfo>> =
        repo.repositoriesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, RepositoryInfo.defaults())

    fun update(propertyName: String, value: Any) = viewModelScope.launch { repo.updateSetting(propertyName, value) }
    fun toggleRepository(url: String) = viewModelScope.launch { repo.toggleRepo(url) }
    fun addRepository(name: String, url: String) = viewModelScope.launch {
        val repos = repositories.value.toMutableList()
        repos.add(RepositoryInfo(name = name, url = url, enabled = true))
        repo.setRepositories(repos)
    }
}