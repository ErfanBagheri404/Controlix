package com.erfanbagheri.controlix.quicksettings

import android.content.Context
import com.erfanbagheri.controlix.data.GlobalFavorite

/** The last-opened pad plus the pinned tile target (issue #81). */
class TileStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastRemoteId(): Int? =
        prefs.getInt(KEY, -1).takeIf { it > 0 }

    fun rememberRemoteId(remoteId: Int) {
        prefs.edit().putInt(KEY, remoteId).apply()
    }

    /**
     * The pinned target. A pointer (device key + button) into the rows that
     * already live in `GlobalFavorites` — no second copy of the row (#81).
     */
    fun target(): TileTarget? = TileFavorites.decode(prefs.getString(TARGET, null))

    fun pinTarget(target: TileTarget?) {
        prefs.edit().putString(TARGET, TileFavorites.encode(target)).apply()
    }

    /**
     * What the tile fires: the pinned target's favourite, else a favourite
     * from the default rule (first of the home row). The picker's explicit
     * choice (issue #78) always wins over the default.
     */
    fun targetOrDefault(favorites: List<GlobalFavorite>): GlobalFavorite? =
        TileFavorites.toFire(target(), favorites)

    companion object {
        private const val PREFS = "controlix_tile"
        private const val KEY = "last_remote_id"
        private const val TARGET = "target"
    }
}
