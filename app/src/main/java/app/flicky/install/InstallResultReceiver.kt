package app.flicky.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            val pending = confirm?.let {
                PendingIntent.getActivity(
                    context,
                    sessionId,
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            CoroutineScope(Dispatchers.Default).launch {
                SessionInstallBus.publish(InstallEvent(sessionId, status, confirmIntent = pending))
            }
            return
        }

        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val other = intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME)

        CoroutineScope(Dispatchers.Default).launch {
            SessionInstallBus.publish(sessionId, status, msg, other)
        }
    }
}
