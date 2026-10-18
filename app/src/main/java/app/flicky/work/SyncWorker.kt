package app.flicky.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.flicky.data.repository.AppUpdatePreferencesMap
import app.flicky.data.repository.RepositorySyncManager
import app.flicky.data.repository.SettingsRepository
import app.flicky.install.Installer
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val syncManager: RepositorySyncManager by inject()
    private val settings: SettingsRepository by inject()
    private val installer: Installer by inject()

    override suspend fun doWork(): Result {
        return try {
            syncManager.syncAll()
            val s = settings.settingsFlow.first()
            if (s.autoUpdate) {
                val hasMeteredOverrides = AppUpdatePreferencesMap.fromJson(s.appUpdatePrefsJson)
                    .prefs.values.any { it.updateOverMetered }
                AutoUpdateWorker.enqueue(applicationContext, s.wifiOnly, hasMeteredOverrides)
            }
            installer.pruneCache()
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Sync failed", e)
            Result.retry()
        }
    }
}
