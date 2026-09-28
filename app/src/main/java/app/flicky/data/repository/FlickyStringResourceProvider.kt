package app.flicky.data.repository

import android.content.Context
import app.flicky.R
import io.github.mlmgames.settings.core.resources.AndroidStringResourceProvider
import io.github.mlmgames.settings.core.resources.StringResourceProvider

/**
 * Maps [FlickySettingsKeys] to Android resources.
 *
 * `AndroidStringResourceProvider` consults its key resolver before falling back to
 * the built-in key set, so one table serves both the string and string-array paths
 * and the generated schema never has to carry a resource id.
 */
private val settingsResources: Map<String, Int> = mapOf(
    FlickySettingsKeys.CATEGORY_ABOUT to R.string.category_about,
    FlickySettingsKeys.CATEGORY_APPEARANCE to R.string.category_appearance,
    FlickySettingsKeys.CATEGORY_DOWNLOADS to R.string.category_downloads,
    FlickySettingsKeys.CATEGORY_FILTERS to R.string.category_filters,
    FlickySettingsKeys.CATEGORY_GENERAL to R.string.category_general,
    FlickySettingsKeys.CATEGORY_OTHER to R.string.category_other,
    FlickySettingsKeys.CATEGORY_PROXY to R.string.category_proxy,
    FlickySettingsKeys.CATEGORY_SYNC to R.string.category_sync,
    FlickySettingsKeys.SETTING_AUTO_UPDATE to R.string.setting_auto_update,
    FlickySettingsKeys.SETTING_CLEAR_CACHE to R.string.setting_clear_cache,
    FlickySettingsKeys.SETTING_DEFAULT_SORT to R.string.setting_default_sort,
    FlickySettingsKeys.SETTING_DIFFERENTIAL_SYNC to R.string.setting_differential_sync,
    FlickySettingsKeys.SETTING_DYNAMIC_COLORS to R.string.setting_dynamic_colors,
    FlickySettingsKeys.SETTING_FAIL_ON_TRUST_ERRORS to R.string.setting_fail_on_trust_errors,
    FlickySettingsKeys.SETTING_FALLBACK_INSTALLER to R.string.setting_fallback_installer,
    FlickySettingsKeys.SETTING_HIDE_ANTI to R.string.setting_hide_anti,
    FlickySettingsKeys.SETTING_IGNORE_UNSTABLE to R.string.setting_ignore_unstable,
    FlickySettingsKeys.SETTING_INSTALLER to R.string.setting_installer,
    FlickySettingsKeys.SETTING_KEEP_CACHE to R.string.setting_keep_cache,
    FlickySettingsKeys.SETTING_PREFERRED_REPO to R.string.setting_preferred_repo,
    FlickySettingsKeys.SETTING_PROXY_URL to R.string.setting_proxy_url,
    FlickySettingsKeys.SETTING_SHOW_APP_ICONS to R.string.setting_show_app_icons,
    FlickySettingsKeys.SETTING_SHOW_DEBUG_INFO to R.string.setting_show_debug_info,
    FlickySettingsKeys.SETTING_SHOW_INCOMPATIBLE to R.string.setting_show_incompatible,
    FlickySettingsKeys.SETTING_SHOW_REPRODUCIBLE to R.string.setting_show_reproducible,
    FlickySettingsKeys.SETTING_SUPPORT_DEVELOPMENT to R.string.setting_support_development,
    FlickySettingsKeys.SETTING_SYNC_INTERVAL to R.string.setting_sync_interval,
    FlickySettingsKeys.SETTING_THEME to R.string.setting_theme,
    FlickySettingsKeys.SETTING_USE_LIST_LAYOUT to R.string.setting_use_list_layout,
    FlickySettingsKeys.SETTING_USE_PROXY to R.string.setting_use_proxy,
    FlickySettingsKeys.SETTING_WIFI_ONLY to R.string.setting_wifi_only,
    FlickySettingsKeys.SETTING_AUTO_UPDATE_DESCRIPTION to R.string.setting_auto_update_desc,
    FlickySettingsKeys.SETTING_CLEAR_CACHE_DESCRIPTION to R.string.setting_clear_cache_desc,
    FlickySettingsKeys.SETTING_DEFAULT_SORT_DESCRIPTION to R.string.setting_default_sort_desc,
    FlickySettingsKeys.SETTING_DIFFERENTIAL_SYNC_DESCRIPTION to R.string.setting_differential_sync_desc,
    FlickySettingsKeys.SETTING_DYNAMIC_COLORS_DESCRIPTION to R.string.setting_dynamic_colors_desc,
    FlickySettingsKeys.SETTING_FAIL_ON_TRUST_ERRORS_DESCRIPTION to
        R.string.setting_fail_on_trust_errors_desc,
    FlickySettingsKeys.SETTING_FALLBACK_INSTALLER_DESCRIPTION to
        R.string.setting_fallback_installer_desc,
    FlickySettingsKeys.SETTING_HIDE_ANTI_FEATURES_DESCRIPTION to
        R.string.setting_hide_anti_features_desc,
    FlickySettingsKeys.SETTING_IGNORE_UNSTABLE_DESCRIPTION to R.string.setting_ignore_unstable_desc,
    FlickySettingsKeys.SETTING_INSTALLER_DESCRIPTION to R.string.setting_installer_desc,
    FlickySettingsKeys.SETTING_KEEP_CACHE_DESCRIPTION to R.string.setting_keep_cache_desc,
    FlickySettingsKeys.SETTING_PREFERRED_REPO_DESCRIPTION to R.string.setting_preferred_repo_desc,
    FlickySettingsKeys.SETTING_SHOW_APP_ICONS_DESCRIPTION to R.string.setting_show_app_icons_desc,
    FlickySettingsKeys.SETTING_SHOW_DEBUG_INFO_DESCRIPTION to R.string.setting_show_debug_info_desc,
    FlickySettingsKeys.SETTING_SHOW_INCOMPATIBLE_DESCRIPTION to R.string.setting_show_incompatible_desc,
    FlickySettingsKeys.SETTING_SHOW_REPRODUCIBLE_DESCRIPTION to R.string.setting_show_reproducible_desc,
    FlickySettingsKeys.SETTING_SUPPORT_DEVELOPMENT_DESCRIPTION to
        R.string.setting_support_development_desc,
    FlickySettingsKeys.SETTING_SYNC_INTERVAL_DESCRIPTION to R.string.setting_sync_interval_desc,
    FlickySettingsKeys.SETTING_THEME_DESCRIPTION to R.string.setting_theme_desc,
    FlickySettingsKeys.SETTING_USE_LIST_LAYOUT_DESCRIPTION to R.string.setting_use_list_layout_desc,
    FlickySettingsKeys.SETTING_USE_PROXY_DESCRIPTION to R.string.setting_use_proxy_desc,
    FlickySettingsKeys.SETTING_WIFI_ONLY_DESCRIPTION to R.string.setting_wifi_only_desc,
    FlickySettingsKeys.SETTING_DEFAULT_SORT_OPTIONS to R.array.setting_default_sort_options,
    FlickySettingsKeys.SETTING_FALLBACK_INSTALLER_OPTIONS to R.array.setting_fallback_installer_options,
    FlickySettingsKeys.SETTING_INSTALLER_OPTIONS to R.array.setting_installer_options,
    FlickySettingsKeys.SETTING_PREFERRED_REPO_OPTIONS to R.array.setting_preferred_repo_options,
    FlickySettingsKeys.SETTING_SYNC_INTERVAL_OPTIONS to R.array.setting_sync_interval_options,
    FlickySettingsKeys.SETTING_THEME_OPTIONS to R.array.setting_theme_options,
)

fun flickyStringResourceProvider(context: Context): StringResourceProvider =
    AndroidStringResourceProvider(context) { key -> settingsResources[key] ?: 0 }
