package app.flicky.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.flicky.di.AppDependencies
import kotlinx.coroutines.flow.first

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val syncManager = AppDependencies.syncManager
            val settings = AppDependencies.settings

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