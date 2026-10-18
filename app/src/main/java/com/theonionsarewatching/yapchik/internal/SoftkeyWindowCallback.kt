package com.theonionsarewatching.yapchik.internal

import android.view.KeyEvent
import android.view.Window
import com.theonionsarewatching.yapchik.SoftkeyController

/**
 * Transparent [Window.Callback] wrapper. Everything is delegated to the
 * original callback (usually the Activity itself) except key events, which
 * are offered to the [SoftkeyController] first.
 *
 * This is what lets host apps integrate without overriding dispatchKeyEvent.
 */
internal class SoftkeyWindowCallback(
    private val wrapped: Window.Callback,
    private val controller: SoftkeyController
) : Window.Callback by wrapped {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (controller.handleKeyEvent(event)) return true
        return wrapped.dispatchKeyEvent(event)
    }
}
