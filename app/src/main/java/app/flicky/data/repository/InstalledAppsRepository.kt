package app.flicky.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Suppress("DEPRECATION")
class InstalledAppsRepository(private val context: Context) {

    data class Installed(val packageName: String, val versionCode: Long)
    data class InstalledDetailed(val packageName: String, val versionCode: Long, val versionName: String?)

    fun getInstalled(): List<Installed> {
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA
        val pkgs = pm.getInstalledPackages(flags)
        return pkgs.mapNotNull { pi ->
            val pkg = pi.packageName
            val vc = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
            Installed(pkg, vc)
        }
    }

    fun getInstalledDetailed(): List<InstalledDetailed> {
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA
        val pkgs = pm.getInstalledPackages(flags)
        return pkgs.mapNotNull { pi ->
            val pkg = pi.packageName
            val vc = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
            val vn = pi.versionName
            InstalledDetailed(pkg, vc, vn)
        }
    }

    fun getVersionCode(packageName: String): Long? {
        return try {
            val pm = context.packageManager
            val pi = pm.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        } catch (_: Exception) { null }
    }

    fun isInstalled(packageName: String): Boolean = getVersionCode(packageName) != null

    /**
     * Emits when a package is added/changed/replaced/removed.
     */
    fun packageChangesFlow(): Flow<Unit> = callbackFlow {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                trySend(Unit).isSuccess
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        awaitClose { context.unregisterReceiver(receiver) }
    }
    // Duplicate to not break UpdatesScreen
    fun packageNameChangesFlow(): Flow<String> = callbackFlow {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val pkg = intent?.data?.schemeSpecificPart
                if (!pkg.isNullOrBlank()) trySend(pkg).isSuccess
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
