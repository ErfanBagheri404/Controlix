package com.erfanbagheri.controlix.feature

import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Power-off sweep (TVKILL-style, but offline and data-driven).
 * Iterates every TV power button in the bundled database and transmits it.
 * Any TV in range that recognizes one of the ~400 real power codes shuts off.
 */
class PowerOffSweep(
    private val repo: IrCodeRepository,
    private val transmitter: IrTransmitter
) {
    data class Progress(val sent: Int, val total: Int, val currentBrand: String)

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress

    @Volatile var cancelled = false
        private set

    suspend fun run(gapMs: Long = 120) {
        cancelled = false
        val buttons = repo.allTvPowerButtons()
        _progress.value = Progress(0, buttons.size, "")
        buttons.forEachIndexed { i, btn ->
            if (cancelled) return
            transmitter.transmitButton(btn.carrierHz, btn.pattern)
            _progress.value = Progress(i + 1, buttons.size, btn.brandName)
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
