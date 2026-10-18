package app.flicky.work

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.work.*
import app.flicky.data.local.AppDatabase
import app.flicky.data.local.AppVariant
import app.flicky.data.model.FDroidApp
import app.flicky.data.repository.AppUpdatePreference
import app.flicky.data.repository.AppUpdatePreferencesMap
import app.flicky.data.repository.InstalledAppsRepository
import app.flicky.data.repository.PreferredRepo
import app.flicky.data.repository.SettingsRepository
import app.flicky.data.repository.VariantSelector
import app.flicky.install.Installer
import app.flicky.install.TaskStage
import app.flicky.notify.UpdateNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class AutoUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val settings: SettingsRepository by inject()
    private val installedRepo: InstalledAppsRepository by inject()
    private val db: AppDatabase by inject()
    private val installer: Installer by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val overridesOnly = inputData.getBoolean(KEY_OVERRIDES_ONLY, false)
            val settingsState = settings.settingsFlow.first()

            if (!settingsState.autoUpdate) return@withContext Result.success()
            if (overridesOnly && !settingsState.wifiOnly) return@withContext Result.success()

            val installed = installedRepo.getInstalledDetailed()
            val installedVc = installed.associate { it.packageName to it.versionCode }
            val apps: List<FDroidApp> = db.appDao().observeAll().first()
            val allPrefs = AppUpdatePreferencesMap.fromJson(settingsState.appUpdatePrefsJson).prefs

            val prefIdx = settingsState.preferredRepo
            val preferredRepo = PreferredRepo.fromIndex(prefIdx)

            val candidates = apps.mapNotNull { app ->
                val cur = installedVc[app.packageName] ?: return@mapNotNull null
                val pref = allPrefs[app.packageName] ?: AppUpdatePreference()
                if (pref.ignoreUpdates) return@mapNotNull null

                if (overridesOnly) {
                    if (!pref.updateOverMetered) return@mapNotNull null
                } else if (settingsState.wifiOnly && pref.updateOverMetered) {
                    return@mapNotNull null
                }

                val variants = db.appDao().variantsFor(app.packageName)
                val effectiveIgnoreUnstable = pref.ignoreUnstable ?: settingsState.ignoreUnstable
                val chosen = VariantSelector.pickCompatible(
                    variants = variants,
                    preferred = preferredRepo,
                    preferredRepoUrl = pref.preferredRepoUrl,
                    strict = pref.lockToRepo,
                    ignoreUnstable = effectiveIgnoreUnstable
                ) ?: return@mapNotNull null

                val latestCompat = chosen.versionCode.toLong()
                if (latestCompat <= cur) return@mapNotNull null
                if (pref.ignoreVersionCode > 0 && latestCompat <= pref.ignoreVersionCode) return@mapNotNull null

                app to chosen
            }

            gentleDelay(candidates.map { it.first.packageName })
            val queue = Channel<AppVariant>(Channel.UNLIMITED)
            candidates.forEach { (_, chosen) -> queue.trySend(chosen) }
            queue.close()
            val updated = AtomicInteger()
            val failed = AtomicInteger()
            coroutineScope {
                repeat(Installer.BATCH_WORKERS) {
                    launch {
                        for (chosen in queue) {
                            val installed = installer.install(chosen)
                            when {
                                installed -> updated.incrementAndGet()
                                installer.tasks.value[chosen.packageName] is TaskStage.NeedsConfirmation -> Unit
                                else -> failed.incrementAndGet()
                            }
                        }
                    }
                }
            }
            UpdateNotifications.notifyResult(applicationContext, updated.get(), failed.get())
            installer.pruneCache()
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("AutoUpdateWorker", "Auto update failed", e)
            Result.retry()
        }
    }

    private suspend fun gentleDelay(packageNames: List<String>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || packageNames.isEmpty()) {
            return
        }
        try {
            val pi = applicationContext.packageManager.packageInstaller
            val constraints = PackageInstaller.InstallConstraints.GENTLE_UPDATE
            val satisfied = suspendCancellableCoroutine { cont ->
                val executor = Executor { it.run() }
                pi.checkInstallConstraints(packageNames, constraints, executor) { result ->
                    cont.resume(result.areAllConstraintsSatisfied())
                }
            }
            if (!satisfied) {
                android.util.Log.d("AutoUpdateWorker", "Gentle-update constraints not met, proceeding anyway")
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val BULK_UNIQUE = "auto_update_worker"
        private const val OVERRIDES_UNIQUE = "auto_update_metered_overrides"
        private const val KEY_OVERRIDES_ONLY = "overrides_only"

        fun enqueue(context: Context, wifiOnly: Boolean, hasMeteredOverrides: Boolean = false) {
            val wm = WorkManager.getInstance(context)
            val bulkNetwork = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            enqueueWork(wm, BULK_UNIQUE, bulkNetwork, overridesOnly = false)
            if (wifiOnly && hasMeteredOverrides) {
                enqueueWork(wm, OVERRIDES_UNIQUE, NetworkType.CONNECTED, overridesOnly = true)
            } else {
                wm.cancelUniqueWork(OVERRIDES_UNIQUE)
            }
        }

        private fun enqueueWork(
            wm: WorkManager,
            unique: String,
            network: NetworkType,
            overridesOnly: Boolean
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(network)
                .setRequiresBatteryNotLow(true)
                .build()
            val work = OneTimeWorkRequestBuilder<AutoUpdateWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_OVERRIDES_ONLY to overridesOnly))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            wm.enqueueUniqueWork(unique, ExistingWorkPolicy.REPLACE, work)
        }
    }
}
