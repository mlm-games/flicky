package com.theonionsarewatching.yapchik

import android.app.Activity
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.widget.FrameLayout
import com.theonionsarewatching.yapchik.internal.SoftkeyWindowCallback
import java.util.EnumMap

/**
 * One per Activity. Owns that screen's softkey configuration, injects/removes
 * the [SoftkeyBar], keeps the content view padded, and intercepts the physical
 * keys via a [Window.Callback] wrapper — host apps never override
 * dispatchKeyEvent themselves.
 *
 * Obtain via `Softkeys.of(activity)`.
 *
 * ## How a slot is resolved (highest priority first)
 * 1. **Focus overlay** — a [whenFocused] config whose view currently has focus
 * 2. **Screen config** — the active named set ([define]/[activate]) or the
 *    config from [set]/[SoftkeyProvider]
 * 3. **App defaults** — [Yapchik.defaults], unless the screen config called
 *    [SoftkeyConfig.noDefaults] or [SoftkeyConfig.remove]d that slot
 *
 * Then filters: [setVisible]`(slot, false)` and the binding's `visibleIf`.
 * A hidden/unbound slot never consumes its physical key.
 * Label: [setLabel] override, else dynamic label lambda, else static text.
 */
class SoftkeyController internal constructor(private val activity: Activity) {

    private var mainConfig = SoftkeyConfig()
    private val namedSets = HashMap<String, SoftkeyConfig>()
    private var activeSet: String? = null

    private val focusConfigs = HashMap<Int, SoftkeyConfig>()
    private var focusListenerAttached = false
    private val focusListener =
        ViewTreeObserver.OnGlobalFocusChangeListener { _, _ -> refresh() }

    private val labelOverrides = EnumMap<SoftkeySlot, CharSequence>(SoftkeySlot::class.java)
    private val forcedHidden = mutableSetOf<SoftkeySlot>()

    private var bar: SoftkeyBar? = null
    private var originalCallback: Window.Callback? = null

    private var insetView: View? = null
    private var addedInsetBottom = 0
    private var navHiddenByBar = false

    private val longPressFired = mutableSetOf<SoftkeySlot>()

    /** The bindings currently in effect (post-resolution, post-filters). */
    private var currentBindings: Map<SoftkeySlot, SoftkeyAction> = emptyMap()

    private var _screenMode: SoftkeyMode? = null

    /** screenMode declared in the app-wide defaults block; cached per refresh. */
    private var defaultsScreenMode: SoftkeyMode? = null

    // ------------------------------------------------------------------ state

    /**
     * Per-screen override; `null` = follow the global [Yapchik.mode].
     * Setting it takes effect immediately. Can also be set declaratively via
     * [SoftkeyConfig.screenMode] inside a `set { }` / provider / `define { }`
     * block.
     */
    var screenMode: SoftkeyMode?
        get() = _screenMode
        set(value) {
            _screenMode = value
            refresh()
        }

    /** Is the bar visible on this screen right now? */
    val isBarShown: Boolean
        get() = bar?.parent != null

    /**
     * Resolved state for this screen. Mode resolution, highest first:
     * this screen's [screenMode], the mode set in a [Yapchik.defaults] block,
     * [Yapchik.defaultScreenMode], then the global [Yapchik.mode].
     * Host apps can check this to adjust layout.
     */
    val isActiveOnThisScreen: Boolean
        get() {
            val mode = _screenMode ?: defaultsScreenMode ?: Yapchik.defaultScreenMode
            return mode?.let { Yapchik.resolve(it) } ?: Yapchik.isActive
        }

    /** Name of the active named set, or null when using the plain config. */
    val activeSetName: String? get() = activeSet

    // ------------------------------------------------------------- config API

    /**
     * Replace this screen's softkeys wholesale. Also deactivates any named set
     * and clears [setLabel]/[setVisible] overrides. Slots not bound in the
     * block still fall back to the app defaults unless the block calls
     * [SoftkeyConfig.noDefaults] or removes them.
     */
    fun set(block: SoftkeyConfig.() -> Unit) {
        val fresh = SoftkeyConfig()
        fresh.block()
        fresh.screenMode?.let { _screenMode = it }
        mainConfig = fresh
        activeSet = null
        labelOverrides.clear()
        forcedHidden.clear()
        refresh()
    }

