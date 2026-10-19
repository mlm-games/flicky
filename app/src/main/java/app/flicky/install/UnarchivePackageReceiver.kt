package app.flicky.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller.EXTRA_UNARCHIVE_ALL_USERS
import android.content.pm.PackageInstaller.EXTRA_UNARCHIVE_ID
import android.content.pm.PackageInstaller.EXTRA_UNARCHIVE_PACKAGE_NAME
import android.os.Build
import androidx.annotation.RequiresApi
import app.flicky.helper.DebugLog

class UnarchivePackageReceiver : BroadcastReceiver() {

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_UNARCHIVE_PACKAGE) return

        val packageName = intent.getStringExtra(EXTRA_UNARCHIVE_PACKAGE_NAME)
        if (packageName.isNullOrBlank()) {
            DebugLog.log("UnarchivePackageReceiver", "Missing package name, ignoring unarchive request")
            return
        }
        val unarchiveId = intent.getIntExtra(EXTRA_UNARCHIVE_ID, -1)
        val allUsers = intent.getBooleanExtra(EXTRA_UNARCHIVE_ALL_USERS, false)

        DebugLog.log(
            "UnarchivePackageReceiver",
            "Unarchive requested for $packageName (id=$unarchiveId, allUsers=$allUsers)"
        )
        UnarchiveWorker.enqueue(context.applicationContext, packageName, unarchiveId, allUsers)
    }
}
