package com.theonionsarewatching.yapchik

/**
 * The three-way switch for the softkey engine.
 *
 * - [ON]   — always show and handle softkeys.
 * - [OFF]  — never show or handle softkeys.
 * - [AUTO] — decide based on the device (see [Yapchik.autoDetector]).
 *            On a keypad phone this resolves to ON; on a touch phone, OFF.
 */
enum class SoftkeyMode {
    ON, OFF, AUTO;

    companion object {
        /** Lenient parse, used for persistence and settings storage. */
        @JvmStatic
        @JvmOverloads
        fun fromString(value: String?, fallback: SoftkeyMode = AUTO): SoftkeyMode =
            values().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: fallback
    }
}
