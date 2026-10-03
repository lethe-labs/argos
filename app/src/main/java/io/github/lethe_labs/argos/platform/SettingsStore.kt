package io.github.lethe_labs.argos.platform

import android.content.Context

/** The user's choices. Off by default, so Argos stays as light as it was. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var simPhoto: Boolean
        get() = prefs.getBoolean(KEY_SIM_PHOTO, false)
        set(value) = prefs.edit().putBoolean(KEY_SIM_PHOTO, value).apply()

    private companion object {
        const val KEY_SIM_PHOTO = "sim_photo"
    }
}
