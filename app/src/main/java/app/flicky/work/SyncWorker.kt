package app.flicky.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.flicky.AppGraph
import kotlinx.coroutines.flow.first

class SyncWorker(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            AppGraph.init(applicationContext)
            AppGraph.syncManager.syncAll()
            val s = AppGraph.settings.settingsFlow.first()
            if (s.autoUpdate) {
                AutoUpdateWorker.enqueue(applicationContext, s.wifiOnly)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}