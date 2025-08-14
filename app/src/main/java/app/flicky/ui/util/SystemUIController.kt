package app.flicky.ui.util

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Controls system UI elements like status bar
 */
@Composable
fun SystemUIController(
    showStatusBar: Boolean
) {
    val view = LocalView.current
    val context = LocalContext.current
    val window = remember { (context as? Activity)?.window }

    DisposableEffect(showStatusBar) {
        if (window != null) {
            if (showStatusBar) {
                showStatusBar(window, view)
            } else {
                hideStatusBar(window, view)
            }
        }

        onDispose { }
    }
}

/**
 * Non-composable function to safely update status bar visibility
 */
fun updateStatusBarVisibility(activity: Activity?, showStatusBar: Boolean) {
    if (activity == null || !activity.window.isActive) return

    try {
        val window = activity.window
        val decorView = window.decorView

        if (showStatusBar) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.show(WindowInsets.Type.statusBars())
            } else {
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let {
                    it.hide(WindowInsets.Type.statusBars())
                    it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or
                        View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        }
    } catch (e: Exception) {
        // Safely handle any exceptions
        e.printStackTrace()
    }
}

private fun showStatusBar(window: android.view.Window, view: View) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            view.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun hideStatusBar(window: android.view.Window, view: View) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            view.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or
                    View.SYSTEM_UI_FLAG_FULLSCREEN
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}