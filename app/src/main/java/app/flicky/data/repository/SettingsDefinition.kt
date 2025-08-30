package app.flicky.data.repository

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

@Target(PROPERTY)
@Retention(RUNTIME)
annotation class Setting(
    val title: String,
    val description: String = "",
    val category: SettingCategory,
    val type: SettingType,
    val dependsOn: String = "",
    val min: Float = 0f,
    val max: Float = 100f,
    val step: Float = 1f,
    val options: Array<String> = []
)

enum class SettingCategory { GENERAL, APPEARANCE, DOWNLOADS, FILTERS, SYSTEM }
enum class SettingType { TOGGLE, DROPDOWN, SLIDER, BUTTON }

data class AppSettings(

    @Setting(
        title = "Default Sort",
        description = "Default sorting for app lists",
        category = SettingCategory.GENERAL,
        type = SettingType.DROPDOWN,
        options = ["Name", "Recently Updated", "Size", "Recently Added"]
    )
    val defaultSort: Int = 1,

    val appsPerRow: Int = 4,

    @Setting(
        title = "Theme",
        category = SettingCategory.APPEARANCE,
        type = SettingType.DROPDOWN,
        options = ["System", "Light", "Dark"]
    )
    val themeMode: Int = 2,

    @Setting(
        title = "Use dynamic colors",
        description = "Use Material You colors (Android 12+)",
        category = SettingCategory.APPEARANCE,
        type = SettingType.TOGGLE
    )
    val dynamicTheme: Boolean = false,

    val compactMode: Boolean = false,

    val showAppIcons: Boolean = true,

    val autoUpdate: Boolean = false,

    @Setting(
        title = "Update over Wi‑Fi only",
        category = SettingCategory.DOWNLOADS,
        type = SettingType.TOGGLE,
        dependsOn = "autoUpdate"
    )
    val wifiOnly: Boolean = true,

    @Setting(
        title = "Sync interval",
        description = "How often to sync repositories",
        category = SettingCategory.DOWNLOADS,
        type = SettingType.DROPDOWN,
        options = ["3 hours", "6 hours", "12 hours", "24 hours", "Weekly", "Manual only"]
    )
    val syncIntervalIndex: Int = 1,

    val notifyUpdates: Boolean = true,

    @Setting(
        title = "Keep download cache",
        description = "Keep downloaded APKs for faster reinstall",
        category = SettingCategory.DOWNLOADS,
        type = SettingType.TOGGLE
    )
    val keepCache: Boolean = false,

    @Setting(
        title = "Mirror rotation",
        description = "Rotate across available repo mirrors when downloading",
        category = SettingCategory.DOWNLOADS,
        type = SettingType.TOGGLE
    )
    val mirrorRotation: Boolean = true,

    @Setting(
        title = "Use .onion mirrors",
        description = "Include Tor (.onion) mirrors when rotating (requires Tor/I2P routing)",
        category = SettingCategory.DOWNLOADS,
        type = SettingType.TOGGLE
    )
    val useOnionMirrors: Boolean = false,

    @Setting(
        title = "Installer",
        description = "Choose how apps are installed",
        category = SettingCategory.DOWNLOADS,
        type = SettingType.DROPDOWN,
        options = ["System (default)", "Session (Package Installer)", "Root (pm)", "Shizuku (pm)"]
    )
    val installerMode: Int = 0,

    @Setting(
        title = "Hide apps with anti-features",
        category = SettingCategory.FILTERS,
        type = SettingType.TOGGLE
    )
    val hideAntiFeatures: Boolean = false,

    @Setting(
        title = "Show incompatible versions",
        category = SettingCategory.FILTERS,
        type = SettingType.TOGGLE
    )
    val showIncompatible: Boolean = false,

    val unstableUpdates: Boolean = false,

    val ignoreSignature: Boolean = false,

    @Setting(
        title = "Differential sync (HTTP)",
        description = "Use ETag/Last-Modified to skip unchanged repos",
        category = SettingCategory.DOWNLOADS,
        type = SettingType.TOGGLE
    )
    val differentialSync: Boolean = true,

    @Setting(
        title = "Use entry.json (experimental)",
        description = "Try entry.json to locate the best index (and future diffs).",
        category = SettingCategory.SYSTEM,
        type = SettingType.TOGGLE
    )
    val useEntryJson: Boolean = false,

    val useProxy: Boolean = false,

    val proxyType: Int = 0,

    val clearCache: Boolean = false,

    val exportSettings: Boolean = false,

    val importSettings: Boolean = false,

    @Setting(
        title = "Preferred repository",
        description = "Default source when app exists in multiple repos",
        category = SettingCategory.DOWNLOADS,
        type = SettingType.DROPDOWN,
        options = ["Auto", "Prefer F-Droid", "Prefer IzzyOnDroid"]
    )
    val preferredRepo: Int = 0,

    @Setting(
        title = "Show debug info",
        description = "Show repo and install events (for troubleshooting)",
        category = SettingCategory.SYSTEM,
        type = SettingType.TOGGLE
    )
    val showDebugInfo: Boolean = false,

    // Non-UI / repo related
    val lastSync: Long = 0L,
    val proxyHost: String = "",
    val proxyPort: Int = 9050,
)

class SettingsManager {
    fun getAll(): List<Pair<KProperty1<AppSettings, *>, Setting>> {
        return AppSettings::class.memberProperties.mapNotNull { p ->
            val ann = p.findAnnotation<Setting>()
            if (ann != null) p to ann else null
        }
    }
    fun getByCategory(): Map<SettingCategory, List<Pair<KProperty1<AppSettings, *>, Setting>>> {
        return getAll().groupBy { it.second.category }
    }
    fun isEnabled(settings: AppSettings, property: KProperty1<AppSettings, *>, annotation: Setting): Boolean {
        val depends = annotation.dependsOn
        if (depends.isBlank()) return true
        val depProp = AppSettings::class.memberProperties.find { it.name == depends }
        return if (depProp != null) {
            val v = depProp.get(settings)
            (v as? Boolean) ?: true
        } else true
    }
}