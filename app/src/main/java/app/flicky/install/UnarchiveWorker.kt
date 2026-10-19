package app.flicky.install

import android.content.Context
import android.content.pm.PackageInstaller.EXTRA_UNARCHIVE_ALL_USERS
import android.content.pm.PackageInstaller.EXTRA_UNARCHIVE_ID
import android.content.pm.PackageInstaller.EXTRA_UNARCHIVE_PACKAGE_NAME
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.flicky.data.local.AppDatabase
import app.flicky.data.repository.AppUpdatePreferencesMap
import app.flicky.data.repository.PreferredRepo
import app.flicky.data.repository.SettingsRepository
import app.flicky.data.repository.VariantSelector
import app.flicky.helper.DebugLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class UnarchiveWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val db: AppDatabase by inject()
    private val settings: SettingsRepository by inject()
    private val installer: Installer by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val packageName = inputData.getString(EXTRA_UNARCHIVE_PACKAGE_NAME)
        if (packageName.isNullOrBlank()) {
            DebugLog.log("UnarchiveWorker", "Missing package name in request")
            return@withContext Result.failure()
        }

        try {
            val state = settings.settingsFlow.first()
            val pref = AppUpdatePreferencesMap.fromJson(state.appUpdatePrefsJson)[packageName]
            val variants = db.appDao().variantsFor(packageName)
            val chosen = VariantSelector.pickCompatible(
                variants = variants,
                preferred = PreferredRepo.fromIndex(state.preferredRepo),
                preferredRepoUrl = pref.preferredRepoUrl,
                strict = pref.lockToRepo,
                ignoreUnstable = pref.ignoreUnstable ?: state.ignoreUnstable
            )
            if (chosen == null) {
                DebugLog.log("UnarchiveWorker", "No compatible variant found for $packageName")
                return@withContext Result.failure()
            }

            DebugLog.log(
                "UnarchiveWorker",
                "Installing ${chosen.packageName} ${chosen.versionName} to unarchive"
            )
            if (installer.install(chosen)) Result.success() else Result.failure()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.log("UnarchiveWorker", "Unarchive failed for $packageName: ${e.message}")
            Result.failure()
        }
    }

    companion object {
        fun enqueue(context: Context, packageName: String, unarchiveId: Int, allUsers: Boolean) {
            val data = Data.Builder()
                .putString(EXTRA_UNARCHIVE_PACKAGE_NAME, packageName)
                .putInt(EXTRA_UNARCHIVE_ID, unarchiveId)
                .putBoolean(EXTRA_UNARCHIVE_ALL_USERS, allUsers)
                .build()
            val request = OneTimeWorkRequestBuilder<UnarchiveWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
