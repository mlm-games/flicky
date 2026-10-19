package app.flicky.notify

import android.content.Context
import app.flicky.data.local.AppDao
import app.flicky.data.model.SortOption
import app.flicky.data.repository.AppRepository
import app.flicky.data.repository.AppUpdatePreferencesMap
import app.flicky.data.repository.InstalledAppsRepository
import app.flicky.data.repository.PreferredRepo
import app.flicky.data.repository.SettingsRepository
import app.flicky.data.repository.UpdatesCalculator
import app.flicky.helper.DebugLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class UpdatesNotifier(
    private val context: Context,
    private val repo: AppRepository,
    private val installedRepo: InstalledAppsRepository,
    private val appDao: AppDao,
    private val settings: SettingsRepository
) {

    suspend fun refresh() = withContext(Dispatchers.IO) {
        try {
            val state = settings.settingsFlow.first()
            val apps = repo.appsFlow(
                query = "",
                sort = SortOption.Updated,
                reverseSort = false,
                hideAnti = false,
                showIncompatible = false
            ).first()
            val snapshot = UpdatesCalculator.compute(
                allApps = apps,
                installedDetails = installedRepo.getInstalledDetailed(),
                appDao = appDao,
                prefs = AppUpdatePreferencesMap.fromJson(state.appUpdatePrefsJson).prefs,
                preferredRepo = PreferredRepo.fromIndex(state.preferredRepo),
                ignoreUnstable = state.ignoreUnstable
            )
            val count = snapshot.updates.size
            if (count > 0) {
                UpdateNotifications.notifyUpdatesAvailable(context, count)
            } else {
                UpdateNotifications.cancelUpdatesAvailable(context)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.log("UpdatesNotifier", "Failed to refresh updates notification: ${e.message}")
        }
    }
}
