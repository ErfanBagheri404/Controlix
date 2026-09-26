package com.erfanbagheri.controlix.feature

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.erfanbagheri.controlix.data.ButtonNames
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.DeviceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Issue #53 — drive the blaster from Tasker / Automate / Home Assistant / adb.
 *
 *     adb shell am broadcast -a com.erfanbagheri.controlix.TRANSMIT \
 *         --es remote_name "Living Room TV" --es button power
 *     adb shell am broadcast -a com.erfanbagheri.controlix.TRANSMIT \
 *         --ei carrier_hz 38000 --es pattern "100,200,300,400"
 *
 * The receiver is exported (an external app can only reach it that way), so
 * [AutomationState] is the trust boundary: off by default, every broadcast
 * dropped until the user flips the drawer toggle.
 *
 * Opening the DB and transmitting both block, so the work moves to
 * [Dispatchers.IO] via `goAsync()`; `finish()` always runs.
 */
class ExternalReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ExternalCommand.ACTION) return
        if (!AutomationState.isEnabled(context)) {
            Log.w(TAG, "dropped: external broadcasts are disabled in the Controlix menu")
            return
        }
        val command = ExternalCommand.parse(intent)
        if (command == null) {
            Log.w(TAG, "dropped: unparseable TRANSMIT extras")
            return
        }

        val app = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                pending.resultCode = if (ExternalFire.fire(app, command)) RESULT_SENT else RESULT_REFUSED
            } catch (e: Exception) {
                Log.w(TAG, "TRANSMIT failed: ${e.message}")
                pending.resultCode = RESULT_REFUSED
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ControlixExternal"
        const val RESULT_SENT = 0
        const val RESULT_REFUSED = 1
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** Executes a parsed [ExternalCommand]. Only the button path opens the DB. */
object ExternalFire {

    fun fire(context: Context, command: ExternalCommand): Boolean =
        if (command.isButton) fireButton(context, command)
        else transmit(context, command.carrierHz!!, command.pattern!!, command.repeat)

    private fun fireButton(context: Context, command: ExternalCommand): Boolean {
        val repo = runCatching { IrCodeRepository(context) }.getOrNull()
            ?: return refuse("code database unavailable")
        return try {
            val burst = resolveButton(context, repo, command) ?: return false
            transmit(context, burst.first, burst.second, command.repeat)
        } finally {
            repo.close()
        }
    }

    private fun transmit(context: Context, carrierHz: Int, pattern: IntArray, repeat: Int): Boolean {
        val tx = IrTransmitter(context)
        var sent = false
        for (i in 0 until repeat) sent = tx.transmitButton(carrierHz, pattern) || sent
        if (!sent) Log.w(TAG, "not sent: $carrierHz Hz, ${pattern.size} durations")
        return sent
    }

    private fun resolveButton(
        context: Context,
        repo: IrCodeRepository,
        command: ExternalCommand,
    ): Pair<Int, IntArray>? {
        val remoteId = command.remoteId ?: matchRemote(context, repo, command.remoteName.orEmpty())
        if (remoteId == null) return refuseRemote("no remote named '${command.remoteName}'")
        val button = pickButton(repo.buttons(remoteId), command.buttonName!!)
        if (button == null) return refuseRemote("no button '${command.buttonName}' on that remote")
        return button.carrierHz to button.pattern
    }

    private fun refuse(reason: String): Boolean {
        Log.w(TAG, "refused: $reason")
        return false
    }

    private fun refuseRemote(reason: String): Pair<Int, IntArray>? {
        Log.w(TAG, "refused: $reason")
        return null
    }

    /**
     * A saved device name first — that is what the user sees in the app —
     * then the exact remote model/file name. An ambiguous name is refused,
     * never guessed: firing another remote's codes is worse than firing none.
     */
    private fun matchRemote(context: Context, repo: IrCodeRepository, name: String): Int? {
        if (name.isBlank()) return null
        // Saved device names are what the user reads in the app, so they win.
        runCatching { DeviceStore(context).load(repo.remoteIndex()) }
            .getOrDefault(emptyList())
            .filter { it.name.equals(name, true) && it.remoteId > 0 }
            .map { it.remoteId }
            .distinct()
            .singleOrNull()
            ?.let { return it }
        val exact = repo.remoteIndex().all().filter { it.fileName.equals(name, true) }
        if (exact.size == 1) return exact[0].remoteId
        if (exact.size > 1) Log.w(TAG, "ambiguous remote name '$name' (${exact.size} matches)")
        return null
    }

    private const val TAG = "ControlixExternal"
}

/**
 * The button a command names: exact label first, then the same semantic match
 * the pad uses, so `power` fires "POWER", "On/Off" or "Standby". Pure, so the
 * vocabulary is unit-tested off-device.
 */
fun pickButton(buttons: List<IrCodeRepository.Button>, name: String): IrCodeRepository.Button? {
    buttons.firstOrNull { it.name == name }?.let { return it }
    buttons.firstOrNull { it.name.equals(name, true) }?.let { return it }
    val pred = when (name.lowercase().replace('-', '_').replace(' ', '_')) {
        "power" -> ButtonNames::power
        "volume_up", "vol_up" -> ButtonNames::volUp
        "volume_down", "vol_down" -> ButtonNames::volDown
        "channel_up", "ch_up" -> ButtonNames::chUp
        "channel_down", "ch_down" -> ButtonNames::chDown
        "mute" -> ButtonNames::mute
        "up" -> ButtonNames::up
        "down" -> ButtonNames::down
        "left" -> ButtonNames::left
        "right" -> ButtonNames::right
        "ok", "enter" -> ButtonNames::ok
        "home" -> ButtonNames::home
        "back" -> ButtonNames::back
        "menu" -> ButtonNames::menu
        "info" -> ButtonNames::info
        "source", "input" -> ButtonNames::source
        "play_pause" -> ButtonNames::playPause
        else -> null
    } ?: return null
    return buttons.firstOrNull { pred(it.name) }
}
