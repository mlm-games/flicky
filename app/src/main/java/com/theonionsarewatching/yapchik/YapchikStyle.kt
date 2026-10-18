package com.theonionsarewatching.yapchik

import android.content.Context

/**
 * Visual styling for the softkey bar. All bars share this one instance
 * (see [Yapchik.style]). Change values and call [Yapchik.refreshAll] to
 * re-render bars already on screen.
 */
class YapchikStyle {
    /** Bar background. Default: near-black. */
    var backgroundColor: Int = 0xFF111111.toInt()

    /** Label text color. */
    var textColor: Int = 0xFFF2F2F2.toInt()

    /** Label color flashed briefly when a softkey fires. */
    var pressedTextColor: Int = 0xFF80CBC4.toInt()

    /** 1dp hairline drawn along the top edge of the bar. */
    var dividerColor: Int = 0x33FFFFFF

    /** Bar height in dp. */
    var heightDp: Int = 42

    /** Label text size in sp. */
    var textSizeSp: Float = 15f

    /**
     * Emphasized labels (recommended for small keypad-phone screens).
     * Rendered as sans-serif-medium, not synthetic bold — fake bold clips
     * glyphs on some keypad-phone font stacks.
     */
    var bold: Boolean = true

    /** Horizontal padding inside each label, in dp. */
    var horizontalPaddingDp: Int = 10

    fun heightPx(context: Context): Int =
        (heightDp * context.resources.displayMetrics.density).toInt()
}
