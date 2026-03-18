package app.flicky.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.flicky.data.repository.RepositorySyncManager
import app.flicky.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val syncManager: RepositorySyncManager by inject()
    private val settings: SettingsRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            syncManager.syncAll()
            val s = settings.settingsFlow.first()
            if (s.autoUpdate) {
                AutoUpdateWorker.enqueue(applicationContext, s.wifiOnly)
            }
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Sync failed", e)
            Result.retry()
        }
    }
}
