package app.flicky.work

import android.content.Context
import androidx.work.*
import app.flicky.data.model.FDroidApp
import app.flicky.data.repository.AppUpdatePreference
import app.flicky.data.repository.AppUpdatePreferencesMap
import app.flicky.data.repository.PreferredRepo
import app.flicky.data.repository.VariantSelector
import app.flicky.di.AppDependencies
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
            val settings = AppDependencies.settings
            val settingsState = settings.settingsFlow.first()

            if (!settingsState.autoUpdate) return@withContext Result.success()

            val installedRepo = AppDependencies.installedRepo
            val db = AppDependencies.db
            val installer = AppDependencies.installer

            val installed = installedRepo.getInstalledDetailed()
            val installedVc = installed.associate { it.packageName to it.versionCode }
            val apps: List<FDroidApp> = db.appDao().observeAll().first()
            val allPrefs = AppUpdatePreferencesMap.fromJson(settingsState.appUpdatePrefsJson).prefs

            val candidates = apps.filter { app ->
                val cur = installedVc[app.packageName] ?: return@filter false
                val latestCompat = db.appDao().maxCompatibleVersionCode(app.packageName)?.toLong() ?: 0L
                if (latestCompat <= cur) return@filter false

                val pref = allPrefs[app.packageName] ?: AppUpdatePreference()
                !pref.ignoreUpdates && !(pref.ignoreVersionCode > 0 && latestCompat <= pref.ignoreVersionCode)
            }

            for (app in candidates) {
                val pref = allPrefs[app.packageName] ?: AppUpdatePreference()
                val variants = db.appDao().variantsFor(app.packageName)
                val chosen = VariantSelector.pick(
                    variants = variants,
                    preferred = PreferredRepo.Auto,
                    preferredRepoUrl = pref.preferredRepoUrl,
                    strict = pref.lockToRepo
                )
                if (chosen != null) {
                    installer.install(chosen)
                } else {
                    // Skip if locked and no variant matches; otherwise fallback
                    if (!pref.lockToRepo) installer.install(app)
                }
            }
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("AutoUpdateWorker", "Auto update failed", e)
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