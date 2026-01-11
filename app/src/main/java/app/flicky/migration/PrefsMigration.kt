package app.flicky.migration

import android.content.Context
import app.flicky.data.repository.AppUpdatePreference
import app.flicky.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.core.content.edit
import app.flicky.data.repository.AppUpdatePreferencesMap
import kotlinx.coroutines.flow.first

object PreferencesMigration {
    private const val MIGRATION_KEY = "migration_v2_completed"

    suspend fun migrateIfNeeded(context: Context, settings: SettingsRepository) {
        val migrationPrefs = context.getSharedPreferences("migration", Context.MODE_PRIVATE)
        if (migrationPrefs.getBoolean(MIGRATION_KEY, false)) return

        withContext(Dispatchers.IO) {
            try {
                // Migrate favorites
                val favoritesPrefs = context.getSharedPreferences("favorites_preferences", Context.MODE_PRIVATE)
                val oldFavorites = favoritesPrefs.getStringSet("favorite_packages", null)
                if (!oldFavorites.isNullOrEmpty()) {
                    settings.updateSettings { current ->
                        current.copy(favoritePackages = current.favoritePackages + oldFavorites)
                    }
                    android.util.Log.d("Migration", "Migrated ${oldFavorites.size} favorites")
                }

                // Migrate update preferences
                val updatesPrefs = context.getSharedPreferences("update_preferences", Context.MODE_PRIVATE)
                val allEntries = updatesPrefs.all
                if (allEntries.isNotEmpty()) {
                    val currentMap = AppUpdatePreferencesMap.fromJson(
                        settings.settingsFlow.first().appUpdatePrefsJson
                    )
                    var newMap = currentMap

                    allEntries.forEach { (packageName, value) ->
                        if (value is String && packageName != MIGRATION_KEY) {
                            try {
                                val obj = JSONObject(value)
                                val pref = AppUpdatePreference(
                                    ignoreUpdates = obj.optBoolean("ignoreUpdates", false),
                                    ignoreVersionCode = obj.optLong("ignoreVersionCode", 0L),
                                    preferredRepoUrl = obj.optString("preferredRepoUrl", "")
                                        .takeIf { it.isNotBlank() },
                                    lockToRepo = obj.optBoolean("lockToRepo", true)
                                )
                                newMap = newMap.with(packageName, pref)
                            } catch (e: Exception) {
                                android.util.Log.w("Migration", "Failed to parse pref for $packageName", e)
                            }
                        }
                    }

                    if (newMap.prefs.isNotEmpty()) {
                        settings.updateSettings { current ->
                            current.copy(appUpdatePrefsJson = AppUpdatePreferencesMap.toJson(newMap))
                        }
                        android.util.Log.d("Migration", "Migrated ${newMap.prefs.size} update prefs")
                    }
                }

                // Mark migration complete only after success
                migrationPrefs.edit { putBoolean(MIGRATION_KEY, true) }
                android.util.Log.d("Migration", "Migration completed successfully")

            } catch (e: Exception) {
                android.util.Log.e("Migration", "Migration failed", e)
                // Don't mark as complete so it retries next launch
            }
        }
    }
}