package com.theonionsarewatching.yapchik

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Default device heuristics for [SoftkeyMode.AUTO].
 *
 * A device "looks like a keypad phone" when it reports a 12-key keyboard or
 * has no touchscreen. TVs are explicitly excluded (they are D-pad devices but
 * softkey bars make no sense there).
 *
 * Replace [Yapchik.autoDetector] if your fleet needs something more specific.
 */
object AutoDetect {

    @JvmStatic
    fun deviceLooksLikeKeypad(context: Context): Boolean {
        val cfg = context.resources.configuration

        val uiModeType = cfg.uiMode and Configuration.UI_MODE_TYPE_MASK
        if (uiModeType == Configuration.UI_MODE_TYPE_TELEVISION) return false

        val hasTouch =
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) &&
                cfg.touchscreen != Configuration.TOUCHSCREEN_NOTOUCH

        val twelveKey = cfg.keyboard == Configuration.KEYBOARD_12KEY

        return twelveKey || !hasTouch
    }
}
