package app.flicky.work

import android.content.Context
import androidx.work.*
import app.flicky.AppGraph
import app.flicky.data.external.UpdatesPreferences
import app.flicky.data.model.FDroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AutoUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val settings = AppGraph.settings.settingsFlow.first()
            if (!settings.autoUpdate) return@withContext Result.success()

            val installed = AppGraph.installedRepo.getInstalledDetailed()
            val installedVc = installed.associate { it.packageName to it.versionCode }
            val apps: List<FDroidApp> = AppGraph.db.appDao().observeAll().first()

            val candidates = apps.filter { app ->
                val cur = installedVc[app.packageName] ?: return@filter false
                val latestCompat = AppGraph.db.appDao().maxCompatibleVersionCode(app.packageName)?.toLong() ?: 0L
                if (latestCompat <= cur) return@filter false
                val pref = UpdatesPreferences[app.packageName]
                !pref.ignoreUpdates && !(pref.ignoreVersionCode > 0 && latestCompat <= pref.ignoreVersionCode)
            }

            for (app in candidates) {
                AppGraph.installer.install(app)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE = "auto_update_worker"

        fun enqueue(context: Context, wifiOnly: Boolean) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val work = OneTimeWorkRequestBuilder<AutoUpdateWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, work)
        }
    }
}