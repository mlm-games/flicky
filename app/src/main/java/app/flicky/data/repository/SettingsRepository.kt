package app.flicky.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.flicky.data.local.AppDao
import app.flicky.data.local.RepoConfig
import app.flicky.data.local.RepoConfigDao
import app.flicky.data.local.RepositoryDao
import app.flicky.data.local.RepositoryEntity
import app.flicky.data.model.RepositoryInfo
import app.flicky.data.remote.MirrorRegistry
import io.github.mlmgames.settings.core.SettingsRepository as KmpSettingsRepository
import io.github.mlmgames.settings.core.backup.DeviceInfo
import io.github.mlmgames.settings.core.backup.ExportResult
import io.github.mlmgames.settings.core.backup.ImportResult
import io.github.mlmgames.settings.core.backup.SettingsBackupManager
import io.github.mlmgames.settings.core.backup.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val repositoryDao: RepositoryDao,
    private val repoConfigDao: RepoConfigDao,
    private val appDao: AppDao,
) {
    private val repo = KmpSettingsRepository(dataStore, AppSettingsSchema)

    private val backupManager = SettingsBackupManager(
        dataStore = dataStore,
        schema = AppSettingsSchema,
        appId = "app.flicky",
        schemaVersion = 5,
        deviceInfoProvider = {
            DeviceInfo(
                platform = "Android",
                osVersion = android.os.Build.VERSION.RELEASE,
                appVersion = app.flicky.BuildConfig.VERSION_NAME,
//                deviceModel = android.os.Build.MODEL,
//                locale = Locale.getDefault().toLanguageTag()
            )
        }
    )

    val settingsFlow: Flow<AppSettings> = repo.flow

    val repositoriesFlow: Flow<List<RepositoryInfo>> =
        repositoryDao.observeWithConfig()
            .map { rows ->
                val defaultNames = RepositoryInfo.defaults().associate {
                    normalizeUrl(it.url) to it.name
                }
                rows.map { r ->
                    val normalizedUrl = normalizeUrl(r.baseUrl)
                    RepositoryInfo(
                        name = r.name.ifBlank { defaultNames[normalizedUrl] ?: normalizedUrl },
                        url = normalizedUrl,
                        enabled = r.enabled,
                        signingKey = r.fingerprint,
                        rotateMirrors = r.rotateMirrors
                    )
                }
            }
            .distinctUntilChanged()

    suspend fun updateSetting(propertyName: String, value: Any) {
        repo.set(propertyName, value)
    }

    suspend fun updateSettings(update: (AppSettings) -> AppSettings) {
        repo.update(update)
    }

    suspend fun toggleRepository(url: String) {
        val base = normalizeUrl(url)
        val existing = repoConfigDao.get(base)
        val newEnabled = !(existing?.enabled ?: true)
        repoConfigDao.upsert((existing ?: RepoConfig(baseUrl = base)).copy(enabled = newEnabled))
        if (!newEnabled) {
            appDao.deleteByRepositoryUrl(base)
            appDao.deleteVariantsByRepositoryUrl(base)
            MirrorRegistry.clear(base)
        }
    }

    suspend fun addRepository(
        name: String,
        url: String,
        trustMode: String = "HttpsOnly",
    ) {
        val base = normalizeUrl(url)

        repositoryDao.upsert(
            RepositoryEntity(
                baseUrl = base,
                name = name.ifBlank { base }
            )
        )

        val isFDroid =
            base.equals("https://f-droid.org/repo", ignoreCase = true) ||
            name.equals("F-Droid", ignoreCase = true) ||
            base.contains("f-droid.org", ignoreCase = true)

        val existing = repoConfigDao.get(base)

        repoConfigDao.upsert(
            (existing ?: RepoConfig(
                baseUrl = base,
                enabled = true,
                rotateMirrors = isFDroid,
                strategy = if (isFDroid) "RoundRobin" else "StickyLastGood"
            )).copy(
                enabled = true,
                trustMode = trustMode
            )
        )
    }

    suspend fun upsertRepoConfig(config: RepoConfig) {
        repoConfigDao.upsert(config)
    }

    suspend fun deleteRepository(url: String) {
        val base = normalizeUrl(url)
        repositoryDao.delete(base)
        repoConfigDao.delete(base)
        appDao.deleteByRepositoryUrl(base)
        appDao.deleteVariantsByRepositoryUrl(base)
        MirrorRegistry.clear(base)
    }

    suspend fun resetRepositoriesToDefaults() = withContext(Dispatchers.IO) {
        repositoryDao.clearAll()
        repoConfigDao.clearAll()
        RepositoryInfo.defaults().forEach { def ->
            val base = normalizeUrl(def.url)
            repositoryDao.upsert(
                RepositoryEntity(
                    baseUrl = base,
                    name = def.name
                )
            )
            val isFDroid = base.equals("https://f-droid.org/repo", ignoreCase = true)

            repoConfigDao.insertIgnore(
                RepoConfig(
                    baseUrl = base,
                    enabled = def.enabled,
                    rotateMirrors = isFDroid,
                    strategy = if (isFDroid) "RoundRobin" else "StickyLastGood"
                )
            )
        }
    }

    suspend fun migrateLegacyProxySettingsIfNeeded() {
        repo.update { settings ->
            if (!settings.useProxy) return@update settings
            if (settings.proxyUrl.isNotBlank()) return@update settings
            if (settings.proxyHost.isBlank()) return@update settings

            val scheme = if (settings.proxyType == 1) "socks5" else "http"
            val port = settings.proxyPort.coerceIn(1, 65535)

            settings.copy(
                proxyUrl = "$scheme://${settings.proxyHost.trim()}:$port"
            )
        }
    }

    fun observeFavorites(): Flow<Set<String>> =
        settingsFlow.map { it.favoritePackages }.distinctUntilChanged()

    fun observeIsFavorite(packageName: String): Flow<Boolean> =
        observeFavorites().map { it.contains(packageName) }

    suspend fun isFavorite(packageName: String): Boolean =
        settingsFlow.first().favoritePackages.contains(packageName)

    suspend fun setFavorite(packageName: String, favorite: Boolean) {
        repo.update { settings ->
            val current = settings.favoritePackages.toMutableSet()
            if (favorite) {
                current.add(packageName)
            } else {
                current.remove(packageName)
            }
            settings.copy(favoritePackages = current)
        }
    }

    suspend fun toggleFavorite(packageName: String): Boolean {
        val isFav = isFavorite(packageName)
        setFavorite(packageName, !isFav)
        return !isFav
    }

    private fun parseUpdatePrefs(json: String): AppUpdatePreferencesMap =
        AppUpdatePreferencesMap.fromJson(json)

    fun observeAppUpdatePreferences(): Flow<AppUpdatePreferencesMap> =
        settingsFlow.map { parseUpdatePrefs(it.appUpdatePrefsJson) }.distinctUntilChanged()

    fun observeAppUpdatePreference(packageName: String): Flow<AppUpdatePreference> =
        observeAppUpdatePreferences().map { it[packageName] }

    suspend fun getAppUpdatePreference(packageName: String): AppUpdatePreference {
        val json = settingsFlow.first().appUpdatePrefsJson
        return parseUpdatePrefs(json)[packageName]
    }

    suspend fun setAppUpdatePreference(packageName: String, pref: AppUpdatePreference) {
        repo.update { settings ->
            val current = parseUpdatePrefs(settings.appUpdatePrefsJson)
            val updated = current.with(packageName, pref)
            settings.copy(appUpdatePrefsJson = AppUpdatePreferencesMap.toJson(updated))
        }
    }

    suspend fun updateAppUpdatePreference(
        packageName: String,
        update: (AppUpdatePreference) -> AppUpdatePreference
    ) {
        val current = getAppUpdatePreference(packageName)
        setAppUpdatePreference(packageName, update(current))
    }

    suspend fun setPreferredRepo(packageName: String, repoUrl: String?, lock: Boolean = true) {
        updateAppUpdatePreference(packageName) {
            it.copy(preferredRepoUrl = repoUrl, lockToRepo = lock)
        }
    }

    suspend fun exportSettings(): ExportResult = backupManager.export()

    suspend fun importSettings(json: String): ImportResult = backupManager.import(json)

    suspend fun validateBackup(json: String): ValidationResult = backupManager.validate(json)

    suspend fun setLastSync(millis: Long) {
        repo.set("lastSync", millis)
    }

    suspend fun setRepoHeadersMap(mapJson: String) {
        repo.set("repoHeadersJson", mapJson)
    }

    suspend fun getRepoHeadersMap(): String =
        repo.get<String>("repoHeadersJson") ?: "{}"

    suspend fun setLastQuery(q: String) {
        repo.set("lastQuery", q)
    }

    suspend fun getLastQuery(): String =
        repo.get<String>("lastQuery") ?: ""

    suspend fun clearCache() {
        repo.update {
            it.copy(
                repoHeadersJson = "{}",
                lastSync = 0L
            )
        }
    }

    fun normalizeUrl(url: String): String = url.trim().trimEnd('/')

    suspend fun dismissAlertBanner(bannerId: String) {
        repo.update { settings ->
            settings.copy(dismissedAlertBannerIds = settings.dismissedAlertBannerIds + bannerId)
        }
    }
}