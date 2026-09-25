package com.erfanbagheri.controlix.feature

import com.erfanbagheri.controlix.data.IrCodeRepository

/**
 * Hold-to-confirm gate for the all-brand power-off sweep.
 *
 * Safety: the sweep NEVER starts before a continuous full hold.
 * Idle -> Holding(progress) -> Armed -> Running -> Done/Cancelled.
 * Early release, backwards progress, or Cancel drops to Cancelled with
 * no transmit. Only Armed + Start enters Running.
 */
sealed interface HoldState {
    data object Idle : HoldState
    data class Holding(val progress: Float) : HoldState
    data object Armed : HoldState
    data object Running : HoldState
    data object Done : HoldState
    data object Cancelled : HoldState
}

sealed interface HoldEvent {
    data object Press : HoldEvent
    /** Absolute elapsed hold time in ms; must grow monotonically. */
    data class Tick(val elapsedMs: Long) : HoldEvent
    data object Release : HoldEvent
    data object Start : HoldEvent
    data object Cancel : HoldEvent
    data object Finished : HoldEvent
}

class HoldConfirm(private val holdMs: Long) {
    var state: HoldState = HoldState.Idle
        private set
    private var peakMs: Long = 0

    fun post(event: HoldEvent): HoldState {
        state = when (val s = state) {
            is HoldState.Idle -> when (event) {
                is HoldEvent.Press -> {
                    peakMs = 0
                    HoldState.Holding(0f)
                }
                else -> s
            }
            is HoldState.Holding -> when (event) {
                is HoldEvent.Tick -> {
                    if (event.elapsedMs < peakMs) {
                        HoldState.Cancelled
                    } else {
                        peakMs = event.elapsedMs
                        if (event.elapsedMs >= holdMs) HoldState.Armed
                        else HoldState.Holding((event.elapsedMs.toFloat() / holdMs).coerceIn(0f, 1f))
                    }
                }
                is HoldEvent.Release -> HoldState.Cancelled
                is HoldEvent.Cancel -> HoldState.Cancelled
                // Press/Start/Finished mid-hold never advance the gate.
                else -> s
            }
            is HoldState.Armed -> when (event) {
                is HoldEvent.Start -> HoldState.Running
                is HoldEvent.Release -> HoldState.Cancelled
                is HoldEvent.Cancel -> HoldState.Cancelled
                else -> s
            }
            is HoldState.Running -> when (event) {
                is HoldEvent.Finished -> HoldState.Done
                is HoldEvent.Release -> HoldState.Cancelled
                is HoldEvent.Cancel -> HoldState.Cancelled
                else -> s
            }
            // Terminal states: only a fresh Press re-arms.
            is HoldState.Done -> when (event) {
                is HoldEvent.Press -> {
                    peakMs = 0
                    HoldState.Holding(0f)
                }
                else -> s
            }
            is HoldState.Cancelled -> when (event) {
                is HoldEvent.Press -> {
                    peakMs = 0
                    HoldState.Holding(0f)
                }
                else -> s
            }
        }
        return state
    }
}

/** Queue cursor for the sweep: the power code at [index], or null past the end. */
fun nextDevice(queue: List<IrCodeRepository.PowerButton>, index: Int): IrCodeRepository.PowerButton? =
    queue.getOrNull(index)

/** Exact pre-flight count shown BEFORE anything transmits. */
fun preflightMessage(count: Int): String = "$count devices will be affected"
