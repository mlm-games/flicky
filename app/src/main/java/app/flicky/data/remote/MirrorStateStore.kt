package app.flicky.data.remote

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

private val Context.mirrorStore by preferencesDataStore("mirror_state")

/**
 * Saves last-good mirror per repo base so StickyLastGood persists across app restarts.
 */
class MirrorStateStore(private val context: Context) : MirrorRegistry.MirrorStateStore {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memoryCache = ConcurrentHashMap<String, String>()

    init {
        scope.launch {
            try {
                val prefs = context.mirrorStore.data.first()
                prefs.asMap().forEach { (key, value) ->
                    if (key.name.startsWith("mirror_last_") && value is String) {
                        memoryCache[key.name] = value
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun keyFor(base: String): Preferences.Key<String> {
        val norm = base.trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256").digest(norm.toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }.take(16)
        return stringPreferencesKey("mirror_last_$hex")
    }

    private fun keyNameFor(base: String): String = keyFor(base).name

    override fun getLastGood(base: String): String? {
        val keyName = keyNameFor(base)
        memoryCache[keyName]?.let { return it }

        // Fallback (rare after init)
        return runBlocking {
            try {
                val prefs = context.mirrorStore.data.first()
                prefs[keyFor(base)]?.also { memoryCache[keyName] = it }
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun putLastGood(base: String, url: String) {
        val keyName = keyNameFor(base)
        memoryCache[keyName] = url

        scope.launch {
            try {
                context.mirrorStore.edit { it[keyFor(base)] = url }
            } catch (_: Exception) { }
        }
    }

    override fun clear(base: String) {
        val keyName = keyNameFor(base)
        memoryCache.remove(keyName)

        // asynchronous
        scope.launch {
            try {
                context.mirrorStore.edit { it.remove(keyFor(base)) }
            } catch (_: Exception) {
                // Ignore
            }
        }
    }
}