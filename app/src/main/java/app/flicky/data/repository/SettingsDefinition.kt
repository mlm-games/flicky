package app.flicky.data.repository

import io.github.mlmgames.settings.core.annotations.ActionHandler
import io.github.mlmgames.settings.core.annotations.CategoryDefinition
import io.github.mlmgames.settings.core.annotations.NoReset
import io.github.mlmgames.settings.core.annotations.Persisted
import io.github.mlmgames.settings.core.annotations.SchemaVersion
import io.github.mlmgames.settings.core.annotations.Setting
import io.github.mlmgames.settings.core.annotations.SettingAction
import io.github.mlmgames.settings.core.locale.AppLanguage
import io.github.mlmgames.settings.core.resources.SettingsTextKeys
import io.github.mlmgames.settings.core.types.Button
import io.github.mlmgames.settings.core.types.Dropdown
import io.github.mlmgames.settings.core.types.TextInput
import io.github.mlmgames.settings.core.types.Toggle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


@Serializable
data class AppUpdatePreference(
    val ignoreUpdates: Boolean = false,
    val ignoreVersionCode: Long = 0L,
    val preferredRepoUrl: String? = null,
    val lockToRepo: Boolean = true,
    val ignoreUnstable: Boolean? = null,
)

@Serializable
data class AppUpdatePreferencesMap(
    val prefs: Map<String, AppUpdatePreference> = emptyMap()
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(jsonString: String): AppUpdatePreferencesMap {
            return if (jsonString.isBlank() || jsonString == "{}") {
                AppUpdatePreferencesMap()
            } else {
                try {
                    json.decodeFromString<AppUpdatePreferencesMap>(jsonString)
                } catch (_: Exception) {
                    AppUpdatePreferencesMap()
                }
            }
        }

        fun toJson(map: AppUpdatePreferencesMap): String {
            return json.encodeToString(map)
        }
    }

    operator fun get(packageName: String): AppUpdatePreference = prefs[packageName] ?: AppUpdatePreference()

    fun with(packageName: String, pref: AppUpdatePreference): AppUpdatePreferencesMap {
        return copy(prefs = prefs + (packageName to pref))
    }

    fun without(packageName: String): AppUpdatePreferencesMap {
        return copy(prefs = prefs - packageName)
    }
}

