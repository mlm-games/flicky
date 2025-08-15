package app.flicky.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

@Suppress("DEPRECATION")
class InstalledAppsRepository(private val context: Context) {

    data class Installed(val packageName: String, val versionCode: Long)

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

    fun getVersionCode(packageName: String): Long? {
        return try {
            val pm = context.packageManager
            val pi = pm.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        } catch (_: Exception) { null }
    }

    fun isInstalled(packageName: String): Boolean = getVersionCode(packageName) != null
}