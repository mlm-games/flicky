package app.flicky.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import app.flicky.data.model.RepositoryInfo
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

private val Context.ds by preferencesDataStore("flicky.settings")

/**
 * Definition for a setting that maps between AppSettings property and DataStore key
 */
sealed class SettingDefinition<T> {
    abstract val key: Preferences.Key<T>
    abstract val getValue: (AppSettings) -> T
    abstract val propertyName: String

    data class BooleanSetting(
        override val propertyName: String,
        override val key: Preferences.Key<Boolean>,
        override val getValue: (AppSettings) -> Boolean
    ) : SettingDefinition<Boolean>()

    data class IntSetting(
        override val propertyName: String,
        override val key: Preferences.Key<Int>,
        override val getValue: (AppSettings) -> Int
    ) : SettingDefinition<Int>()

    data class FloatSetting(
        override val propertyName: String,
        override val key: Preferences.Key<Float>,
        override val getValue: (AppSettings) -> Float
    ) : SettingDefinition<Float>()

    data class StringSetting(
        override val propertyName: String,
        override val key: Preferences.Key<String>,
        override val getValue: (AppSettings) -> String
    ) : SettingDefinition<String>()

    data class LongSetting(
        override val propertyName: String,
        override val key: Preferences.Key<Long>,
        override val getValue: (AppSettings) -> Long
    ) : SettingDefinition<Long>()
}

class SettingsRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    companion object {
        // Appearance
        val THEME_MODE = intPreferencesKey("theme_mode")
        val DYNAMIC_THEME = booleanPreferencesKey("dynamic_theme")
        val COMPACT_MODE = booleanPreferencesKey("compact_mode")
        val SHOW_APP_ICONS = booleanPreferencesKey("show_app_icons")

        // General
        val DEFAULT_SORT = intPreferencesKey("default_sort")
        val APPS_PER_ROW = intPreferencesKey("apps_per_row")

        val AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val SYNC_INTERVAL = intPreferencesKey("sync_interval_idx")
        val NOTIFY_UPDATES = booleanPreferencesKey("notify_updates")
        val KEEP_CACHE = booleanPreferencesKey("keep_cache")

        val HIDE_ANTI = booleanPreferencesKey("hide_anti_features")
        val SHOW_INCOMPATIBLE = booleanPreferencesKey("show_incompatible")
        val UNSTABLE_UPDATES = booleanPreferencesKey("unstable_updates")
        val IGNORE_SIGNATURE = booleanPreferencesKey("ignore_signature")

        val USE_PROXY = booleanPreferencesKey("use_proxy")
        val PROXY_TYPE = intPreferencesKey("proxy_type")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = intPreferencesKey("proxy_port")

        val LAST_SYNC = longPreferencesKey("last_sync")

