package app.flicky.helper

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

object DeviceUtils {
    fun isTV(pm: PackageManager): Boolean {
        val features = listOf("android.software.leanback", "android.hardware.type.television")
        return features.any { pm.hasSystemFeature(it) }
    }

    fun isTV(context: Context): Boolean {
        val pm = context.packageManager
        val featureHit = isTV(pm)
        if (featureHit) return true
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
}