package com.theonionsarewatching.yapchik

import android.app.Activity
import android.app.AlertDialog
import android.view.KeyEvent
import android.widget.Toast

/**
 * Ready-made dialogs for picking which physical keys act as softkeys.
 *
 * [show] — single-choice list of the built-in [KeyProfile]s plus a
 * "detect my keys" option that runs [startCalibration].
 *
 * Uses the framework AlertDialog, so it works in any app with no extra
 * dependencies.
 */
object SoftkeyProfileChooser {

    /** Show the profile chooser. [onDone] fires after a profile is applied. */
    @JvmStatic
    @JvmOverloads
    fun show(activity: Activity, onDone: ((KeyProfile) -> Unit)? = null) {
        val builtIn = KeyProfile.BUILT_IN
        val items = (builtIn.map { "${it.displayName}\n${it.describe()}" } +
            "Custom — detect by pressing my keys…").toTypedArray()

        val current = Yapchik.keyProfile
        val checked =
            if (current.id == KeyProfile.CUSTOM_ID) items.size - 1
            else builtIn.indexOfFirst { it.id == current.id }

        AlertDialog.Builder(activity)
            .setTitle("Softkey key layout")
            .setSingleChoiceItems(items, checked) { dialog, which ->
                if (which < builtIn.size) {
                    Yapchik.keyProfile = builtIn[which]
                    dialog.dismiss()
                    onDone?.invoke(Yapchik.keyProfile)
                } else {
                    dialog.dismiss()
                    startCalibration(activity, onDone)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Interactive calibration: asks the user to press LEFT, then RIGHT,
     * captures the two keycodes, and saves them as the custom profile.
     *
     * Normal keys are never captured ([ReservedKeys]): D-pad (including OK),
     * numbers, *, #, letters, volume, call keys, typing keys and so on keep
     * working as usual and just show a hint. Vendor-specific/unknown codes
     * ARE accepted — many keypad phones use odd keycodes for their softkeys,
     * so the filter is a blocklist of known-normal keys, not an allowlist.
     *
     * BACK cancels the flow — so if a device's right softkey *is* the BACK
     * key, use the built-in "Menu / Back keys" preset instead of calibration.
     */
    @JvmStatic
    @JvmOverloads
    fun startCalibration(activity: Activity, onDone: ((KeyProfile) -> Unit)? = null) {
        val prompts = arrayOf(
            "Press your LEFT softkey",
            "Press your RIGHT softkey"
        )
        val suffix = "\n\n(BACK cancels)"
        val captured = IntArray(2)
        var step = 0

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Detect softkeys (1 of 2)")
            .setMessage(prompts[0] + suffix)
            .setNegativeButton("Cancel", null)
            .create()

        fun keyName(keyCode: Int) =
            KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")

        dialog.setOnKeyListener { d, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                return@setOnKeyListener false // let the dialog cancel itself
            }
            if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                return@setOnKeyListener true
            }

            // Reserved keys (D-pad, numbers, letters, volume…) are never
            // captured — show a hint on key-up and let the system keep
            // handling them (so the D-pad still moves focus in this dialog).
            if (!ReservedKeys.isCapturable(keyCode)) {
                if (event.action == KeyEvent.ACTION_UP) {
                    dialog.setMessage(
                        prompts[step] + suffix +
                            "\n\n${keyName(keyCode)} is a normal key and stays as-is — " +
                            "press the actual softkey."
                    )
                }
                return@setOnKeyListener false
            }

            if (event.action == KeyEvent.ACTION_UP) {
                // The same key can't serve both slots.
                if (step == 1 && captured[0] == keyCode) {
                    dialog.setMessage(
                        prompts[step] + suffix +
                            "\n\n${keyName(keyCode)} is already your LEFT key — " +
                            "press a different key."
                    )
                    return@setOnKeyListener true
                }

                captured[step] = keyCode
                step++
                if (step == 2) {
                    val profile = KeyProfile.custom(captured[0], captured[1])
                    Yapchik.keyProfile = profile
                    d.dismiss()
                    Toast.makeText(
                        activity,
                        "Saved custom layout — ${profile.describe()}",
                        Toast.LENGTH_LONG
                    ).show()
                    onDone?.invoke(profile)
                } else {
                    dialog.setTitle("Detect softkeys (2 of 2)")
                    dialog.setMessage(prompts[step] + suffix)
                }
            }
            true // consume capturable keys (incl. DOWN) while calibrating
        }
        dialog.show()
    }
}
