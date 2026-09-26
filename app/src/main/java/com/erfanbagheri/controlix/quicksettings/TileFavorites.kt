package com.erfanbagheri.controlix.quicksettings

import com.erfanbagheri.controlix.data.DeviceKey
import com.erfanbagheri.controlix.data.GlobalFavorite
import com.erfanbagheri.controlix.data.RemoteIndex
import com.erfanbagheri.controlix.data.prefKey
import com.erfanbagheri.controlix.feature.FavoriteFire
import com.erfanbagheri.controlix.feature.KeyResolver
import com.erfanbagheri.controlix.feature.Resolution

/**
 * Issue #81 — favourites on the Quick Settings tile: one tap fires a pinned
 * key.
 *
 * The tile can pin any favourite from the global row (issue #71) and fires it
 * through [FavoriteFire] — the same resolution the home row uses, so a key
 * whose code vanished reports instead of sending nothing. The pin is a
 * POINTER to a row that already lives in `GlobalFavorites` (device key +
 * button), never a copy of the row and never a stored pattern.
 *
 * Pure Kotlin: ordering, the pin codec and the tap decision are JVM-testable
 * without Robolectric.
 */
sealed interface TileTarget {
    val display: String

    /** A pinned global favourite. */
    data class Favourite(val favorite: GlobalFavorite) : TileTarget {
        override val display: String get() = favorite.display
    }

    /** A pinned key on a saved device; the picker's device rows (issue #78). */
    data class Device(val device: DeviceKey, val button: String, val label: String) : TileTarget {
        override val display: String get() = if (label.isBlank()) button else label
    }
}

/** What firing the tile's target does. */
sealed interface TileTap {
    data class Send(val carrierHz: Int, val pattern: IntArray) : TileTap

    /** Pinned but gone from the current DB: report, fire nothing. */
    data class Unavailable(val display: String) : TileTap

    /** No favourite to fire: the caller keeps its own fallback. */
    data object Nothing : TileTap
}

object TileFavorites {

    /**
     * Picker rows: every favourite first, in home-row order, then the saved
     * devices. Issue #78's long-press picker renders this as-is, so the tile
     * list can never drift from the home row.
     */
    fun picker(favorites: List<GlobalFavorite>, devices: List<TileTarget.Device>): List<TileTarget> =
        favorites.map { TileTarget.Favourite(it) } + devices

    /**
     * The favourite the tile fires — everything the tile can pin reduces to
     * one [GlobalFavorite] so it all goes through [FavoriteFire]:
     *
     * - pinned favourite, matched by identity so a rename or reorder in the
     *   home row keeps the pin. Pinned then deleted from the row: honour the
     *   pin rather than silently firing a different key;
     * - pinned device key (the picker's device rows, issue #78): the same
     *   type without a row behind it;
     * - nothing pinned: the first of the home row — the user's declared
     *   most-used key.
     */
    fun toFire(target: TileTarget?, favorites: List<GlobalFavorite>): GlobalFavorite? = when (target) {
        null -> favorites.firstOrNull()
        is TileTarget.Favourite ->
            favorites.firstOrNull { it.device == target.favorite.device && it.button == target.favorite.button }
                ?: target.favorite
        is TileTarget.Device -> GlobalFavorite(target.device, target.button, target.label)
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

    // ---- pinned pointer on disk: `f|<device.prefKey()>|<button>` ----
    // One short string in SharedPreferences. ponytail: URL-encode like
    // GlobalFavorites' own codec if a label ever joins it; the button name is
    // the only free-form field and holds no '|'.

    fun encode(target: TileTarget?): String = when (target) {
        null -> ""
        is TileTarget.Favourite -> "f|${target.favorite.device.prefKey()}|${target.favorite.button}"
        is TileTarget.Device -> "d|${target.device.prefKey()}|${target.button}"
    }

    fun decode(raw: String?): TileTarget? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val f = text.split('|', limit = 3)
        if (f.size < 3 || f[2].isBlank()) return null
        val parts = f[1].split('/', limit = 3)
        if (parts.size < 3) return null
        val key = DeviceKey(parts[0], parts[1], parts[2])
        return when (f[0]) {
            "f" -> TileTarget.Favourite(GlobalFavorite(key, f[2], ""))
            "d" -> TileTarget.Device(key, f[2], "")
            else -> null
        }
    }
}
