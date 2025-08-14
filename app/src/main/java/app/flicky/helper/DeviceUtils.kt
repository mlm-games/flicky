package app.flicky.helper

import android.content.pm.PackageManager
import android.os.Build

object DeviceUtils {
    fun isTVFeatures(pm: PackageManager): Boolean {
        val features = listOf("android.software.leanback", "android.hardware.type.television")
        return features.any { pm.hasSystemFeature(it) }
    }
}