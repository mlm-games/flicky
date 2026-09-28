package app.flicky.data.repository

import app.flicky.R
import io.github.mlmgames.settings.core.annotations.ActionHandler
import io.github.mlmgames.settings.core.annotations.CategoryDefinition
import io.github.mlmgames.settings.core.annotations.NoReset
import io.github.mlmgames.settings.core.annotations.Persisted
import io.github.mlmgames.settings.core.annotations.SchemaVersion
import io.github.mlmgames.settings.core.annotations.Setting
import io.github.mlmgames.settings.core.annotations.SettingAction
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
        titleRes = R.string.setting_theme,
        description = "Choose between light, dark, or system theme",
        descriptionRes = R.string.setting_theme_desc,
        category = Appearance::class,
        type = Dropdown::class,
        options = ["System", "Light", "Dark"],
        optionsRes = R.array.setting_theme_options,
    )
    val themeMode: Int = 2,

    @Setting(
        titleRes = R.string.setting_dynamic_colors,
        description = "Use Material You dynamic colors (Android 12+)",
        descriptionRes = R.string.setting_dynamic_colors_desc,
        category = Appearance::class,
        type = Toggle::class
    )
    val dynamicTheme: Boolean = false,

    @Setting(
        titleRes = R.string.setting_show_app_icons,
        description = "Display app icons in lists and grids",
        descriptionRes = R.string.setting_show_app_icons_desc,
        category = Appearance::class,
        type = Toggle::class
    )
    val showAppIcons: Boolean = true,

    @Setting(
        titleRes = R.string.setting_use_list_layout,
        description = "Show apps in a list instead of grid",
        descriptionRes = R.string.setting_use_list_layout_desc,
        category = Appearance::class,
        type = Toggle::class
    )
    val useListLayout: Boolean = false,

    @Setting(
        titleRes = R.string.setting_default_sort,
        description = "How to sort apps by default",
        descriptionRes = R.string.setting_default_sort_desc,
        category = General::class,
        type = Dropdown::class,
        options = ["Name", "Name (Z-A)", "Updated", "Size", "Added"],
        optionsRes = R.array.setting_default_sort_options,
    )
    val defaultSort: Int = 1,

    @Persisted(key = "reverse_sort")
    val reverseSort: Boolean = false,

    @Setting(
        titleRes = R.string.setting_auto_update,
        description = "Automatically update apps in the background",
        descriptionRes = R.string.setting_auto_update_desc,
        category = Downloads::class,
        type = Toggle::class
    )
    val autoUpdate: Boolean = false,

    @Setting(
        titleRes = R.string.setting_wifi_only,
        description = "Only download and sync over Wi-Fi",
        descriptionRes = R.string.setting_wifi_only_desc,
        category = Downloads::class,
        type = Toggle::class
    )
    val wifiOnly: Boolean = true,

    @Setting(
        titleRes = R.string.setting_sync_interval,
        description = "How often to check for updates",
        descriptionRes = R.string.setting_sync_interval_desc,
        category = Downloads::class,
        type = Dropdown::class,
        options = ["3 hours", "6 hours", "12 hours", "Daily", "Weekly", "Never"],
        optionsRes = R.array.setting_sync_interval_options,
        key = "sync_interval_idx"
    )
    val syncIntervalIndex: Int = 1,

    @Setting(
        titleRes = R.string.setting_keep_cache,
        description = "Keep downloaded APKs after installation",
        descriptionRes = R.string.setting_keep_cache_desc,
        category = Downloads::class,
        type = Toggle::class
    )
    val keepCache: Boolean = false,

    @Setting(
        titleRes = R.string.setting_installer,
        description = "Method to use for installing apps",
        descriptionRes = R.string.setting_installer_desc,
        category = Downloads::class,
        type = Dropdown::class,
        options = ["System", "Session", "Root", "Shizuku", "App Manager", "Dhizuku"],
        optionsRes = R.array.setting_installer_options,
    )
    val installerMode: Int = 1,

    @Setting(
        titleRes = R.string.setting_fallback_installer,
        description = "Used if the primary installer fails, set to None to disable",
        descriptionRes = R.string.setting_fallback_installer_desc,
        category = Downloads::class,
        type = Dropdown::class,
        options = ["None", "System", "Session", "Root", "Shizuku", "App Manager", "Dhizuku"],
        optionsRes = R.array.setting_fallback_installer_options,
    )
    val fallbackInstallerMode: Int = 2,

    @Setting(
        titleRes = R.string.setting_preferred_repo,
        description = "Prefer updates from specific repository",
        descriptionRes = R.string.setting_preferred_repo_desc,
        category = Downloads::class,
        type = Dropdown::class,
        options = ["Auto", "F-Droid", "IzzyOnDroid"],
        optionsRes = R.array.setting_preferred_repo_options,
    )
    val preferredRepo: Int = 0,

    @Setting(
        titleRes = R.string.setting_hide_anti,
        description = "Hide apps with anti-features",
        category = Filters::class,
        type = Toggle::class
    )
    val hideAntiFeatures: Boolean = false,

    @Setting(
        titleRes = R.string.setting_show_incompatible,
        description = "Show apps that are incompatible with your device",
        category = Filters::class,
        type = Toggle::class
    )
    val showIncompatible: Boolean = false,

    @Setting(
        titleRes = R.string.setting_show_reproducible,
        description = "Display reproducible build badges from IzzyOnDroid",
        descriptionRes = R.string.setting_show_reproducible_desc,
        category = Filters::class,
        type = Toggle::class
    )
    val showReproducibleBadges: Boolean = false,

    @Setting(
        titleRes = R.string.setting_ignore_unstable,
        description = "Hide alpha and beta releases from updates",
        descriptionRes = R.string.setting_ignore_unstable_desc,
        category = Filters::class,
        type = Toggle::class
    )
    val ignoreUnstable: Boolean = false,

    @Setting(
        titleRes = R.string.setting_differential_sync,
        description = "Only fetch changes since last sync",
        descriptionRes = R.string.setting_differential_sync_desc,
        category = Sync::class,
        type = Toggle::class
    )
    val differentialSync: Boolean = true,

    @Persisted
    val useEntryJson: Boolean = false,

    @Setting(
        titleRes = R.string.setting_fail_on_trust_errors,
        description = "Strict SSL/TLS verification",
        descriptionRes = R.string.setting_fail_on_trust_errors_desc,
        category = Sync::class,
        type = Toggle::class
    )
    val failOnTrustErrors: Boolean = false,

    @Setting(
        titleRes = R.string.setting_use_proxy,
        description = "Route connections through a proxy",
        descriptionRes = R.string.setting_use_proxy_desc,
        category = Proxy::class,
        type = Toggle::class
    )
    val useProxy: Boolean = false,

    @Setting(
        titleRes = R.string.setting_proxy_url,
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
        titleRes = R.string.setting_show_debug_info,
        description = "Clear all cached data and images",
        descriptionRes = R.string.setting_show_debug_info_desc,
        category = Other::class,
        type = Button::class
    )
    @NoReset
    @ActionHandler(ClearCacheAction::class)
    val clearCache: Unit = Unit,

    @Setting(
        titleRes = R.string.setting_show_debug_info,
        description = "Display debug information in the UI",
        descriptionRes = R.string.setting_show_debug_info_desc,
        category = Other::class,
        type = Toggle::class
    )
    val showDebugInfo: Boolean = false,

    @Setting(
        title = "Support Development",
        description = "If you find this app useful, consider supporting its continued development",
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


@CategoryDefinition(order = 0, titleRes = R.string.category_appearance)
object Appearance

@CategoryDefinition(order = 1, titleRes = R.string.category_general)
object General

@CategoryDefinition(order = 2, titleRes = R.string.category_downloads)
object Downloads

@CategoryDefinition(order = 3, titleRes = R.string.category_filters)
object Filters

@CategoryDefinition(order = 4, titleRes = R.string.category_sync)
object Sync

@CategoryDefinition(order = 5, titleRes = R.string.category_proxy)
object Proxy

@CategoryDefinition(order = 6, titleRes = R.string.category_other)
object Other

//@CategoryDefinition(order = 7, titleRes = R.string.category_about)
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
