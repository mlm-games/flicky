package app.flicky.data.repository

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

enum class SettingCategory {
    APPEARANCE, GENERAL, DOWNLOADS, FILTERS, SYNC, PROXY, OTHER
}

enum class SettingType {
    TOGGLE, DROPDOWN, SLIDER, BUTTON
}

@Target(PROPERTY)
@Retention(RUNTIME)
annotation class Setting(
    val title: String,
    val description: String = "",
    val category: SettingCategory = SettingCategory.OTHER,
    val type: SettingType = SettingType.TOGGLE,
    val options: Array<String> = [],
    val min: Float = 0f,
    val max: Float = 100f,
    val step: Float = 1f,
    val enabledIf: String = ""
)

class SettingsManager {
    private val settingsProperties = AppSettings::class.memberProperties
        .mapNotNull { prop ->
            val annotation = prop.findAnnotation<Setting>()
            if (annotation != null) {
                prop to annotation
            } else null
        }

    fun getByCategory(): Map<SettingCategory, List<Pair<KProperty1<AppSettings, *>, Setting>>> {
        return settingsProperties.groupBy { it.second.category }
    }

    fun isEnabled(settings: AppSettings, prop: KProperty1<AppSettings, *>, ann: Setting): Boolean {
        if (ann.enabledIf.isBlank()) return true

        val depProp = AppSettings::class.memberProperties.find { it.name == ann.enabledIf }
        return if (depProp != null) {
            val v = depProp.get(settings)
            (v as? Boolean) ?: true
        } else true
    }
}
data class AppSettings(
    // Appearance
    @Setting(
        title = "Theme Mode",
        description = "Choose between light, dark, or system theme",
        category = SettingCategory.APPEARANCE,
        type = SettingType.DROPDOWN,
        options = ["System", "Light", "Dark"]
    )
    val themeMode: Int = 2,

    @Setting(
        title = "Dynamic Theme",
        description = "Use Material You dynamic colors (Android 12+)",
        category = SettingCategory.APPEARANCE,
        type = SettingType.TOGGLE
    )
    val dynamicTheme: Boolean = false,

    @Setting(
        title = "Show App Icons",
        description = "Display app icons in lists and grids",
        category = SettingCategory.APPEARANCE,
        type = SettingType.TOGGLE
    )
    val showAppIcons: Boolean = true,

    @Setting(
        title = "Use List Layout",
        description = "Show apps in a list instead of grid",
        category = SettingCategory.APPEARANCE,
        type = SettingType.TOGGLE
    )
    val useListLayout: Boolean = false,

    // General
    @Setting(
        title = "Default Sort",
        description = "How to sort apps by default",
        category = SettingCategory.GENERAL,
        type = SettingType.DROPDOWN,
        options = ["Name", "Updated", "Size", "Added"]
    )
    val defaultSort: Int = 1,

    // Downloads
    @Setting(
        title = "Auto Update",
        description = "Automatically update apps in the background",
        category = SettingCategory.DOWNLOADS,
        type = SettingType.TOGGLE
    )
    val autoUpdate: Boolean = false,

    @Setting(
        title = "Wi-Fi Only",
        description = "Only download and sync over Wi-Fi",
        category = SettingCategory.DOWNLOADS,
        type = SettingType.TOGGLE
    )
    val wifiOnly: Boolean = true,

    @Setting(
        title = "Sync Interval",
        description = "How often to check for updates",
        category = SettingCategory.DOWNLOADS,
        type = SettingType.DROPDOWN,
        options = ["3 hours", "6 hours", "12 hours", "Daily", "Weekly", "Never"]
    )
    val syncIntervalIndex: Int = 1,

    @Setting(
        title = "Keep Download Cache",
        description = "Keep downloaded APKs after installation",
        category = SettingCategory.DOWNLOADS,
        type = SettingType.TOGGLE
    )
    val keepCache: Boolean = false,

    @Setting(
        title = "Installer Mode",
        description = "Method to use for installing apps",
        category = SettingCategory.DOWNLOADS,
        type = SettingType.DROPDOWN,
        options = ["System", "Session", "Root", "Shizuku"]
    )
    val installerMode: Int = 0,

    @Setting(
        title = "Preferred Repository",
        description = "Prefer updates from specific repository",
        category = SettingCategory.DOWNLOADS,
        type = SettingType.DROPDOWN,
        options = ["Auto", "F-Droid", "IzzyOnDroid"]
    )
    val preferredRepo: Int = 0,

    // Filters
    @Setting(
        title = "Hide Anti-Features",
        description = "Hide apps with anti-features",
        category = SettingCategory.FILTERS,
        type = SettingType.TOGGLE
    )
    val hideAntiFeatures: Boolean = false,

    @Setting(
        title = "Show Incompatible",
        description = "Show apps that are incompatible with your device",
        category = SettingCategory.FILTERS,
        type = SettingType.TOGGLE
    )
    val showIncompatible: Boolean = false,

    // Sync
    @Setting(
        title = "Differential Sync",
        description = "Only fetch changes since last sync",
        category = SettingCategory.SYNC,
        type = SettingType.TOGGLE
    )
    val differentialSync: Boolean = true,

//    @Setting(
//        title = "Use Entry JSON",
//        description = "Use per-app JSON files (experimental)",
//        category = SettingCategory.SYNC,
//        type = SettingType.TOGGLE
//    )
    val useEntryJson: Boolean = false,

    @Setting(
        title = "Fail on Trust Errors",
        description = "Strict SSL/TLS verification",
        category = SettingCategory.SYNC,
        type = SettingType.TOGGLE
    )
    val failOnTrustErrors: Boolean = false,

    // Proxy
    @Setting(
        title = "Use Proxy",
        description = "Route connections through a proxy",
        category = SettingCategory.PROXY,
        type = SettingType.TOGGLE
    )
    val useProxy: Boolean = false,

    @Setting(
        title = "Proxy Type",
        description = "Type of proxy to use",
        category = SettingCategory.PROXY,
        type = SettingType.DROPDOWN,
        options = ["HTTP", "SOCKS5"],
        enabledIf = "useProxy"
    )
    val proxyType: Int = 0,

    @Setting(
        title = "Proxy Host",
        description = "Proxy server hostname",
        category = SettingCategory.PROXY,
        type = SettingType.BUTTON,
        enabledIf = "useProxy"
    )
    val proxyHost: String = "",

    @Setting(
        title = "Proxy Port",
        description = "Proxy server port",
        category = SettingCategory.PROXY,
        type = SettingType.SLIDER,
        min = 1f,
        max = 65535f,
        step = 1f,
        enabledIf = "useProxy"
    )
    val proxyPort: Int = 9050,

    // Other
    @Setting(
        title = "Clear Cache",
        description = "Clear all cached data and images",
        category = SettingCategory.OTHER,
        type = SettingType.BUTTON
    )
    val clearCache: Boolean = false,

//    @Setting(
//        title = "Export Settings",
//        description = "Export settings and repository list",
//        category = SettingCategory.OTHER,
//        type = SettingType.BUTTON
//    )
    val exportSettings: Boolean = false,

    @Setting(
        title = "Show Debug Info",
        description = "Display debug information in the UI",
        category = SettingCategory.OTHER,
        type = SettingType.TOGGLE
    )
    val showDebugInfo: Boolean = false,

    // Non-UI settings (no annotation)
    val lastSync: Long = 0L,

    val importSettings: Boolean = false,
    )