        val REPOS_JSON = stringPreferencesKey("repos_json")
        val REPO_HEADERS = stringPreferencesKey("repo_headers_json")
    }

    /**
     * Single source of truth mapping AppSettings <-> DataStore
     */
    private val definitions: Map<String, SettingDefinition<*>> = mapOf(
        "themeMode" to SettingDefinition.IntSetting("themeMode", THEME_MODE) { it.themeMode },
        "dynamicTheme" to SettingDefinition.BooleanSetting("dynamicTheme", DYNAMIC_THEME) { it.dynamicTheme },
        "compactMode" to SettingDefinition.BooleanSetting("compactMode", COMPACT_MODE) { it.compactMode },
        "showAppIcons" to SettingDefinition.BooleanSetting("showAppIcons", SHOW_APP_ICONS) { it.showAppIcons },

        "defaultSort" to SettingDefinition.IntSetting("defaultSort", DEFAULT_SORT) { it.defaultSort },
        "appsPerRow" to SettingDefinition.IntSetting("appsPerRow", APPS_PER_ROW) { it.appsPerRow },

        "autoUpdate" to SettingDefinition.BooleanSetting("autoUpdate", AUTO_UPDATE) { it.autoUpdate },
        "wifiOnly" to SettingDefinition.BooleanSetting("wifiOnly", WIFI_ONLY) { it.wifiOnly },
        "syncIntervalIndex" to SettingDefinition.IntSetting("syncIntervalIndex", SYNC_INTERVAL) { it.syncIntervalIndex },
        "notifyUpdates" to SettingDefinition.BooleanSetting("notifyUpdates", NOTIFY_UPDATES) { it.notifyUpdates },
        "keepCache" to SettingDefinition.BooleanSetting("keepCache", KEEP_CACHE) { it.keepCache },

        "hideAntiFeatures" to SettingDefinition.BooleanSetting("hideAntiFeatures", HIDE_ANTI) { it.hideAntiFeatures },
        "showIncompatible" to SettingDefinition.BooleanSetting("showIncompatible", SHOW_INCOMPATIBLE) { it.showIncompatible },
        "unstableUpdates" to SettingDefinition.BooleanSetting("unstableUpdates", UNSTABLE_UPDATES) { it.unstableUpdates },
        "ignoreSignature" to SettingDefinition.BooleanSetting("ignoreSignature", IGNORE_SIGNATURE) { it.ignoreSignature },

        "useProxy" to SettingDefinition.BooleanSetting("useProxy", USE_PROXY) { it.useProxy },
        "proxyType" to SettingDefinition.IntSetting("proxyType", PROXY_TYPE) { it.proxyType },
        "proxyHost" to SettingDefinition.StringSetting("proxyHost", PROXY_HOST) { it.proxyHost },
        "proxyPort" to SettingDefinition.IntSetting("proxyPort", PROXY_PORT) { it.proxyPort },

        "lastSync" to SettingDefinition.LongSetting("lastSync", LAST_SYNC) { it.lastSync }
    )

    /**
     * Flow for typing
     */
    val settingsFlow: Flow<AppSettings> = context.ds.data.map { p ->
        AppSettings(
            defaultSort = p[DEFAULT_SORT] ?: 1,
            appsPerRow = p[APPS_PER_ROW] ?: 4,

            themeMode = p[THEME_MODE] ?: 2,
            dynamicTheme = p[DYNAMIC_THEME] ?: false,
            compactMode = p[COMPACT_MODE] ?: false,
            showAppIcons = p[SHOW_APP_ICONS] ?: true,

            autoUpdate = p[AUTO_UPDATE] ?: false,
            wifiOnly = p[WIFI_ONLY] ?: true,
            syncIntervalIndex = p[SYNC_INTERVAL] ?: 1,
            notifyUpdates = p[NOTIFY_UPDATES] ?: true,
            keepCache = p[KEEP_CACHE] ?: false,

            hideAntiFeatures = p[HIDE_ANTI] ?: false,
            showIncompatible = p[SHOW_INCOMPATIBLE] ?: false,
            unstableUpdates = p[UNSTABLE_UPDATES] ?: false,
            ignoreSignature = p[IGNORE_SIGNATURE] ?: false,

            useProxy = p[USE_PROXY] ?: false,
            proxyType = p[PROXY_TYPE] ?: 0,
            clearCache = false,
            exportSettings = false,
            importSettings = false,

            lastSync = p[LAST_SYNC] ?: 0L,
            proxyHost = p[PROXY_HOST] ?: "",
            proxyPort = p[PROXY_PORT] ?: 9050
        )
    }.distinctUntilChanged()

    /**
     * Flow of repositories persisted as JSON
     */
    val repositoriesFlow: Flow<List<RepositoryInfo>> = context.ds.data.map { p ->
        p[REPOS_JSON]?.let {
            runCatching { json.decodeFromString<List<RepositoryInfo>>(it) }.getOrNull()
        } ?: RepositoryInfo.defaults()
    }.distinctUntilChanged()

    /**
     * Generic update using property name and dynamic mapping
     */
    suspend fun updateSetting(propertyName: String, value: Any) {
        val def = definitions[propertyName]
        if (def == null) {
            // Unknown mapped setting name; ignore silently to avoid crashes
            return
        }
        context.ds.edit { prefs ->
            when (def) {
                is SettingDefinition.BooleanSetting -> prefs[def.key] = (value as? Boolean) ?: return@edit
                is SettingDefinition.IntSetting -> prefs[def.key] = (value as? Int) ?: return@edit
                is SettingDefinition.FloatSetting -> prefs[def.key] = (value as? Float) ?: return@edit
                is SettingDefinition.StringSetting -> prefs[def.key] = (value as? String) ?: return@edit
                is SettingDefinition.LongSetting -> prefs[def.key] = (value as? Long) ?: return@edit
            }
        }
    }

    /**
     * Batch update that computes diffs and writes only changed keys
     */
    suspend fun updateSettings(update: (AppSettings) -> AppSettings) {
        val current = settingsFlow.first()
        val updated = update(current)
        context.ds.edit { prefs ->
            definitions.values.forEach { def ->
                when (def) {
                    is SettingDefinition.BooleanSetting -> {
                        val old = def.getValue(current)
                        val new = def.getValue(updated)
                        if (old != new) prefs[def.key] = new
                    }
                    is SettingDefinition.IntSetting -> {
                        val old = def.getValue(current)
                        val new = def.getValue(updated)
                        if (old != new) prefs[def.key] = new
                    }
                    is SettingDefinition.FloatSetting -> {
                        val old = def.getValue(current)
                        val new = def.getValue(updated)
                        if (old != new) prefs[def.key] = new
                    }
                    is SettingDefinition.StringSetting -> {
                        val old = def.getValue(current)
                        val new = def.getValue(updated)
                        if (old != new) prefs[def.key] = new
                    }
                    is SettingDefinition.LongSetting -> {
                        val old = def.getValue(current)
                        val new = def.getValue(updated)
                        if (old != new) prefs[def.key] = new
                    }
                }
            }
        }
    }

    /**
     * Repository helpers
     */
    suspend fun setRepositories(list: List<RepositoryInfo>) {
        context.ds.edit { it[REPOS_JSON] = json.encodeToString(list) }
    }

    suspend fun toggleRepository(url: String) {
        val repos = repositoriesFlow.first()
        val updated = repos.map { if (it.url == url) it.copy(enabled = !it.enabled) else it }
        setRepositories(updated)
    }

    suspend fun addRepository(name: String, url: String) {
        val repos = repositoriesFlow.first().toMutableList()
        if (repos.none { it.url.equals(url, ignoreCase = true) }) {
            repos.add(RepositoryInfo(name = name.ifBlank { url }, url = url, enabled = true))
            setRepositories(repos)
        }
    }

    suspend fun deleteRepository(url: String) {
        val repos = repositoriesFlow.first().filterNot { it.url == url }
        setRepositories(repos)
    }

    /**
     * Meta
     */
    suspend fun setLastSync(millis: Long) {
        context.ds.edit { it[LAST_SYNC] = millis }
    }

    /**
     * Repo headers persistence for conditional requests
     */
    suspend fun setRepoHeadersMap(mapJson: String) {
        context.ds.edit { it[REPO_HEADERS] = mapJson }
    }

    suspend fun getRepoHeadersMap(): String {
        return context.ds.data.first()[REPO_HEADERS] ?: "{}"
    }

    /**
     * Convenience helpers (used by UI actions)
     */
    suspend fun clearCache() {
        // Clears cached repo headers + resets lastSync (download cache is cleared elsewhere if required)
        context.ds.edit {
            it[REPO_HEADERS] = "{}"
            it[LAST_SYNC] = 0L
        }
    }
}