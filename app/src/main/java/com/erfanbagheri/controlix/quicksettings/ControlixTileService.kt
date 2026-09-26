package com.erfanbagheri.controlix.quicksettings

import android.app.PendingIntent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.erfanbagheri.controlix.R
import com.erfanbagheri.controlix.data.GlobalFavorite
import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.feature.RepoKeyResolver
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.DeviceStore
import com.erfanbagheri.controlix.ui.FavoritesScenesStore

/**
 * One tile, one action. Issue #81: a pinned favourite fires through the same
 * resolution the home row uses — same [FavoriteFire], same `RemoteIndex`, same
 * `RepoKeyResolver` — and an unavailable favourite says why instead of sending
 * nothing. With nothing pinned the tile keeps its original behaviour: power on
 * the remembered remote, else the app. The label names the pinned key so the
 * tap is never a guess.
 */
class ControlixTileService : TileService() {

    override fun onStartListening() {
        val (title, active) = tileLabel()
        qsTile?.apply {
            label = title
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
        super.onStartListening()
    }

    /** Pinned favourite's label when there is one; else the remembered device, else the app name. */
    private fun tileLabel(): Pair<String, Boolean> {
        val fav = runCatching { pinnedFavorite() }.getOrNull()
        if (fav != null) return fav.display to true
        val dev = rememberedDevice()
        return (dev?.name ?: getString(R.string.app_name)) to (dev != null)
    }

    override fun onClick() {
        when (val tap = runCatching { tileTap() }.getOrDefault(TileTap.Nothing)) {
            // Same wording as the home row's unavailable report.
            is TileTap.Unavailable -> {
                Toast.makeText(this, "${tap.display} is unavailable on this remote.", Toast.LENGTH_SHORT).show()
                openApp()
            }
            is TileTap.Send -> if (!transmit(tap)) openApp()
            TileTap.Nothing -> when (val action = TileAction.decide(TileStore(this).lastRemoteId())) {
                is TileAction.Decision.TransmitPower ->
                    if (!transmitPower(action.remoteId)) openApp()
                TileAction.Decision.OpenApp -> openApp()
            }
        }
    }

    /** The favourite the tile fires: the pin, else the first of the home row. */
    private fun pinnedFavorite(): GlobalFavorite? =
        TileStore(this).targetOrDefault(favorites())

    private fun favorites(): List<GlobalFavorite> =
        runCatching { FavoritesScenesStore(applicationContext).loadFavorites() }.getOrDefault(emptyList())

    /** The tap decision for the tile's target, resolved on the tile's own DB handle. */
    private fun tileTap(): TileTap {
        val fav = pinnedFavorite() ?: return TileTap.Nothing
        val repo = IrCodeRepository(this)
        return try {
            TileFavorites.decide(fav, repo.remoteIndex(), RepoKeyResolver(repo))
        } finally {
            repo.close()
        }
    }

    private fun rememberedDevice() =
        TileStore(this).lastRemoteId()?.let { last ->
            runCatching { DeviceStore(this).load().find { it.remoteId == last } }.getOrNull()
        }

    private fun transmit(tap: TileTap.Send): Boolean =
        runCatching { IrTransmitter(this).transmitButton(tap.carrierHz, tap.pattern) }.getOrDefault(false)

    /** Own power code first, brand sibling as fallback. False when nothing to send. */
    private fun transmitPower(remoteId: Int): Boolean = runCatching {
        val repo = IrCodeRepository(this)
        try {
            val own = repo.buttons(remoteId)
            if (own.isEmpty()) return false
            val siblings = repo.brandIdOf(remoteId)?.let { repo.brandButtons(it) } ?: own
            val power = EffectiveButtons.resolve(remoteId, own, siblings)
                .firstOrNull { it.key == "power" } ?: return false
            IrTransmitter(this).transmitButton(power.carrierHz, power.pattern)
        } finally {
            repo.close()
        }
    }.getOrDefault(false)

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