@SchemaVersion(5)
data class AppSettings(
    @Setting(
        title = "Language",
        titleKey = SettingsTextKeys.LANGUAGE,
        category = Appearance::class,
        type = Dropdown::class,
        key = "language",
        languages = ["en", "ar", "cs", "de", "el", "es", "fa", "fi", "fr", "he", "hr", "hu", "id", "it", "ja", "ko", "nl", "pl", "pt", "ru", "sv", "tr", "uk", "vi", "zh-CN", "zh-TW"]
    )
    val language: AppLanguage = AppLanguage.System,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_THEME,
        description = "Choose between light, dark, or system theme",
        descriptionKey = FlickySettingsKeys.SETTING_THEME_DESCRIPTION,
        category = Appearance::class,
        type = Dropdown::class,
        options = ["System", "Light", "Dark"],
        optionsKey = FlickySettingsKeys.SETTING_THEME_OPTIONS,
    )
    val themeMode: Int = 2,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_DYNAMIC_COLORS,
        description = "Use Material You dynamic colors (Android 12+)",
        descriptionKey = FlickySettingsKeys.SETTING_DYNAMIC_COLORS_DESCRIPTION,
        category = Appearance::class,
        type = Toggle::class
    )
    val dynamicTheme: Boolean = false,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_SHOW_APP_ICONS,
        description = "Display app icons in lists and grids",
        descriptionKey = FlickySettingsKeys.SETTING_SHOW_APP_ICONS_DESCRIPTION,
        category = Appearance::class,
        type = Toggle::class
    )
    val showAppIcons: Boolean = true,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_USE_LIST_LAYOUT,
        description = "Show apps in a list instead of grid",
        descriptionKey = FlickySettingsKeys.SETTING_USE_LIST_LAYOUT_DESCRIPTION,
        category = Appearance::class,
        type = Toggle::class
    )
    val useListLayout: Boolean = false,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_DEFAULT_SORT,
        description = "How to sort apps by default",
        descriptionKey = FlickySettingsKeys.SETTING_DEFAULT_SORT_DESCRIPTION,
        category = General::class,
        type = Dropdown::class,
        options = ["Name", "Name (Z-A)", "Updated", "Size", "Added"],
        optionsKey = FlickySettingsKeys.SETTING_DEFAULT_SORT_OPTIONS,
    )
    val defaultSort: Int = 1,

    @Persisted(key = "reverse_sort")
    val reverseSort: Boolean = false,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_AUTO_UPDATE,
        description = "Automatically update apps in the background",
        descriptionKey = FlickySettingsKeys.SETTING_AUTO_UPDATE_DESCRIPTION,
        category = Downloads::class,
        type = Toggle::class
    )
    val autoUpdate: Boolean = false,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_WIFI_ONLY,
        description = "Only download and sync over Wi-Fi",
        descriptionKey = FlickySettingsKeys.SETTING_WIFI_ONLY_DESCRIPTION,
        category = Downloads::class,
        type = Toggle::class
    )
    val wifiOnly: Boolean = true,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_SYNC_INTERVAL,
        description = "How often to check for updates",
        descriptionKey = FlickySettingsKeys.SETTING_SYNC_INTERVAL_DESCRIPTION,
        category = Downloads::class,
        type = Dropdown::class,
        options = ["3 hours", "6 hours", "12 hours", "Daily", "Weekly", "Never"],
        optionsKey = FlickySettingsKeys.SETTING_SYNC_INTERVAL_OPTIONS,
        key = "sync_interval_idx"
    )
    val syncIntervalIndex: Int = 1,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_KEEP_CACHE,
        description = "Keep downloaded APKs after installation",
        descriptionKey = FlickySettingsKeys.SETTING_KEEP_CACHE_DESCRIPTION,
        category = Downloads::class,
        type = Toggle::class
    )
    val keepCache: Boolean = false,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_INSTALLER,
        description = "Method to use for installing apps",
        descriptionKey = FlickySettingsKeys.SETTING_INSTALLER_DESCRIPTION,
        category = Downloads::class,
        type = Dropdown::class,
        options = ["System", "Session", "Root", "Shizuku", "App Manager", "Dhizuku"],
        optionsKey = FlickySettingsKeys.SETTING_INSTALLER_OPTIONS,
    )
    val installerMode: Int = 1,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_FALLBACK_INSTALLER,
        description = "Used if the primary installer fails, set to None to disable",
        descriptionKey = FlickySettingsKeys.SETTING_FALLBACK_INSTALLER_DESCRIPTION,
        category = Downloads::class,
        type = Dropdown::class,
        options = ["None", "System", "Session", "Root", "Shizuku", "App Manager", "Dhizuku"],
        optionsKey = FlickySettingsKeys.SETTING_FALLBACK_INSTALLER_OPTIONS,
    )
    val fallbackInstallerMode: Int = 2,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_PREFERRED_REPO,
        description = "Prefer updates from specific repository",
        descriptionKey = FlickySettingsKeys.SETTING_PREFERRED_REPO_DESCRIPTION,
        category = Downloads::class,
        type = Dropdown::class,
        options = ["Auto", "F-Droid", "IzzyOnDroid"],
        optionsKey = FlickySettingsKeys.SETTING_PREFERRED_REPO_OPTIONS,
    )
    val preferredRepo: Int = 0,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_HIDE_ANTI,
        description = "Hide apps with anti-features",
        descriptionKey = FlickySettingsKeys.SETTING_HIDE_ANTI_FEATURES_DESCRIPTION,
        category = Filters::class,
        type = Toggle::class
    )
    val hideAntiFeatures: Boolean = false,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_SHOW_INCOMPATIBLE,
        description = "Show apps that are incompatible with your device",
        descriptionKey = FlickySettingsKeys.SETTING_SHOW_INCOMPATIBLE_DESCRIPTION,
        category = Filters::class,
        type = Toggle::class
    )
    val showIncompatible: Boolean = false,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_SHOW_REPRODUCIBLE,
        description = "Display reproducible build badges from IzzyOnDroid",
        descriptionKey = FlickySettingsKeys.SETTING_SHOW_REPRODUCIBLE_DESCRIPTION,
        category = Filters::class,
        type = Toggle::class
    )
    val showReproducibleBadges: Boolean = false,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_IGNORE_UNSTABLE,
        description = "Hide alpha and beta releases from updates",
        descriptionKey = FlickySettingsKeys.SETTING_IGNORE_UNSTABLE_DESCRIPTION,
        category = Filters::class,
        type = Toggle::class
    )
    val ignoreUnstable: Boolean = false,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_DIFFERENTIAL_SYNC,
        description = "Only fetch changes since last sync",
        descriptionKey = FlickySettingsKeys.SETTING_DIFFERENTIAL_SYNC_DESCRIPTION,
        category = Sync::class,
        type = Toggle::class
    )
    val differentialSync: Boolean = true,

    @Persisted
    val useEntryJson: Boolean = false,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_FAIL_ON_TRUST_ERRORS,
        description = "Strict SSL/TLS verification",
        descriptionKey = FlickySettingsKeys.SETTING_FAIL_ON_TRUST_ERRORS_DESCRIPTION,
        category = Sync::class,
        type = Toggle::class
    )
    val failOnTrustErrors: Boolean = false,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_USE_PROXY,
        description = "Route connections through a proxy",
        descriptionKey = FlickySettingsKeys.SETTING_USE_PROXY_DESCRIPTION,
        category = Proxy::class,
        type = Toggle::class
    )
    val useProxy: Boolean = false,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_PROXY_URL,
        description = "",
        category = Proxy::class,
        type = TextInput::class,
        dependsOn = "useProxy"
    )
    val proxyUrl: String = "http://10.2.2.2:8888",

    @Persisted
    val proxyType: Int = 0,

    @Persisted
    val proxyHost: String = "",

    @Persisted
    val proxyPort: Int = 9050,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_CLEAR_CACHE,
        description = "Clear all cached data and images",
        descriptionKey = FlickySettingsKeys.SETTING_CLEAR_CACHE_DESCRIPTION,
        category = Other::class,
        type = Button::class
    )
    @NoReset
    @ActionHandler(ClearCacheAction::class)
    val clearCache: Unit = Unit,

    @Setting(
        titleKey = FlickySettingsKeys.SETTING_SHOW_DEBUG_INFO,
        description = "Display debug information in the UI",
        descriptionKey = FlickySettingsKeys.SETTING_SHOW_DEBUG_INFO_DESCRIPTION,
        category = Other::class,
        type = Toggle::class
    )
    val showDebugInfo: Boolean = false,

    @Setting(
        title = "Support Development",
        titleKey = FlickySettingsKeys.SETTING_SUPPORT_DEVELOPMENT,
        description = "If you find this app useful, consider supporting its continued development",
        descriptionKey = FlickySettingsKeys.SETTING_SUPPORT_DEVELOPMENT_DESCRIPTION,
        category = Other::class,
        type = Button::class
    )
    @NoReset
    @ActionHandler(SupportDevelopmentAction::class)
    val supportDevelopment: Unit = Unit,

    // Non-UI persisted
    @Persisted
    val lastSync: Long = 0L,

    @Persisted(key = "repo_headers_json")
    val repoHeadersJson: String = "{}",

    @Persisted(key = "last_query")
    val lastQuery: String = "",

    @Persisted(key = "favorite_packages")
    val favoritePackages: Set<String> = emptySet(),

    @Persisted(key = "app_update_prefs_json")
    val appUpdatePrefsJson: String = "{}",

    @Persisted(key = "dismissed_alert_banner_ids")
    val dismissedAlertBannerIds: Set<String> = emptySet(),
)


