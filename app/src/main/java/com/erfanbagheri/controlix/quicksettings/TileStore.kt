package com.erfanbagheri.controlix.quicksettings

import android.content.Context

/** Persists the last-opened pad so the tile knows what to fire at. */
class TileStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastRemoteId(): Int? =
        prefs.getInt(KEY, -1).takeIf { it > 0 }

    fun rememberRemoteId(remoteId: Int) {
        prefs.edit().putInt(KEY, remoteId).apply()
    }

    companion object {
        private const val PREFS = "controlix_tile"
        private const val KEY = "last_remote_id"
    }
}