    /**
     * Mutate the *currently shown* config in place (the active named set, or
     * the plain config) — e.g. rebind one key without clearing the rest.
     */
    fun update(block: SoftkeyConfig.() -> Unit) {
        val target = baseConfig()
        target.block()
        target.screenMode?.let { _screenMode = it; target.screenMode = null }
        refresh()
    }

    /** Remove all softkeys from this screen, including inherited defaults. */
    fun clear() = set { noDefaults() }

    // ------------------------------------------------------------- named sets

    /**
     * Define a named softkey set for this screen without activating it.
     * Useful for screens with distinct modes (browse / map / edit):
     * define each set once in onCreate, then switch with [activate].
     */
    fun define(name: String, block: SoftkeyConfig.() -> Unit) {
        namedSets[name] = SoftkeyConfig().apply(block)
        if (name == activeSet) refresh()
    }

    /**
     * Switch this screen to a named set (must have been [define]d), or back to
     * the plain [set]-config with `activate(null)`. If the set declares a
     * [SoftkeyConfig.screenMode], it is applied on activation.
     */
    fun activate(name: String?) {
        require(name == null || namedSets.containsKey(name)) {
            "No softkey set named \"$name\" — call define(\"$name\") { ... } first."
        }
        activeSet = name
        baseConfig().screenMode?.let { _screenMode = it }
        refresh()
    }

    // -------------------------------------------------- imperative overrides

    /**
     * Relabel one slot without touching its action. Sticky until changed,
     * cleared by [set]/[clear]. `setLabel(slot, null)` reverts to the
     * binding's own label.
     */
    fun setLabel(slot: SoftkeySlot, label: CharSequence?) {
        if (label == null) labelOverrides.remove(slot) else labelOverrides[slot] = label
        refresh()
    }

    /**
     * Imperatively hide/show one slot (hidden ⇒ key passes through). This is
     * the quick alternative to a `visibleIf` condition. Sticky until changed,
     * cleared by [set]/[clear].
     */
    fun setVisible(slot: SoftkeySlot, visible: Boolean) {
        if (visible) forcedHidden.remove(slot) else forcedHidden.add(slot)
        refresh()
    }

    // --------------------------------------------------------- focus overlays

    /**
     * Overlay softkeys while a specific view (or any of its descendants) has
     * focus — the classic feature-phone pattern where softkeys change as the
     * D-pad moves between fields:
     *
     * ```kotlin
     * Softkeys.of(this).whenFocused(R.id.search_box) {
     *     left("Clear") { searchBox.text.clear() }
     * }
     * ```
     *
     * Slots bound here override the screen config while focused; slots
     * `remove`d here are blocked; everything else falls through. Re-register
     * (same id) to replace; [clearWhenFocused] to remove.
     */
    fun whenFocused(viewId: Int, block: SoftkeyConfig.() -> Unit) {
        focusConfigs[viewId] = SoftkeyConfig().apply(block)
        refresh()
    }

    /** Remove a [whenFocused] overlay. */
    fun clearWhenFocused(viewId: Int) {
        focusConfigs.remove(viewId)
        refresh()
    }

    // ------------------------------------------------------------- lifecycle

    internal fun attach() {
        val window = activity.window ?: return
        if (window.callback !is SoftkeyWindowCallback) {
            originalCallback = window.callback
            window.callback = SoftkeyWindowCallback(window.callback, this)
        }
    }

    internal fun detach() {
        val window = activity.window
        if (window != null && window.callback is SoftkeyWindowCallback) {
            originalCallback?.let { window.callback = it }
        }
        if (focusListenerAttached) {
            activity.window?.peekDecorView()?.viewTreeObserver?.let {
                if (it.isAlive) it.removeOnGlobalFocusChangeListener(focusListener)
            }
            focusListenerAttached = false
        }
        removeBar()
    }

