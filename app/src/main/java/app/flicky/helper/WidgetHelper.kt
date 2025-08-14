package app.flicky.helper

import android.app.Activity
import android.app.ActivityOptions
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Helper class to manage external widgets
 */
class WidgetHelper(private val context: Context, private val appWidgetManager: AppWidgetManager, private val appWidgetHost: AppWidgetHost) {
    companion object {
        private const val TAG = "WidgetHelper"
    }


    /**
     * Check if a widget requires configuration
     */
    fun needsConfiguration(widgetId: Int): Boolean {
        return try {
            val providerInfo = appWidgetManager.getAppWidgetInfo(widgetId)
            val needsConfig = providerInfo?.configure != null
            Log.d(TAG, "Widget ID: $widgetId needs configuration: $needsConfig")
            needsConfig
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if widget needs configuration: ${e.message}")
            false
        }
    }

    /**
     * Create configuration intent for a widget
     */
    fun createConfigurationIntent(widgetId: Int): Intent? {
        return try {
            val providerInfo = appWidgetManager.getAppWidgetInfo(widgetId) ?: return null
            if (providerInfo.configure == null) return null

            Log.d(TAG, "Creating configuration intent for widget ID: $widgetId")
            Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = providerInfo.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                // Add this flag to allow background activity starts for Android 14+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    addFlags(Intent.FLAG_ACTIVITY_MATCH_EXTERNAL)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating configuration intent: ${e.message}")
            null
        }
    }

    /**
     * Starts the configuration activity for an existing widget
     * @return true if the configuration activity was started successfully
     */
    fun startWidgetConfiguration(
        activity: Activity,
        widgetId: Int,
        requestCode: Int
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val widgetInfo = appWidgetManager.getAppWidgetInfo(widgetId)
                if (widgetInfo?.widgetFeatures?.and(AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0) {
                    appWidgetHost.startAppWidgetConfigureActivityForResult(
                        activity,
                        widgetId,
                        0, // Unused in current implementations
                        requestCode,
                        if (Build.VERSION.SDK_INT >= 34) {
                            ActivityOptions.makeBasic()
                                .setPendingIntentBackgroundActivityStartMode(
                                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                )
                                .toBundle()
                        } else {
                            null
                        }
                    )
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start widget configuration", e)
            false
        }
    }


}