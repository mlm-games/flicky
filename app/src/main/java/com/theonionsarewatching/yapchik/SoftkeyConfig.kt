package com.theonionsarewatching.yapchik

import java.util.EnumMap

/**
 * Builder describing one layer of softkeys (LEFT and RIGHT).
 *
 * Used by:
 *  - [Yapchik.defaults] — the app-wide default layer
 *  - [SoftkeyProvider.onCreateSoftkeys] and `Softkeys.of(activity).set { }` /
 *    `.define(name) { }` — the per-screen layer
 *  - `Softkeys.of(activity).whenFocused(id) { }` — the focus layer
 *
 * Per-slot resolution across layers: focus overlay, then screen config, then
 * app defaults. A slot bound here overrides the layers below it; a slot
 * [remove]d here blocks the layers below it; a slot never mentioned falls
 * through. Call [noDefaults] to cut off the app-default layer entirely for
 * this config.
 *
 * The D-pad (including center/OK), ENTER, numbers, and other normal keys are
 * never touched — softkeys are strictly the LEFT/RIGHT keys of the active
 * [KeyProfile].
 */
class SoftkeyConfig {

    internal val actions = EnumMap<SoftkeySlot, SoftkeyAction>(SoftkeySlot::class.java)
    internal val blocked = mutableSetOf<SoftkeySlot>()

    /**
     * Whether slots not bound by this screen fall back to the app-wide
     * defaults ([Yapchik.defaults]). Default true. See [noDefaults].
     */
    var inheritDefaults: Boolean = true

    /**
     * Per-screen override of the global mode.
     * `null` (default) — follow the global [Yapchik.mode].
     * [SoftkeyMode.OFF] — softkeys never appear on this screen, even if globally on.
     * [SoftkeyMode.ON]  — softkeys always appear on this screen, even if globally auto/off.
     *
     * In a [Yapchik.defaults] block this sets the app-wide default instead
     * (same as [Yapchik.defaultScreenMode]): `screenMode = SoftkeyMode.OFF`
     * there means no softkeys anywhere until a screen opts in with
     * `screenMode = SoftkeyMode.ON`.
     */
    var screenMode: SoftkeyMode? = null

    // ------------------------------------------------------- static labels

    /**
     * Bind the LEFT softkey.
     * `left("Menu") { ... }` — plain.
     * `left("Zoom -", visibleIf = { zoom > 1 }) { ... }` — conditional; the
     * key passes through and the label is hidden while the condition is false.
     */
    @JvmOverloads
    fun left(
        label: CharSequence,
        onLongPress: Runnable? = null,
        visibleIf: (() -> Boolean)? = null,
        onPress: Runnable
    ) = put(SoftkeySlot.LEFT, label, onLongPress, visibleIf, onPress)

    /** Bind the RIGHT softkey. */
    @JvmOverloads
    fun right(
        label: CharSequence,
        onLongPress: Runnable? = null,
        visibleIf: (() -> Boolean)? = null,
        onPress: Runnable
    ) = put(SoftkeySlot.RIGHT, label, onLongPress, visibleIf, onPress)

    // ------------------------------------------------------ dynamic labels

    /**
     * Bind the LEFT softkey with a dynamic label, re-read on every
     * invalidate/refresh: `left(label = { "Zoom (${zoom}x)" }) { ... }`.
     */
    fun left(
        label: () -> CharSequence,
        onLongPress: Runnable? = null,
        visibleIf: (() -> Boolean)? = null,
        onPress: Runnable
    ) = putDynamic(SoftkeySlot.LEFT, label, onLongPress, visibleIf, onPress)

    /** Dynamic-label variant of [right]. */
    fun right(
        label: () -> CharSequence,
        onLongPress: Runnable? = null,
        visibleIf: (() -> Boolean)? = null,
        onPress: Runnable
    ) = putDynamic(SoftkeySlot.RIGHT, label, onLongPress, visibleIf, onPress)

    // ------------------------------------------------------------- generic

    /** Generic form of the static-label builders (Java-friendly). */
    @JvmOverloads
    fun put(
        slot: SoftkeySlot,
        label: CharSequence,
        onLongPress: Runnable? = null,
        visibleIf: (() -> Boolean)? = null,
        onPress: Runnable
    ) {
        actions[slot] = SoftkeyAction(label, onPress, onLongPress, null, visibleIf)
        blocked.remove(slot)
    }

    /** Generic form of the dynamic-label builders. */
    fun putDynamic(
        slot: SoftkeySlot,
        label: () -> CharSequence,
        onLongPress: Runnable? = null,
        visibleIf: (() -> Boolean)? = null,
        onPress: Runnable
    ) {
        actions[slot] = SoftkeyAction("", onPress, onLongPress, label, visibleIf)
        blocked.remove(slot)
    }

    /**
     * Unbind a slot *and block lower layers from filling it*. On a screen
     * config this suppresses the app-default action for that slot; the
     * physical key passes through to the app.
     */
    fun remove(slot: SoftkeySlot) {
        actions.remove(slot)
        blocked.add(slot)
    }

    /**
     * This config does not inherit any slots from the app-wide defaults.
     * Equivalent to `inheritDefaults = false`.
     */
    fun noDefaults() {
        inheritDefaults = false
    }

    val isEmpty: Boolean get() = actions.isEmpty()

    /** Resolve one slot against this layer: bound action, explicit block, or fall through. */
    internal fun lookup(slot: SoftkeySlot): Resolution = when {
        actions.containsKey(slot) -> Resolution.Bound(actions.getValue(slot))
        slot in blocked -> Resolution.Blocked
        else -> Resolution.FallThrough
    }

    internal sealed class Resolution {
        class Bound(val action: SoftkeyAction) : Resolution()
        object Blocked : Resolution()
        object FallThrough : Resolution()
    }
}