    internal fun rebuildFromProvider() {
        val provider = activity as? SoftkeyProvider
        if (provider != null) {
            val fresh = SoftkeyConfig()
            provider.onCreateSoftkeys(fresh)
            fresh.screenMode?.let { _screenMode = it }
            mainConfig = fresh
            activeSet = null
        }
        refresh()
    }

    // -------------------------------------------------------------- rendering

    /**
     * Re-resolve everything — `visibleIf` conditions, dynamic labels, focus —
     * and re-render. Cheap; call after any state your conditions/labels read
     * has changed (the softkey equivalent of `invalidateOptionsMenu()`).
     */
    fun invalidate() = refresh()

    /** Same as [invalidate]; kept for symmetry with [Yapchik.refreshAll]. */
    fun refresh() {
        val base = baseConfig()
        val defaults =
            if (base.inheritDefaults) Yapchik.buildDefaults(activity) else null
        defaultsScreenMode = defaults?.screenMode
        currentBindings =
            if (isActiveOnThisScreen) computeBindings(base, defaults) else emptyMap()
        ensureFocusListener()
        if (currentBindings.isNotEmpty()) showBar() else removeBar()
    }

    private fun baseConfig(): SoftkeyConfig =
        activeSet?.let { namedSets[it] } ?: mainConfig

    private fun computeBindings(
        base: SoftkeyConfig,
        defaults: SoftkeyConfig?
    ): Map<SoftkeySlot, SoftkeyAction> {
        val overlay = currentFocusOverlay()

        val out = EnumMap<SoftkeySlot, SoftkeyAction>(SoftkeySlot::class.java)
        for (slot in SoftkeySlot.values()) {
            if (slot in forcedHidden) continue
            val action = resolveSlot(slot, overlay, base, defaults) ?: continue
            if (!action.isVisibleNow) continue
            out[slot] = action
        }
        return out
    }

    private fun resolveSlot(
        slot: SoftkeySlot,
        overlay: SoftkeyConfig?,
        base: SoftkeyConfig,
        defaults: SoftkeyConfig?
    ): SoftkeyAction? {
        if (overlay != null) {
            when (val r = overlay.lookup(slot)) {
                is SoftkeyConfig.Resolution.Bound -> return r.action
                is SoftkeyConfig.Resolution.Blocked -> return null
                else -> Unit // fall through
            }
        }
        when (val r = base.lookup(slot)) {
            is SoftkeyConfig.Resolution.Bound -> return r.action
            is SoftkeyConfig.Resolution.Blocked -> return null
            else -> Unit
        }
        return defaults?.actions?.get(slot)
    }

    private fun currentFocusOverlay(): SoftkeyConfig? {
        if (focusConfigs.isEmpty()) return null
        var v: View? = activity.currentFocus ?: return null
        while (v != null) {
            val id = v.id
            if (id != View.NO_ID) focusConfigs[id]?.let { return it }
            v = v.parent as? View
        }
        return null
    }

