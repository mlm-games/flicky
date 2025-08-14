package app.flicky.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import app.flicky.data.model.RepositoryInfo
import app.flicky.data.settings.AppSettings
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.ds by preferencesDataStore("flicky.settings")

class SettingsRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val DEFAULT_SORT = intPreferencesKey("default_sort")
        val AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val SYNC_INTERVAL = intPreferencesKey("sync_interval_idx")
        val HIDE_ANTI = booleanPreferencesKey("hide_anti_features")

        val REPOS_JSON = stringPreferencesKey("repos_json")
        val LAST_SYNC = longPreferencesKey("last_sync")
        val REPO_HEADERS = stringPreferencesKey("repo_headers_json")
    }

    val settingsFlow: Flow<AppSettings> = context.ds.data.map { p ->
        AppSettings(
            themeMode = p[THEME_MODE] ?: 2,
            defaultSort = p[DEFAULT_SORT] ?: 1,
            autoUpdate = p[AUTO_UPDATE] ?: false,
            wifiOnly = p[WIFI_ONLY] ?: true,
            syncIntervalIndex = p[SYNC_INTERVAL] ?: 1,
            hideAntiFeatures = p[HIDE_ANTI] ?: false
        )
    }.distinctUntilChanged()

    val repositoriesFlow: Flow<List<RepositoryInfo>> = context.ds.data.map { p ->
        p[REPOS_JSON]?.let {
            runCatching { json.decodeFromString<List<RepositoryInfo>>(it) }.getOrNull()
        } ?: RepositoryInfo.defaults()
    }.distinctUntilChanged()

    suspend fun updateSetting(propertyName: String, value: Any) {
        context.ds.edit { prefs ->
            when (propertyName) {
                "themeMode" -> prefs[THEME_MODE] = value as Int
                "defaultSort" -> prefs[DEFAULT_SORT] = value as Int
                "autoUpdate" -> prefs[AUTO_UPDATE] = value as Boolean
                "wifiOnly" -> prefs[WIFI_ONLY] = value as Boolean
                "syncIntervalIndex" -> prefs[SYNC_INTERVAL] = value as Int
                "hideAntiFeatures" -> prefs[HIDE_ANTI] = value as Boolean
            }
        }
    }

    suspend fun setRepositories(list: List<RepositoryInfo>) {
        context.ds.edit { it[REPOS_JSON] = json.encodeToString(list) }
    }

    suspend fun toggleRepo(url: String) {
        val repos = repositoriesFlow.first()
        val updated = repos.map { if (it.url == url) it.copy(enabled = !it.enabled) else it }
        setRepositories(updated)
    }

    suspend fun setLastSync(millis: Long) {
        context.ds.edit { it[LAST_SYNC] = millis }
    }

    suspend fun setRepoHeadersMap(mapJson: String) {
        context.ds.edit { it[REPO_HEADERS] = mapJson }
    }

    suspend fun getRepoHeadersMap(): String {
        return context.ds.data.first()[REPO_HEADERS] ?: "{}"
    }
}