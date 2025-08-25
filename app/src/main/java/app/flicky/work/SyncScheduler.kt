package app.flicky.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {
    fun schedule(context: Context, wifiOnly: Boolean, intervalHours: Int) {
        val wm = WorkManager.getInstance(context)

        if (intervalHours <= 0) {
            wm.cancelUniqueWork("flicky.sync")
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val work = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalHours.toLong(), TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        wm.enqueueUniquePeriodicWork(
            "flicky.sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            work
        )
    }
}