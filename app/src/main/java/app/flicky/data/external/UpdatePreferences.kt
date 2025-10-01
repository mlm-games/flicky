package app.flicky.data.external
// Similar to Droid-ify (can you call it clean-room?)
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class UpdatesPreference(
    val ignoreUpdates: Boolean = false,
    val ignoreVersionCode: Long = 0L,
    val preferredRepoUrl: String? = null,
    val lockToRepo: Boolean = true,
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
                ignoreVersionCode = obj.optLong("ignoreVersionCode", 0L),
                preferredRepoUrl = obj.optString("preferredRepoUrl", "")
                    .takeIf { it.isNotBlank() },
                lockToRepo = obj.optBoolean("lockToRepo", true)
            )
        } catch (_: Exception) {
            UpdatesPreference()
        }
    }

    operator fun set(packageName: String, pref: UpdatesPreference) {
        val obj = JSONObject().apply {
            put("ignoreUpdates", pref.ignoreUpdates)
            put("ignoreVersionCode", pref.ignoreVersionCode)
            put("preferredRepoUrl", pref.preferredRepoUrl ?: "")
            put("lockToRepo", pref.lockToRepo)
        }
        prefs.edit { putString(packageName, obj.toString()) }
    }

    fun setPreferredRepo(packageName: String, repoUrl: String?, lock: Boolean = true) {
        val cur = get(packageName)
        set(packageName, cur.copy(preferredRepoUrl = repoUrl, lockToRepo = lock))
    }

    fun observe(packageName: String): Flow<UpdatesPreference> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == packageName) trySend(get(packageName)).isSuccess
        }
        // emit current
        trySend(get(packageName))
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun observeAll(): Flow<Map<String, UpdatesPreference>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, _ ->
            val allPrefs = sharedPreferences.all.mapNotNull { (key, value) ->
                (value as? String)?.let { json ->
                    try {
                        val obj = JSONObject(json)
                        key to UpdatesPreference(
                            ignoreUpdates = obj.optBoolean("ignoreUpdates", false),
                            ignoreVersionCode = obj.optLong("ignoreVersionCode", 0L),
                            preferredRepoUrl = obj.optString("preferredRepoUrl", "")
                                .takeIf { it.isNotBlank() },
                            lockToRepo = obj.optBoolean("lockToRepo", true)
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
            }.toMap()
            trySend(allPrefs)
        }

        listener.onSharedPreferenceChanged(prefs, null)

        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}