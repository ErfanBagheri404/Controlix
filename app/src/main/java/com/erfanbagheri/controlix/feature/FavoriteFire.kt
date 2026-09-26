package com.erfanbagheri.controlix.feature

import com.erfanbagheri.controlix.data.GlobalFavorite
import com.erfanbagheri.controlix.data.RemoteIdentity
import com.erfanbagheri.controlix.data.RemoteIndex

/**
 * Issue #81 — the ONE path that turns a favourite into a wire code.
 *
 * Home's strip and the quick-settings tile both come through here: stored
 * [GlobalFavorite] -> current remoteId (via its [com.erfanbagheri.controlix.data.DeviceKey])
 * -> live button lookup. A favourite whose device or key is gone returns
 * [Resolution.Unsupported] so the caller reports it; nothing is ever sent
 * silently, and no caller keeps its own copy of the resolution rules.
 */
object FavoriteFire {

    fun resolve(
        favorite: GlobalFavorite,
        index: RemoteIndex?,
        resolver: KeyResolver?,
    ): Resolution {
        val remoteId = index?.let { RemoteIdentity.resolve(favorite.device, it)?.remoteId }
            ?: return Resolution.Unsupported
        return resolver?.resolve(remoteId, favorite.button) ?: Resolution.Unsupported
    }

    /** Resolve, or null when the code cannot be produced right now. */
    fun code(
        favorite: GlobalFavorite,
        index: RemoteIndex?,
        resolver: KeyResolver?,
    ): ResolvedKey? = (resolve(favorite, index, resolver) as? Resolution.Found)?.code
}
