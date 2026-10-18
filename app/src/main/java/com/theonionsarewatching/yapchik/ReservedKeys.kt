package com.theonionsarewatching.yapchik

import android.view.KeyEvent

/**
 * Keys that must always keep their normal function: D-pad navigation
 * (including center/OK), ENTER, number and letter input, HOME, call keys,
 * volume, media, typing keys.
 *
 * The policy is a *blocklist*, not an allowlist: many keypad phones emit odd
 * vendor keycodes for their softkeys, so anything not recognized as a normal
 * key is allowed. Known-normal keys are refused.
 *
 * Used in two places:
 *  - Calibration ([SoftkeyProfileChooser.startCalibration]) refuses to capture
 *    reserved keys (plus BACK, which cancels the flow).
 *  - Runtime ([SoftkeyController]) never consumes a reserved key even if a
 *    hand-built profile contains one — so a bad profile can't break D-pad
 *    navigation, OK-clicks, or number input.
 *
 * Deliberately NOT reserved: SOFT_LEFT/SOFT_RIGHT, MENU, BACK, F1–F12,
 * SEARCH, and all unknown/vendor codes. BACK stays usable by explicit
 * profiles (the Menu/Back preset exists because many MTK devices' right
 * softkey *is* BACK) but can never be captured by calibration.
 */
object ReservedKeys {

    /** True when this keycode must keep its normal system/app function. */
    @JvmStatic
    fun isReserved(keyCode: Int): Boolean = when (keyCode) {
        // D-pad (incl. center/OK) and diagonals
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_DPAD_UP_LEFT,
        KeyEvent.KEYCODE_DPAD_DOWN_LEFT,
        KeyEvent.KEYCODE_DPAD_UP_RIGHT,
        KeyEvent.KEYCODE_DPAD_DOWN_RIGHT -> true

        // System / call / hardware keys
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_CALL,
        KeyEvent.KEYCODE_ENDCALL,
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_CAMERA,
        KeyEvent.KEYCODE_CLEAR,
        KeyEvent.KEYCODE_NOTIFICATION,
        KeyEvent.KEYCODE_APP_SWITCH -> true

        // Volume
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE -> true

        else -> when (keyCode) {
            // 0-9, *, #
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_POUND -> true
            // Letters A-Z
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> true
            // Typing block: comma..plus (TAB, SPACE, ENTER, DEL, punctuation,
            // NUM, HEADSETHOOK, FOCUS)
            in KeyEvent.KEYCODE_COMMA..KeyEvent.KEYCODE_PLUS -> true
            // Media playback + mic mute (85..91) and 126..130
            in KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE..KeyEvent.KEYCODE_MUTE -> true
            in KeyEvent.KEYCODE_MEDIA_PLAY..KeyEvent.KEYCODE_MEDIA_RECORD -> true
            // Page up/down
            in KeyEvent.KEYCODE_PAGE_UP..KeyEvent.KEYCODE_PAGE_DOWN -> true
            // ESC, FORWARD_DEL, modifier keys (CTRL/SHIFT/CAPS/META/FUNCTION)
            in KeyEvent.KEYCODE_ESCAPE..KeyEvent.KEYCODE_FUNCTION -> true
            // MOVE_HOME / MOVE_END / INSERT
            in KeyEvent.KEYCODE_MOVE_HOME..KeyEvent.KEYCODE_INSERT -> true
            // Numpad block (digits, ops, enter, parens)
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN -> true
            else -> false
        }
    }

    /**
     * Calibration rule (stricter than runtime): reserved keys can't be
     * captured, and neither can BACK (it cancels the flow — devices whose
     * right softkey IS the BACK key should use the Menu/Back preset).
     */
    internal fun isCapturable(keyCode: Int): Boolean =
        !isReserved(keyCode) && keyCode != KeyEvent.KEYCODE_BACK
}
