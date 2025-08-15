package app.flicky.data.external
// Similar to Droid-ify (can you call it clean-room?)
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import androidx.core.content.edit

data class UpdatesPreference(
    val ignoreUpdates: Boolean = false,
    val ignoreVersionCode: Long = 0L
)

object UpdatesPreferences {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("update_preferences", Context.MODE_PRIVATE)
    }

    operator fun get(packageName: String): UpdatesPreference {
        val json = prefs.getString(packageName, null) ?: return UpdatesPreference()
        return try {
            val obj = JSONObject(json)
            UpdatesPreference(
                ignoreUpdates = obj.optBoolean("ignoreUpdates", false),
                ignoreVersionCode = obj.optLong("ignoreVersionCode", 0L)
            )
        } catch (_: Exception) {
            UpdatesPreference()
        }
    }

    operator fun set(packageName: String, pref: UpdatesPreference) {
        val obj = JSONObject().apply {
            put("ignoreUpdates", pref.ignoreUpdates)
            put("ignoreVersionCode", pref.ignoreVersionCode)
        }
        prefs.edit { putString(packageName, obj.toString()) }
    }
}