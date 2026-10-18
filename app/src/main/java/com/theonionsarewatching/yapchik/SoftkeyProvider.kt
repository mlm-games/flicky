package com.theonionsarewatching.yapchik

/**
 * Implement this on an Activity to declare its softkeys statically.
 * Yapchik calls [onCreateSoftkeys] automatically when the Activity is created.
 *
 * ```kotlin
 * class MainActivity : Activity(), SoftkeyProvider {
 *     override fun onCreateSoftkeys(softkeys: SoftkeyConfig) {
 *         softkeys.left("Menu") { openMenu() }
 *         softkeys.right("Exit") { finish() }
 *     }
 * }
 * ```
 *
 * For dynamic screens you can skip this interface entirely and call
 * `Softkeys.of(activity).set { ... }` / `.update { ... }` whenever you like.
 */
interface SoftkeyProvider {
    fun onCreateSoftkeys(softkeys: SoftkeyConfig)
}