@CategoryDefinition(order = 0, titleKey = FlickySettingsKeys.CATEGORY_APPEARANCE)
object Appearance

@CategoryDefinition(order = 1, titleKey = FlickySettingsKeys.CATEGORY_GENERAL)
object General

@CategoryDefinition(order = 2, titleKey = FlickySettingsKeys.CATEGORY_DOWNLOADS)
object Downloads

@CategoryDefinition(order = 3, titleKey = FlickySettingsKeys.CATEGORY_FILTERS)
object Filters

@CategoryDefinition(order = 4, titleKey = FlickySettingsKeys.CATEGORY_SYNC)
object Sync

@CategoryDefinition(order = 5, titleKey = FlickySettingsKeys.CATEGORY_PROXY)
object Proxy

@CategoryDefinition(order = 6, titleKey = FlickySettingsKeys.CATEGORY_OTHER)
object Other

//@CategoryDefinition(order = 7, titleKey = FlickySettingsKeys.CATEGORY_ABOUT)
//object About

object ClearCacheAction : SettingAction
object SupportDevelopmentAction : SettingAction

class SettingsActions(
    private val clearCache: suspend () -> Unit,
    private val openSupportDevelopment: suspend () -> Unit
) {
    suspend fun clearCache() = clearCache.invoke()
    suspend fun openSupportDevelopment() = openSupportDevelopment.invoke()
}
