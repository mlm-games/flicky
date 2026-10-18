package com.theonionsarewatching.yapchik

import android.app.Activity
import android.app.AlertDialog

/**
 * One-call settings UI: an On / Off / Automatic chooser with a shortcut to
 * the key-layout chooser. Drop it behind a menu item or settings row:
 *
 * ```kotlin
 * YapchikSettingsDialog.show(this)
 * ```
 *
 * Selection is persisted automatically (it just sets [Yapchik.mode]).
 * If you'd rather build your own settings UI, see the README — reading and
 * writing [Yapchik.mode] directly is all it takes.
 */
object YapchikSettingsDialog {

    @JvmStatic
    @JvmOverloads
    fun show(activity: Activity, onChanged: (() -> Unit)? = null) {
        val modes = SoftkeyMode.values() // ON, OFF, AUTO
        val labels = arrayOf(
            "On",
            "Off",
            "Automatic — detect keypad device"
        )
        val checked = modes.indexOf(Yapchik.mode)

        AlertDialog.Builder(activity)
            .setTitle("Softkeys")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                Yapchik.mode = modes[which]
                onChanged?.invoke()
                dialog.dismiss()
            }
            .setNeutralButton("Key layout…") { _, _ ->
                SoftkeyProfileChooser.show(activity) { onChanged?.invoke() }
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
