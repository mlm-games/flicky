package com.theonionsarewatching.yapchik

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.EnumMap

/**
 * The visual softkey bar: a slim strip pinned to the bottom of the screen with
 * LEFT and RIGHT labels. Yapchik injects and removes it for you — you
 * normally never construct this class yourself.
 *
 * Labels are also clickable, so hybrid devices (touch + keypad) can tap them.
 */
class SoftkeyBar(context: Context) : LinearLayout(context) {

    private val labels = EnumMap<SoftkeySlot, TextView>(SoftkeySlot::class.java)
    private var actions: Map<SoftkeySlot, SoftkeyAction> = emptyMap()

    init {
        val style = Yapchik.style
        orientation = VERTICAL
        // Force LTR so LEFT always means the physical left of the screen,
        // regardless of the app's locale direction.
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        setBackgroundColor(style.backgroundColor)
        isClickable = true // swallow stray touches so they don't hit views underneath

        // 1dp top hairline
        addView(
            View(context).apply { setBackgroundColor(style.dividerColor) },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(1))
        )

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val slots = listOf(
            SoftkeySlot.LEFT to (Gravity.LEFT or Gravity.CENTER_VERTICAL),
            SoftkeySlot.RIGHT to (Gravity.RIGHT or Gravity.CENTER_VERTICAL)
        )
        val hp = dp(style.horizontalPaddingDp)
        for ((slot, gravity) in slots) {
            val tv = TextView(context).apply {
                this.gravity = gravity
                setTextColor(style.textColor)
                textSize = style.textSizeSp
                // Medium weight instead of fake bold — synthetic bold clips
                // label glyphs on some keypad-phone font stacks.
                if (style.bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(hp, 0, hp, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setOnClickListener { actions[slot]?.let { a -> flash(slot); a.onPress.run() } }
                setOnLongClickListener {
                    actions[slot]?.onLongPress?.let { l -> flash(slot); l.run(); true } ?: false
                }
            }
            labels[slot] = tv
            row.addView(tv, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
    }

    /**
     * (Re)binds the labels + actions. Called by the controller on every
     * refresh/invalidate, so dynamic labels and [labelOverrides]
     * (from `setLabel`) are re-resolved each time.
     */
    @JvmOverloads
    fun bind(
        newActions: Map<SoftkeySlot, SoftkeyAction>,
        labelOverrides: Map<SoftkeySlot, CharSequence> = emptyMap()
    ) {
        actions = newActions
        for ((slot, tv) in labels) {
            val action = newActions[slot]
            tv.text = if (action == null) "" else (labelOverrides[slot] ?: action.label)
        }
    }

    /** Brief visual feedback when a softkey fires. */
    fun flash(slot: SoftkeySlot) {
        val tv = labels[slot] ?: return
        tv.setTextColor(Yapchik.style.pressedTextColor)
        tv.postDelayed({ tv.setTextColor(Yapchik.style.textColor) }, 150)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