    private fun ensureFocusListener() {
        if (focusListenerAttached || focusConfigs.isEmpty()) return
        val decor = activity.window?.peekDecorView() ?: return
        decor.viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)
        focusListenerAttached = true
    }

    private fun contentFrame(): ViewGroup? {
        val window = activity.window ?: return null
        // Don't force decor creation before setContentView — that would break
        // requestWindowFeature() in the host Activity. We simply retry on
        // onStart/onResume, by which time the decor exists.
        if (window.peekDecorView() == null) return null
        return activity.findViewById(android.R.id.content)
    }

    private fun showBar() {
        val content = contentFrame() ?: return
        val heightPx = Yapchik.style.heightPx(activity)

        var b = bar
        if (b == null) {
            b = SoftkeyBar(activity)
            bar = b
        }

        // FRAMEWORK-theme nav guard: vendor builds of Theme.DeviceDefault can
        // keep drawing their bottom strip even after a navbar-hide request
        // (which drops the navbar inset, expanding the window under the
        // strip). Grow the bar by the guard height with the labels anchored
        // in the top portion so they always stay above such a strip.
        // MATERIAL3/APPCOMPAT themes ship in the APK and don't need this.
        val policy = Yapchik.resolvedNavBarPolicy(activity)
        val hideInEffect = policy == Yapchik.NavBarPolicy.HIDE_ALWAYS ||
            policy == Yapchik.NavBarPolicy.HIDE_WITH_BAR
        val guardPx =
            if (hideInEffect && Yapchik.themeKind(activity) == Yapchik.ThemeKind.FRAMEWORK)
                Yapchik.navGuardPx(activity)
            else 0
        val totalHeight = heightPx + guardPx
        b.setPadding(0, 0, 0, guardPx)
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, totalHeight, Gravity.BOTTOM
        )
        if (b.parent == null) {
            content.addView(b, lp)
        } else {
            // Re-apply full params (incl. BOTTOM gravity) on every show so the
            // bar always sits flush with the bottom edge of the app window.
            b.layoutParams = lp
        }
        b.bringToFront()
        b.bind(currentBindings, labelOverrides)
        applyInset(content, totalHeight)

        // HIDE_WITH_BAR: navbar hidden exactly while a softkey bar is shown.
        // Re-asserted on every refresh so it survives dialogs/focus changes.
        if (Yapchik.resolvedNavBarPolicy(activity) == Yapchik.NavBarPolicy.HIDE_WITH_BAR) {
            Yapchik.hideSystemNavigation(activity)
            navHiddenByBar = true
        }
    }

    private fun removeBar() {
        bar?.let { (it.parent as? ViewGroup)?.removeView(it) }
        clearInset()
        if (navHiddenByBar) {
            navHiddenByBar = false
            Yapchik.restoreSystemNavigation(activity)
        }
    }

    private fun applyInset(content: ViewGroup, h: Int) {
        if (!Yapchik.autoInsetContent) return
        val target = (0 until content.childCount)
            .map(content::getChildAt)
            .firstOrNull { it !== bar } ?: return
        if (insetView === target && addedInsetBottom == h) return
        clearInset()
        target.setPadding(
            target.paddingLeft, target.paddingTop,
            target.paddingRight, target.paddingBottom + h
        )
        insetView = target
        addedInsetBottom = h
    }

    private fun clearInset() {
        insetView?.let { v ->
            v.setPadding(
                v.paddingLeft, v.paddingTop, v.paddingRight,
                (v.paddingBottom - addedInsetBottom).coerceAtLeast(0)
            )
        }
        insetView = null
        addedInsetBottom = 0
    }

    // ----------------------------------------------------------- key handling

    /**
     * Called from the window-callback wrapper for every key event.
     * Returns true when the event was consumed by a softkey.
     *
     * Rules:
     * - Nothing is touched unless softkeys are active on this screen.
     * - A key is only consumed when its slot resolves to a *visible* action —
     *   unbound, hidden (`visibleIf` false / [setVisible] false), and blocked
     *   slots pass their keys through untouched (e.g. BACK with no RIGHT
     *   action behaves normally).
     * - Long press fires on key auto-repeat (~500 ms) and suppresses the
     *   short press for that cycle.
     */
    internal fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!isActiveOnThisScreen) return false
        val slot = Yapchik.keyProfile.slotFor(event.keyCode) ?: return false
        // Safety net: normal keys (D-pad incl. OK, numbers, letters, volume,
        // calls…) are never consumed as softkeys, even if a hand-built
        // profile contains them.
        if (ReservedKeys.isReserved(event.keyCode)) return false
        val action = currentBindings[slot] ?: return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    longPressFired.remove(slot)
                } else if (action.onLongPress != null && slot !in longPressFired) {
                    longPressFired.add(slot)
                    bar?.flash(slot)
                    action.onLongPress.run()
                }
                return true
            }
            KeyEvent.ACTION_UP -> {
                val alreadyFired = slot in longPressFired
                longPressFired.remove(slot)
                if (!alreadyFired && !event.isCanceled) {
                    bar?.flash(slot)
                    action.onPress.run()
                }
                return true
            }
        }
        return true
    }
}
