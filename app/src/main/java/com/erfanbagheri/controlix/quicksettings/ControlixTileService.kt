package com.erfanbagheri.controlix.quicksettings

import android.app.PendingIntent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.erfanbagheri.controlix.R
import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.DeviceStore

/**
 * One tile, one action. Tap fires power on the last-used remote when one is
 * remembered (borrowed-code aware, straight from the DB, no UI); with nothing
 * remembered it opens the app. Label follows the remembered device.
 */
class ControlixTileService : TileService() {

    override fun onStartListening() {
        val dev = rememberedDevice()
        qsTile?.apply {
            label = dev?.name ?: getString(R.string.app_name)
            state = if (dev != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
        super.onStartListening()
    }

    override fun onClick() {
        when (val action = TileAction.decide(TileStore(this).lastRemoteId())) {
            is TileAction.Decision.TransmitPower ->
                if (!transmitPower(action.remoteId)) openApp()
            TileAction.Decision.OpenApp -> openApp()
        }
    }

    private fun rememberedDevice() =
        TileStore(this).lastRemoteId()?.let { last ->
            runCatching { DeviceStore(this).load().find { it.remoteId == last } }.getOrNull()
        }

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
