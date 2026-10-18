package com.theonionsarewatching.yapchik

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent

/** Persistence for mode + key profile ("yapchik" SharedPreferences file). */
internal object YapchikPrefs {

    private const val FILE = "yapchik"
    private const val KEY_MODE = "mode"
    private const val KEY_PROFILE_ID = "profile_id"
    private const val KEY_CUSTOM_LEFT = "custom_left"
    private const val KEY_CUSTOM_RIGHT = "custom_right"
    private const val KEY_NAV_GUARD = "nav_guard_dp"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    /** null = automatic. */
    fun loadNavGuard(): Int? {
        val p = prefs ?: return null
        return if (p.contains(KEY_NAV_GUARD)) p.getInt(KEY_NAV_GUARD, 0) else null
    }

    fun saveNavGuard(value: Int?) {
        val e = prefs?.edit() ?: return
        if (value == null) e.remove(KEY_NAV_GUARD) else e.putInt(KEY_NAV_GUARD, value)
        e.apply()
    }

    fun loadMode(): SoftkeyMode =
        SoftkeyMode.fromString(prefs?.getString(KEY_MODE, null))

    fun saveMode(mode: SoftkeyMode) {
        prefs?.edit()?.putString(KEY_MODE, mode.name)?.apply()
    }

    /** null = the user has never explicitly chosen/calibrated a layout. */
    fun loadProfile(): KeyProfile? {
        val p = prefs ?: return null
        val id = p.getString(KEY_PROFILE_ID, null) ?: return null
        if (id == KeyProfile.CUSTOM_ID) {
            return KeyProfile.custom(
                p.getInt(KEY_CUSTOM_LEFT, KeyEvent.KEYCODE_SOFT_LEFT),
                p.getInt(KEY_CUSTOM_RIGHT, KeyEvent.KEYCODE_SOFT_RIGHT)
            )
        }
        return KeyProfile.BUILT_IN.firstOrNull { it.id == id }
    }

    /** Pass null to forget the user's explicit choice. */
    fun saveProfile(profile: KeyProfile?) {
        val e = prefs?.edit() ?: return
        if (profile == null) {
            e.remove(KEY_PROFILE_ID).remove(KEY_CUSTOM_LEFT).remove(KEY_CUSTOM_RIGHT).apply()
            return
        }
        e.putString(KEY_PROFILE_ID, profile.id)
        if (profile.id == KeyProfile.CUSTOM_ID) {
            e.putInt(KEY_CUSTOM_LEFT, profile.leftKeys.first())
            e.putInt(KEY_CUSTOM_RIGHT, profile.rightKeys.first())
        }
        e.apply()
    }
}
