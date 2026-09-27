package com.erfanbagheri.controlix.quicksettings

import android.app.PendingIntent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.erfanbagheri.controlix.R
import com.erfanbagheri.controlix.data.GlobalFavorite
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.feature.RepoKeyResolver
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.FavoritesScenesStore

/**
 * One tile, one *pinned* action (issues #78 and #81).
 *
 * A pin is a (DeviceKey, key) pair, so a pinned favourite and a pinned
 * device key are the same thing. A tap reduces the pin to one
 * [GlobalFavorite] and fires it through [TileFavorites.decide] — the same
 * [com.erfanbagheri.controlix.feature.FavoriteFire] resolution the home row
 * uses. A pin whose code vanished says why and fires nothing; it never
 * silently no-ops and never falls through to a different device.
 *
 * With nothing pinned there is no target at all, so the tile opens the app
 * (pre-#78 installs get their stored id honoured once, then re-pinned).
 * Label names the pinned key, so a tap is never a guess.
 */
class ControlixTileService : TileService() {

    override fun onStartListening() {
        val fav = runCatching { pinnedFavorite() }.getOrNull()
        qsTile?.apply {
            label = fav?.display ?: getString(R.string.app_name)
            state = if (fav != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
        super.onStartListening()
    }

    override fun onClick() {
        when (val tap = resolveTap()) {
            // Same wording as the home row's unavailable report.
            is TileTap.Unavailable -> {
                Toast.makeText(this, "${tap.display} is unavailable on this remote.", Toast.LENGTH_SHORT).show()
                openApp()
            }
            is TileTap.Send ->
                if (!runCatching { IrTransmitter(this).transmitButton(tap.carrierHz, tap.pattern) }
                        .getOrDefault(false)
                ) openApp()
            TileTap.Nothing -> openApp()
        }
    }

    /**
     * The tap for the stored pin. A pre-#78 int slot has no [TileTarget], so
     * the pin falls back to the home row's first favourite; if that also
     * cannot resolve, the app opens so the user can fix the pin.
     */
    private fun resolveTap(): TileTap = runCatching {
        val fav = pinnedFavorite() ?: return@runCatching TileTap.Nothing
        val repo = IrCodeRepository(this)
        try {
            TileFavorites.decide(fav, repo.remoteIndex(), RepoKeyResolver(repo))
        } finally {
            repo.close()
        }
    }.getOrDefault(TileTap.Nothing)

    /** The pin as a favourite: the explicit choice first, else the home row. */
    private fun pinnedFavorite(): GlobalFavorite? =
        TileStore(this).targetOrDefault(favorites())

    private fun favorites(): List<GlobalFavorite> =
        runCatching { FavoritesScenesStore(applicationContext).loadFavorites() }
            .getOrDefault(emptyList())

    @Suppress("DEPRECATION") // PendingIntent overload is the API 26-33 path; minSdk is 26.
    private fun openApp() {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        startActivityAndCollapse(
            PendingIntent.getActivity(
                this, 0, launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
    }
}
