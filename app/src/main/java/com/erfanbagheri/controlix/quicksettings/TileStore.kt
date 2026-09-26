package com.erfanbagheri.controlix.quicksettings

import android.content.Context
import com.erfanbagheri.controlix.data.GlobalFavorite

/**
 * Persists what the Quick Settings tile fires at (issues #78 and #81).
 *
 * One slot, one codec, one writer: the picker. Before #78 the pad wrote the
 * last-opened remote id into this slot, so opening a pad to check the time
 * silently retargeted the tile. The slot now holds a `tile2|...` pinned line
 * ([TileTargetCodec]); the old int form is still read — read-only — for
 * pre-#78 installs, and is re-pinned the next time the user picks a target.
 *
 * A pinned favourite is the *same* type as a pinned device key: both are a
 * (DeviceKey, key) pair (#81), so there is no second store and no second
 * encoder to drift apart.
 */
class TileStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * The stored line as-is, whether the slot still holds the legacy int
     * or a new `tile2` string. Read via `prefs.all` because `getString`
     * on an int slot throws.
     */
    fun readRaw(): String? = when (val v = prefs.all[KEY]) {
        is String -> v
        is Int -> v.toString()
        else -> null
    }

    /** The pinned target, or null when the slot holds nothing usable. */
    fun target(): TileTarget? = readRaw()?.let(TileTargetCodec::decode)

    /** The single writer of the tile's stored state — the picker calls this. */
    fun pin(target: TileTarget) {
        prefs.edit().putString(KEY, TileTargetCodec.encode(target)).apply()
    }

    /** Drop the pin; the next read falls back to the default target. */
    fun clear() = prefs.edit().remove(KEY).apply()

    /**
     * Legacy read: a bare int is a pre-#78 last-used remote id; a `tile2`
     * line is not an id. Callers resolve it against the current DB and
     * re-[pin] it — nothing writes the int form anymore.
     */
    fun readLegacyRemoteId(): Int? =
        readRaw()?.takeUnless(TileTargetCodec::isTileTargetLine)?.trim()?.toIntOrNull()

    /**
     * What the tile fires, reduced to one [GlobalFavorite] so it goes through
     * the same resolution as the home row: the explicit picker choice (#78)
     * always wins; with nothing pinned, the first of the home row — the
     * user's declared most-used key.
     */
    fun targetOrDefault(favorites: List<GlobalFavorite>): GlobalFavorite? =
        TileFavorites.toFire(target(), favorites)

    companion object {
        private const val PREFS = "controlix_tile"
        private const val KEY = "last_remote_id"
    }
}
