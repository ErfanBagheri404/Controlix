package com.erfanbagheri.controlix.feature

import com.erfanbagheri.controlix.data.DeviceKey
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.data.Macro
import com.erfanbagheri.controlix.data.MacroRunResult
import com.erfanbagheri.controlix.data.NO_DEVICE_ID
import com.erfanbagheri.controlix.data.RemoteIdentity
import com.erfanbagheri.controlix.data.RemoteIndex
import com.erfanbagheri.controlix.data.RemoteRow
import com.erfanbagheri.controlix.data.migrateMacro
import com.erfanbagheri.controlix.data.runMacro
import com.erfanbagheri.controlix.ir.IrTransmitter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.erfanbagheri.controlix.ui.SendResult

/**
 * Power-off sweep (TVKILL-style, but offline and data-driven).
 * Iterates every TV power button in the bundled database and transmits it.
 * Any TV in range that recognizes one of the ~3,900 real power codes shuts off.
 * 120 ms gap keeps the full sweep under 8 minutes.
 *
 * Power-only by design: only remotes WITH a power button are queued;
 * the rest are skipped (see IrCodeRepository.tvRemotesWithoutPowerCount).
 * Per-device results stream live; cancel stops within one gap (< 300 ms).
 */
class PowerOffSweep(
    private val repo: IrCodeRepository,
    private val transmitter: IrTransmitter
) {
    data class DeviceResult(val brand: String, val result: SendResult)
    data class Progress(
        val sent: Int,
        val total: Int,
        val currentBrand: String,
        val failed: Int = 0,
        val elapsedMs: Long = 0,
        val recent: List<DeviceResult> = emptyList(),
    )

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress

    /** The exact pre-flight queue; run() walks this same list, so the count
     * shown on screen before any transmit is the count that will transmit. */
    private val queue: List<IrCodeRepository.PowerButton> by lazy { repo.allTvPowerButtons() }
    val totalDevices: Int get() = queue.size

    @Volatile var cancelled = false
        private set

    suspend fun run(gapMs: Long = 120) {
        cancelled = false
        val buttons = queue
        val log = ArrayDeque<DeviceResult>()
        var failed = 0
        _progress.value = Progress(0, buttons.size, "")
        buttons.forEachIndexed { i, btn ->
            if (cancelled) return
            val res = transmitter.transmitButtonResult(btn.carrierHz, btn.pattern)
            if (res !is SendResult.Sent) failed++
            log.addLast(DeviceResult(btn.brandName, res))
            while (log.size > 12) log.removeFirst()
            _progress.value = Progress(i + 1, buttons.size, btn.brandName, failed, 0L, log.toList())
            delay(gapMs)
        }
        _progress.value = null
    }

    fun cancel() { cancelled = true }
}

/**
 * Plays a macro across devices (issue #70). The step model, validation,
 * migration and run loop live pure in [com.erfanbagheri.controlix.data];
 * only resolution against the live DB and the transmit touch anything else.
 *
 * A step that cannot send aborts the run with its index — it is never
 * skipped, because a half-run macro leaves devices in an unknown state.
 */
class MacroPlayer(
    private val repo: IrCodeRepository,
    private val transmitter: IrTransmitter
) {
    private val index: RemoteIndex? = runCatching { repo.remoteIndex() }.getOrNull()

    /** Saved devices the DB can still see, for on-read key migration. */
    private val rows: List<RemoteRow> = runCatching { index?.all() }.getOrNull().orEmpty()

    /** Migrates pre-#70 id-only steps the moment a macro leaves the store. */
    fun migrate(macro: Macro): Macro = migrateMacro(macro, rows)

    suspend fun play(raw: Macro): MacroRunResult {
        val macro = migrate(raw)
        val byKey = HashMap<DeviceKey, Int>()
        for (step in macro.steps) {
            val key = step.key ?: continue
            val remoteId = byKey.getOrPut(key) {
                // Cached per key: -1 means "gone", which must not be re-queried
                // mid-run, and it keeps a 50-step macro to one lookup per device.
                index?.let { RemoteIdentity.resolve(key, it)?.remoteId } ?: NO_DEVICE_ID
            }
            if (remoteId == NO_DEVICE_ID) {
                val position = macro.steps.indexOf(step) + 1
                return MacroRunResult.Failed(
                    sent = position - 1,
                    stoppedAt = position - 1,
                    reason = "Step $position: device is no longer available.",
                )
            }
        }
        return runMacro(
            macro = macro,
            deviceExists = { step -> step.key != null || step.legacyRemoteId != NO_DEVICE_ID },
            send = { step ->
                val remoteId = step.remoteIdOr(index)
                val btn = if (remoteId == NO_DEVICE_ID) null
                else runCatching { repo.buttonByName(remoteId, step.buttonName) }.getOrNull()
                btn != null && transmitter.transmitButton(btn.carrierHz, btn.pattern)
            },
            sleep = { kotlinx.coroutines.delay(it) },
        )
    }
}
