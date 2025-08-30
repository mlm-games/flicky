package app.flicky.data.remote

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.mirrorStore by preferencesDataStore("mirror_state")

/**
 * Saves last-good mirror per repo base so StickyLastGood persists across app restarts.
 */
class MirrorStateStore(private val context: Context) : MirrorRegistry.MirrorStateStore {

    private fun keyFor(base: String): Preferences.Key<String> =
        stringPreferencesKey("mirror_last_${base.hashCode()}")

    override fun getLastGood(base: String): String? = runBlocking {
        val prefs = context.mirrorStore.data.first()
        prefs[keyFor(base)]
    }

    override fun putLastGood(base: String, url: String) {
        runBlocking {
            context.mirrorStore.edit { it[keyFor(base)] = url }
        }
    }

    override fun clear(base: String) {
        runBlocking {
            context.mirrorStore.edit { it.remove(keyFor(base)) }
        }
    }
}