package com.theonionsarewatching.yapchik

import android.app.Activity

/** Convenience accessors — the everyday API surface. */
object Softkeys {

    /**
     * The controller for this screen. Creates + attaches one on first use.
     *
     * ```kotlin
     * Softkeys.of(this).set {
     *     left("Save") { save() }
     *     right("Back") { finish() }
     * }
     * ```
     */
    @JvmStatic
    fun of(activity: Activity): SoftkeyController = Yapchik.controllerFor(activity)

    /** Is the softkey bar currently visible in this Activity? */
    @JvmStatic
    fun isShownIn(activity: Activity): Boolean =
        Yapchik.controllerOrNull(activity)?.isBarShown == true
}
