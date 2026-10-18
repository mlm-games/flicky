package com.theonionsarewatching.yapchik

/**
 * A label + behavior bound to one softkey slot.
 *
 * [onPress] fires on a normal press (key up).
 * [onLongPress] (optional) fires once the key auto-repeats (~500 ms held);
 * when it fires, the normal press is suppressed for that key cycle.
 *
 * [labelProvider] (optional) supplies the label dynamically; it is re-read on
 * every [SoftkeyController.invalidate] / refresh, so labels can reflect state
 * ("Zoom - (3x)").
 *
 * [visibleIf] (optional) is evaluated on every invalidate/refresh. When it
 * returns false the slot is hidden AND its physical key passes through to the
 * app (nothing is consumed). Call [SoftkeyController.invalidate] after the
 * state it reads changes.
 *
 * Press callbacks are [Runnable] so the API is equally usable from Kotlin
 * (trailing lambdas SAM-convert) and Java.
 */
class SoftkeyAction @JvmOverloads constructor(
    label: CharSequence = "",
    val onPress: Runnable,
    val onLongPress: Runnable? = null,
    val labelProvider: (() -> CharSequence)? = null,
    val visibleIf: (() -> Boolean)? = null
) {
    private val staticLabel: CharSequence = label

    /** The label to display right now (dynamic provider wins over static text). */
    val label: CharSequence
        get() = labelProvider?.invoke() ?: staticLabel

    /** Whether the slot should currently be shown / its key consumed. */
    internal val isVisibleNow: Boolean
        get() = visibleIf?.invoke() != false
}
