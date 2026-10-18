package com.theonionsarewatching.yapchik

import android.view.KeyEvent

/**
 * Maps physical keycodes to the LEFT and RIGHT softkey slots.
 *
 * Different keypad phones route their softkeys differently: proper
 * KEYCODE_SOFT_LEFT/RIGHT, MENU/BACK, or even F1/F2. [UNIVERSAL] (the
 * default) listens for all of the unambiguous ones at once; or pick a
 * specific built-in profile, declare keys per device in XML
 * ([Yapchik.loadDeviceProfiles]), or let the user calibrate their own with
 * [SoftkeyProfileChooser.startCalibration].
 *
 * The D-pad (including center/OK), ENTER, numbers, and all other normal keys
 * are never part of a profile — they keep their native behavior always.
 */
class KeyProfile(
    val id: String,
    val displayName: String,
    val leftKeys: Set<Int>,
    val rightKeys: Set<Int>
) {

    /** Which slot (if any) does this keycode belong to under this profile? */
    fun slotFor(keyCode: Int): SoftkeySlot? = when (keyCode) {
        in leftKeys -> SoftkeySlot.LEFT
        in rightKeys -> SoftkeySlot.RIGHT
        else -> null
    }

    /** Human-readable mapping summary, e.g. for a settings screen. */
    fun describe(): String {
        fun names(keys: Set<Int>) = keys.joinToString("/") {
            KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_")
        }
        return "L:${names(leftKeys)}  R:${names(rightKeys)}"
    }

    companion object {
        const val CUSTOM_ID = "custom"
        const val UNIVERSAL_ID = "universal"
        const val DEVICE_ID = "device"

        /**
         * Listens for every known softkey keycode at once — the default, and
         * the profile that works on most keypad phones with no setup.
         *
         * LEFT: SOFT_LEFT (1), MENU (82), F1 (131), plus MULTIFUNC_LEFT on
         *       ROMs that define it (see [resolveNames])
         * RIGHT: SOFT_RIGHT (2), F2 (132), plus MULTIFUNC_RIGHT likewise
         *
         * Deliberately excluded:
         * - BACK (4). It has a system meaning, so listening for it globally
         *   would swallow the back key on every screen with a RIGHT action.
         *   Devices whose right softkey *is* BACK should use [MENU_BACK].
         * - Anything reserved (D-pad, numbers, letters, volume… see
         *   [ReservedKeys]) and any keycode whose slot is ambiguous across
         *   devices — a code that means LEFT on one phone and RIGHT on
         *   another can't be mapped blind.
         *
         * Extra vendor keycodes can be appended from XML with
         * [Yapchik.loadDeviceProfiles]; a matching per-device entry in that
         * file replaces this profile entirely.
         */
        @JvmField
        val UNIVERSAL = KeyProfile(
            UNIVERSAL_ID, "All known softkeys",
            setOf(
                KeyEvent.KEYCODE_SOFT_LEFT,
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_F1
            ) + resolveNames("MULTIFUNC_LEFT"),
            setOf(
                KeyEvent.KEYCODE_SOFT_RIGHT,
                KeyEvent.KEYCODE_F2
            ) + resolveNames("MULTIFUNC_RIGHT")
        )

        /**
         * Resolve keycode *names* against the device's own keycode table —
         * the table `adb shell input keyevent NAME` uses.
         *
         * Vendor ROMs add their own softkey keycodes (e.g. keypad phones that
         * report their softkeys as `KEYCODE_MULTIFUNC_LEFT` /
         * `KEYCODE_MULTIFUNC_RIGHT`, kernel scancodes 357 / 444). Their
         * numeric values aren't in the public SDK and can differ per ROM, so
         * they're looked up by name at runtime instead of hard-coded: a name
         * this device doesn't define resolves to KEYCODE_UNKNOWN and is
         * dropped, which makes listing extra names free on phones that lack
         * them.
         *
         * The "KEYCODE_" prefix is optional.
         */
        @JvmStatic
        fun resolveNames(vararg names: String): Set<Int> {
            val out = LinkedHashSet<Int>()
            for (name in names) {
                val code = KeyEvent.keyCodeFromString(
                    if (name.startsWith("KEYCODE_")) name else "KEYCODE_$name"
                )
                if (code != KeyEvent.KEYCODE_UNKNOWN && !ReservedKeys.isReserved(code)) {
                    out.add(code)
                }
            }
            return out
        }

        /** KEYCODE_SOFT_LEFT / KEYCODE_SOFT_RIGHT. */
        @JvmField
        val STANDARD = KeyProfile(
            "standard", "Standard softkeys",
            setOf(KeyEvent.KEYCODE_SOFT_LEFT),
            setOf(KeyEvent.KEYCODE_SOFT_RIGHT)
        )

        /**
         * MENU as left, BACK as right — common on MTK keypad devices that never
         * emit real softkey codes. Note: BACK is only consumed on screens that
         * actually show a RIGHT action; otherwise it passes through normally.
         */
        @JvmField
        val MENU_BACK = KeyProfile(
            "menu_back", "Menu / Back keys",
            setOf(KeyEvent.KEYCODE_MENU),
            setOf(KeyEvent.KEYCODE_BACK)
        )

        /** F1 / F2 as the softkeys — seen on some feature-phone keymaps. */
        @JvmField
        val FUNCTION = KeyProfile(
            "function", "F1 / F2 keys",
            setOf(KeyEvent.KEYCODE_F1),
            setOf(KeyEvent.KEYCODE_F2)
        )

        @JvmField
        val BUILT_IN = listOf(UNIVERSAL, STANDARD, MENU_BACK, FUNCTION)

        /**
         * Build a custom profile, e.g. from calibration. Persisted by id.
         * Note: reserved normal keys (see [ReservedKeys]) are never consumed
         * at runtime even if included here.
         */
        @JvmStatic
        fun custom(left: Int, right: Int) = KeyProfile(
            CUSTOM_ID, "Custom (detected)",
            setOf(left), setOf(right)
        )

        /** Profile built from a matching `<device>` entry in a profiles XML. */
        @JvmStatic
        fun device(model: String, left: Set<Int>, right: Set<Int>) = KeyProfile(
            DEVICE_ID, "Device profile ($model)", left, right
        )
    }
}
