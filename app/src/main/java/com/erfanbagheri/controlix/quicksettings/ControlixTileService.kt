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
 * One tile, one *pinned* action (issue #78).
 *
 * The stored `tile2|...` target names a saved device by stable key plus one
 * semantic key; a tap resolves it against the current DB and fires exactly
 * that key with no UI. A target whose remote left the DB — or nothing stored
 * — opens the app so the pin can be fixed, never a silent no-op. Pre-#78
 * installs stored a bare remote id; that is honoured once via
 * [TileAction.decideLegacy] and re-pinned by the picker on the next choice.
 *
 * Label follows the target: the device name, or the app name when untargeted.
 */
class ControlixTileService : TileService() {

    override fun onStartListening() {
        val dev = pinnedDevice() ?: legacyDevice()
        qsTile?.apply {
            label = dev?.name ?: getString(R.string.app_name)
            state = if (dev != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
        super.onStartListening()
    }

    override fun onClick() {
        val store = TileStore(this)
        val target = store.target()
        val remoteId = target?.let { t ->
            runCatching {
                val repo = IrCodeRepository(this)
                try {
                    TileTargetSelection.resolveRemoteId(t, repo.remoteIndex())
                } finally {
                    repo.close()
                }
            }.getOrNull()
        }
        // Pre-#78 int slot: no tile2 line, honour the legacy id once.
        val action = if (target == null) TileAction.decideLegacy(store.readLegacyRemoteId())
        else TileAction.decide(target, remoteId)
        when (action) {
            is TileAction.Decision.Transmit ->
                if (!transmitKey(action.remoteId, action.key)) openApp()
            TileAction.Decision.OpenApp -> openApp()
        }
    }

    /** The saved device behind the pinned target, if it is still saved. */
    private fun pinnedDevice() = TileStore(this).target()?.let { t ->
        runCatching { DeviceStore(this).load().find { it.key == t.device } }.getOrNull()
    }

    /** Pre-#78 fallback: the last-used remote id, if it is still saved. */
    private fun legacyDevice() =
        TileStore(this).readLegacyRemoteId()?.let { last ->
            runCatching { DeviceStore(this).load().find { it.remoteId == last } }.getOrNull()
        }

    /**
     * Fire the pinned key: own code first, brand sibling as fallback, same
     * resolution as the pad. False when nothing to send.
     */
    private fun transmitKey(remoteId: Int, key: String): Boolean = runCatching {
        val repo = IrCodeRepository(this)
        try {
            val own = repo.buttons(remoteId)
            if (own.isEmpty()) return false
            val siblings = repo.brandIdOf(remoteId)?.let { repo.brandButtons(it) } ?: own
            val btn = EffectiveButtons.resolve(remoteId, own, siblings)
                .firstOrNull { it.key.equals(key, ignoreCase = true) } ?: return false
            IrTransmitter(this).transmitButton(btn.carrierHz, btn.pattern)
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
