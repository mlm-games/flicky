package app.flicky.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import app.flicky.R
import app.flicky.data.local.RepoConfig
import app.flicky.data.model.RepositoryInfo
import app.flicky.data.repository.AppSettings
import app.flicky.data.repository.SettingsRepository
import app.flicky.di.AppDependencies
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import io.github.mlmgames.settings.core.backup.ExportResult
import io.github.mlmgames.settings.core.backup.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        repo.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val repositories: StateFlow<List<RepositoryInfo>> =
        repo.repositoriesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val _configsVersion = MutableStateFlow(0)

    fun reloadConfigs() {
        _configsVersion.value++
    }

    val repositoriesWithConfigs: StateFlow<List<Pair<RepositoryInfo, RepoConfig>>> =
        combine(
            repo.repositoriesFlow,
            _configsVersion
        ) { repos, _ ->
            withContext(Dispatchers.IO) {
                val dao = AppDependencies.db.repoConfigDao()
                repos.map { r ->
                    val base = r.url.trimEnd('/')
                    val config = dao.get(base) ?: RepoConfig(
                        baseUrl = base,
                        enabled = r.enabled
                    )
                    r to config
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun updateSetting(propertyName: String, value: Any) = viewModelScope.launch {
        repo.updateSetting(propertyName, value)
    }

    fun toggleRepository(url: String) = viewModelScope.launch {
        AppDependencies.syncManager.cancelCurrentSync()
        repo.toggleRepository(url)
    }

    fun addRepository(name: String, url: String) = viewModelScope.launch {
        repo.addRepository(name, url)
    }

    fun deleteRepository(url: String) = viewModelScope.launch {
        repo.deleteRepository(url)
    }

    fun performAction(propertyName: String) = viewModelScope.launch {
        when (propertyName) {
            "clearCache" -> {
                clearAllCaches()
                _events.emit(UiEvent.Toast(R.string.cache_cleared))
                runCatching { AppDependencies.syncManager.syncAll(force = true) }
            }
            "exportSettings" -> _events.emit(UiEvent.RequestExport)
            "supportDevelopment" -> _events.emit(UiEvent.OpenUrl("https://ko-fi.com/mlmgames"))
            else -> _events.emit(UiEvent.Toast(R.string.no_action_attached))
        }
    }

    fun resetRepositoriesToDefaults() = viewModelScope.launch {
        repo.resetRepositoriesToDefaults()
        _events.emit(UiEvent.Toast(R.string.repositories_reset))
    }

    suspend fun exportSettings(): ExportResult = repo.exportSettings()

    suspend fun importSettings(json: String): ImportResult = repo.importSettings(json)

    @OptIn(ExperimentalCoilApi::class)
    private suspend fun clearAllCaches() = withContext(Dispatchers.IO) {
        AppDependencies.syncManager.cancelCurrentSync()

        AppDependencies.db.withTransaction {
            AppDependencies.db.appDao().clear()
            AppDependencies.db.appDao().clearVariants()
            AppDependencies.db.repositoryDao().clearAll()
        }
        AppDependencies.headersStore.clear()

        runCatching {
            repo.repositoriesFlow.first().forEach {
                app.flicky.data.remote.MirrorRegistry.clear(it.url)
            }
        }

        runCatching {
            val loader = Coil.imageLoader(AppDependencies.context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }

        runCatching { AppDependencies.installer.clearDownloadCache() }
    }

    sealed class UiEvent {
        data object RequestExport : UiEvent()
        data class OpenUrl(val url: String) : UiEvent()
        data class Toast(@param:StringRes val messageResId: Int) : UiEvent()
    }
}