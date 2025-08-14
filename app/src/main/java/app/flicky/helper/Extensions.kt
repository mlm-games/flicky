package app.flicky.helper

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import app.flicky.data.AnimationConstants


/**
 * Show a toast message with flexible input types
 * @param message Can be a String, StringRes Int, or null
 * @param duration Toast duration (LENGTH_SHORT or LENGTH_LONG)
 */
fun Context.showToast(
    message: Any?,
    duration: Int = Toast.LENGTH_SHORT
) {
    when (message) {
        is String -> {
            if (message.isNotBlank()) {
                Toast.makeText(this, message, duration).show()
            }
        }
        is Int -> {
            try {
                Toast.makeText(this, getString(message), duration).show()
            } catch (_: Exception) {
                // Invalid resource ID, ignore
            }
        }
        else -> {
            // Null or unsupported type, do nothing
        }
    }
}



fun Context.openSearch(query: String? = null) {
    val intent = Intent(Intent.ACTION_WEB_SEARCH)
    intent.putExtra(SearchManager.QUERY, query ?: "")
    startActivity(intent)
}

fun Context.isEinkDisplay(): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val refreshRate = (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
                .getDisplay(Display.DEFAULT_DISPLAY)
                .refreshRate
            refreshRate <= AnimationConstants.MIN_ANIM_REFRESH_RATE
        } else {
            // Legacy API (pre-Android 11)
            @Suppress("DEPRECATION")
            val display = (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            display.refreshRate <= AnimationConstants.MIN_ANIM_REFRESH_RATE
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}