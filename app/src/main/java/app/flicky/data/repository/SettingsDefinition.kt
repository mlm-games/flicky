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

    // To Add Later when UI suits mobile (adapted from CCL)
//    @Setting(
//        title = "Default Sort",
//        description = "Default sorting for app lists",
//        category = SettingCategory.GENERAL,
//        type = SettingType.DROPDOWN,
//        options = ["Name", "Recently Updated", "Size", "Recently Added"]
//    )
    val defaultSort: Int = 1,

//    @Setting(
//        title = "Apps per row",
//        description = "Number of app cards per row",
//        category = SettingCategory.GENERAL,
//        type = SettingType.SLIDER,
//        min = 2f, max = 8f, step = 1f
//    )
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

//    @Setting(
//        title = "Compact mode",
//        description = "Show more items on screen",
//        category = SettingCategory.APPEARANCE,
//        type = SettingType.TOGGLE
//    )
    val compactMode: Boolean = false,

//    @Setting(
//        title = "Show app icons",
//        category = SettingCategory.APPEARANCE,
//        type = SettingType.TOGGLE
//    )
    val showAppIcons: Boolean = true,

    // Specific
//    @Setting(
//        title = "Auto-update apps",
//        category = SettingCategory.DOWNLOADS,
//        type = SettingType.TOGGLE
//    )
    val autoUpdate: Boolean = false,

//    @Setting(
//        title = "Update over Wi‑Fi only",
//        category = SettingCategory.DOWNLOADS,
//        type = SettingType.TOGGLE,
//        dependsOn = "autoUpdate"
//    )
    val wifiOnly: Boolean = true,

    @Setting(
        title = "Sync interval",
        description = "How often to sync repositories",
        category = SettingCategory.DOWNLOADS,
        type = SettingType.DROPDOWN,
        options = ["3 hours", "6 hours", "12 hours", "24 hours", "Weekly", "Manual only"]
    )
    val syncIntervalIndex: Int = 1,

//    @Setting(
//        title = "Update notifications",
//        category = SettingCategory.DOWNLOADS,
//        type = SettingType.TOGGLE
//    )
    val notifyUpdates: Boolean = true,

    @Setting(
        title = "Keep download cache",
        description = "Keep downloaded APKs for faster reinstall",
        category = SettingCategory.DOWNLOADS,
        type = SettingType.TOGGLE
    )
    val keepCache: Boolean = false,

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

    @Setting(
        title = "Show unstable updates",
        category = SettingCategory.FILTERS,
        type = SettingType.TOGGLE
    )
    val unstableUpdates: Boolean = false,

    @Setting(
        title = "Ignore signature",
        description = "Allow updates with different signatures (unsafe)",
        category = SettingCategory.FILTERS,
        type = SettingType.TOGGLE
    )
    val ignoreSignature: Boolean = false,

    // Misc (advanced, not needed for ui)
//    @Setting(
//        title = "Use proxy",
//        category = SettingCategory.SYSTEM,
//        type = SettingType.TOGGLE
//    )
    val useProxy: Boolean = false,

//    @Setting(
//        title = "Proxy type",
//        category = SettingCategory.SYSTEM,
//        type = SettingType.DROPDOWN,
//        options = ["HTTP", "SOCKS5"],
//        dependsOn = "useProxy"
//    )
    val proxyType: Int = 0,

    @Setting(
        title = "Clear cache",
        description = "Delete repo headers and reset last sync",
        category = SettingCategory.SYSTEM,
        type = SettingType.BUTTON
    )
    val clearCache: Boolean = false,

//    @Setting(
//        title = "Export settings",
//        description = "Backup settings and repositories",
//        category = SettingCategory.SYSTEM,
//        type = SettingType.BUTTON
//    )
    val exportSettings: Boolean = false,

//    @Setting(
//        title = "Import settings",
//        description = "Restore settings from backup",
//        category = SettingCategory.SYSTEM,
//        type = SettingType.BUTTON
//    )
    val importSettings: Boolean = false,

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