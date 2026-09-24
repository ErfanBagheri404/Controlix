package com.erfanbagheri.controlix.feature

import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
    data class DeviceResult(val brand: String, val result: IrTransmitter.SendResult)
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
            val res = transmitter.transmitButtonDetailed(btn.carrierHz, btn.pattern)
            if (res !is IrTransmitter.SendResult.Sent) failed++
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
 * Macro: a named sequence of buttons with a delay between steps.
 * Buttons are referenced by (remoteId, buttonName) so macros survive DB updates.
 */
data class MacroStep(val remoteId: Int, val buttonName: String, val delayMs: Long = 350)
data class Macro(val id: Int, val name: String, val steps: List<MacroStep>)

class MacroPlayer(
    private val repo: IrCodeRepository,
    private val transmitter: IrTransmitter
) {
    suspend fun play(macro: Macro): Int {
        var sent = 0
        for (step in macro.steps) {
            val btn = repo.buttonByName(step.remoteId, step.buttonName) ?: continue
            if (transmitter.transmitButton(btn.carrierHz, btn.pattern)) sent++
            delay(step.delayMs)
        }
        return sent
    }
}
