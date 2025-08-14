package app.flicky.data.settings

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
        title = "Theme",
        category = SettingCategory.APPEARANCE,
        type = SettingType.DROPDOWN,
        options = ["System", "Light", "Dark"]
    )
    val themeMode: Int = 2,

    @Setting(
        title = "Default Sort",
        description = "Default sorting for app lists",
        category = SettingCategory.GENERAL,
        type = SettingType.DROPDOWN,
        options = ["Name", "Recently Updated", "Size", "Recently Added"]
    )
    val defaultSort: Int = 1, // 0=Name,1=Updated,2=Size,3=Added

    @Setting(
        title = "Auto-update apps",
        category = SettingCategory.DOWNLOADS,
        type = SettingType.TOGGLE
    )
    val autoUpdate: Boolean = false,

    @Setting(
        title = "Update over Wi‑Fi only",
        category = SettingCategory.DOWNLOADS,
        type = SettingType.TOGGLE
    )
    val wifiOnly: Boolean = true,

    @Setting(
        title = "Sync Interval",
        description = "How often to sync repositories in background",
        category = SettingCategory.DOWNLOADS,
        type = SettingType.DROPDOWN,
        options = ["3 hours", "6 hours", "12 hours", "24 hours"]
    )
    val syncIntervalIndex: Int = 1, // 0=3h,1=6h,2=12h,3=24h

    @Setting(
        title = "Hide apps with anti-features",
        category = SettingCategory.FILTERS,
        type = SettingType.TOGGLE
    )
    val hideAntiFeatures: Boolean = false,
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

    fun isEnabled(
        settings: AppSettings,
        property: KProperty1<AppSettings, *>,
        annotation: Setting
    ): Boolean {
        val depends = annotation.dependsOn
        if (depends.isBlank()) return true
        val depProp = AppSettings::class.memberProperties.find { it.name == depends }
        return if (depProp != null) {
            val v = depProp.get(settings)
            (v as? Boolean) ?: true
        } else true
    }
}