package com.erfanbagheri.controlix.quicksettings

import com.erfanbagheri.controlix.data.GlobalFavorite
import com.erfanbagheri.controlix.data.RemoteIndex
import com.erfanbagheri.controlix.feature.FavoriteFire
import com.erfanbagheri.controlix.feature.KeyResolver
import com.erfanbagheri.controlix.feature.Resolution

/**
 * Issue #81 — favourites on the Quick Settings tile.
 *
 * A favourite IS a (DeviceKey, key) pair, which is exactly what a
 * [TileTarget] is (#78), so this file adds no second target type, no second
 * encoder and no second slot: the tile's pin already addresses a favourite.
 * What it adds is the *resolution* — a pinned key is fired through
 * [FavoriteFire], the same function the home row uses, so a key whose code
 * vanished reports instead of sending nothing.
 *
 * Pure Kotlin: ordering, the tap decision and the pin/favourite mapping are
 * JVM-testable without Robolectric.
 */

/** What firing the tile's target does. */
sealed interface TileTap {
    data class Send(val carrierHz: Int, val pattern: IntArray) : TileTap

    /** Pinned but gone from the current DB: report, fire nothing. */
    data class Unavailable(val display: String) : TileTap

    /** Nothing to fire: the caller keeps its own fallback. */
    data object Nothing : TileTap
}

object TileFavorites {

    /**
     * Picker rows: every favourite first, in home-row order, then the plain
     * device rows the picker already offers. Both are the same type, so the
     * tile list can never drift from the home row.
     */
    fun picker(favorites: List<GlobalFavorite>, devices: List<TileTarget>): List<TileTarget> =
        favorites.map { TileTarget(it.device, it.button) } + devices

    /**
     * The favourite the tile fires:
     * - a pinned target is a (device, key) *pointer*, never a copy of the row
     *   — a rename or reorder in the home row cannot invalidate it, and the
     *   live row supplies the display label at fire time;
     * - nothing pinned: the first of the home row, the user's declared
     *   most-used key.
     */
    fun toFire(target: TileTarget?, favorites: List<GlobalFavorite>): GlobalFavorite? = when {
        target != null -> favorites.firstOrNull {
            it.device == target.device && it.button == target.lookupKey
        } ?: GlobalFavorite(target.device, target.key, "")
        else -> favorites.firstOrNull()
    }

    /** Resolve the favourite the tile fires into what to do about it. */
    fun decide(
        favorite: GlobalFavorite?,
        index: RemoteIndex?,
        resolver: KeyResolver?,
    ): TileTap {
        if (favorite == null) return TileTap.Nothing
        return when (val r = FavoriteFire.resolve(favorite, index, resolver)) {
            is Resolution.Found -> TileTap.Send(r.code.carrierHz, r.code.pattern)
            Resolution.Unsupported -> TileTap.Unavailable(favorite.display)
        }
    }
}
