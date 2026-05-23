package app.flicky.data.repository

import app.flicky.R
import io.github.mlmgames.settings.core.annotations.CategoryDefinition
import io.github.mlmgames.settings.core.annotations.NoReset
import io.github.mlmgames.settings.core.annotations.Persisted
import io.github.mlmgames.settings.core.annotations.SchemaVersion
import io.github.mlmgames.settings.core.annotations.Setting
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

@SchemaVersion(4)
data class AppSettings(
    @Setting(
        title = "Theme Mode",
        description = "Choose between light, dark, or system theme",
        category = Appearance::class,
        type = Dropdown::class,
        options = ["System", "Light", "Dark"]
    )
    val themeMode: Int = 2,

    @Setting(
        title = "Dynamic Theme",
        description = "Use Material You dynamic colors (Android 12+)",
        category = Appearance::class,
        type = Toggle::class
    )
    val dynamicTheme: Boolean = false,

    @Setting(
        title = "Show App Icons",
        description = "Display app icons in lists and grids",
        category = Appearance::class,
        type = Toggle::class
    )
    val showAppIcons: Boolean = true,

    @Setting(
        title = "Use List Layout",
        description = "Show apps in a list instead of grid",
        category = Appearance::class,
        type = Toggle::class
    )
    val useListLayout: Boolean = false,

    @Setting(
        title = "Default Sort",
        description = "How to sort apps by default",
        category = General::class,
        type = Dropdown::class,
        options = ["Name", "Name (Z-A)", "Updated", "Size", "Added"]
    )
    val defaultSort: Int = 1,

    @Persisted(key = "reverse_sort")
    val reverseSort: Boolean = false,

    @Setting(
        title = "Auto Update",
        description = "Automatically update apps in the background",
        category = Downloads::class,
        type = Toggle::class
    )
    val autoUpdate: Boolean = false,

    @Setting(
        title = "Wi-Fi Only",
        description = "Only download and sync over Wi-Fi",
        category = Downloads::class,
        type = Toggle::class
    )
    val wifiOnly: Boolean = true,

    @Setting(
        title = "Sync Interval",
        description = "How often to check for updates",
        category = Downloads::class,
        type = Dropdown::class,
        options = ["3 hours", "6 hours", "12 hours", "Daily", "Weekly", "Never"],
        key = "sync_interval_idx"
    )
    val syncIntervalIndex: Int = 1,

    @Setting(
        title = "Keep Download Cache",
        description = "Keep downloaded APKs after installation",
        category = Downloads::class,
        type = Toggle::class
    )
    val keepCache: Boolean = false,

    @Setting(
        title = "Installer Mode",
        description = "Method to use for installing apps",
        category = Downloads::class,
        type = Dropdown::class,
        options = ["System", "Session", "Root", "Shizuku", "App Manager", "Dhizuku"]
    )
    val installerMode: Int = 1,

    @Setting(
        title = "Preferred Repository",
        description = "Prefer updates from specific repository",
        category = Downloads::class,
        type = Dropdown::class,
        options = ["Auto", "F-Droid", "IzzyOnDroid"]
    )
    val preferredRepo: Int = 0,

    @Setting(
        title = "Hide Anti-Features",
        description = "Hide apps with anti-features",
        category = Filters::class,
        type = Toggle::class
    )
    val hideAntiFeatures: Boolean = false,

    @Setting(
        title = "Show Incompatible",
        description = "Show apps that are incompatible with your device",
        category = Filters::class,
        type = Toggle::class
    )
    val showIncompatible: Boolean = false,

    @Setting(
        title = "Show Reproducible Badges",
        description = "Display reproducible build badges from IzzyOnDroid",
        category = Filters::class,
        type = Toggle::class
    )
    val showReproducibleBadges: Boolean = false,

    @Setting(
        title = "Ignore Alpha/Beta Builds",
        description = "Hide alpha and beta releases from updates",
        category = Filters::class,
        type = Toggle::class
    )
    val ignoreUnstable: Boolean = false,

    @Setting(
        title = "Differential Sync",
        description = "Only fetch changes since last sync",
        category = Sync::class,
        type = Toggle::class
    )
    val differentialSync: Boolean = true,

    @Persisted
    val useEntryJson: Boolean = false,

    @Setting(
        title = "Fail on Trust Errors",
        description = "Strict SSL/TLS verification",
        category = Sync::class,
        type = Toggle::class
    )
    val failOnTrustErrors: Boolean = false,

    @Setting(
        title = "Use Proxy",
        description = "Route connections through a proxy",
        category = Proxy::class,
        type = Toggle::class
    )
    val useProxy: Boolean = false,

    @Setting(
        title = "Proxy URL",
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
        title = "Clear Cache",
        description = "Clear all cached data and images",
        category = Other::class,
        type = Button::class
    )
    @NoReset
    val clearCache: Long = 0L,

    @Setting(
        title = "Show Debug Info",
        description = "Display debug information in the UI",
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
    val supportDevelopment: Long = 0L,

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
