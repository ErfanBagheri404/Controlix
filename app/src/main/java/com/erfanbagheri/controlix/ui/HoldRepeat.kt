package com.erfanbagheri.controlix.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.toSize
import com.erfanbagheri.controlix.data.KeyRepeat

/**
 * Long-press repeat schedule for the VOL/CH rockers (issue #11).
 * After [initialDelayMs] of continuous hold, the first repeat is due, then
 * every [intervalMs]. [latestEventAt] returns the latest due deadline at or
 * before [elapsedMs] (null before the first), so frame-paced callers cannot
 * drift or double-fire. [stop] cancels every future deadline.
 */
class HoldRepeatTiming(
    private val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    val intervalMs: Long = DEFAULT_INTERVAL_MS,
) {
    private var stopped = false

    fun latestEventAt(elapsedMs: Long): Long? {
        if (stopped || elapsedMs < initialDelayMs) return null
        val n = (elapsedMs - initialDelayMs) / intervalMs
        return initialDelayMs + n * intervalMs
    }

    /**
     * The smallest deadline strictly after [afterMs] (pass the deadline just
     * fired), or null once stopped. Exact arithmetic over the absolute
     * schedule — callers sleep until it instead of polling.
     */
    fun nextAfter(afterMs: Long): Long? {
        if (stopped) return null
        val base = maxOf(afterMs + 1, initialDelayMs)
        val n = (base - initialDelayMs).ceilDiv(intervalMs)
        return initialDelayMs + n * intervalMs
    }

    private fun Long.ceilDiv(d: Long): Long = (this + d - 1) / d

    fun stop() { stopped = true }

    companion object {
        const val DEFAULT_INITIAL_DELAY_MS = KeyRepeat.INITIAL_DELAY_MS
        const val DEFAULT_INTERVAL_MS = KeyRepeat.INTERVAL_MS
    }
}

/**
 * Pure-Compose rocker key: one immediate send on press-down, then repeats every
 * configured interval while held. Only a rampable key repeats (issue #67) — a
 * toggle would send the same code twenty times.
 *
 * The repeat loop is scoped to the gesture coroutine, so it dies with the
 * pointer: finger-up, drag-out of the key, and screen exit all cancel it and no
 * send can survive the gesture.
 *
 * Copy-code (issue #10) is not dropped: when the hold reached the long-press
 * threshold without ramping, [onLongRelease] fires on release instead of
 * during the hold, so holding and copying do not fight over one gesture.
 */
fun Modifier.rockerPressable(
    rampable: Boolean,
    repeatEnabled: Boolean = Feedback.rockerRepeatOn,
    onFire: () -> Unit,
    onLongRelease: (() -> Unit)? = null,
): Modifier = composed {
    val view = LocalView.current
    var pressed by remember { mutableStateOf(false) }
    val animationsOn = Feedback.animationsOn
    val scale by animateFloatAsState(
        targetValue = if (animationsOn && pressed) 0.97f else 1f,
        animationSpec = if (animationsOn) tween(Motion.Press, easing = Motion.EaseOut) else snap(),
        label = "rockerPress",
    )
    val repeating = rampable && repeatEnabled
    this
        .graphicsLayer {
            scaleX = if (animationsOn) scale else 1f
            scaleY = if (animationsOn) scale else 1f
        }
        .pointerInput(repeating) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val timing = HoldRepeatTiming(intervalMs = Feedback.rockerRepeatIntervalMs.toLong())
                pressed = true
                onFire()
                Feedback.tap(view)

                val bounds = Rect(Offset.Zero, size.toSize())
                val pressTime = System.currentTimeMillis()
                var lastFired = -1L
                var released = false
                while (!released) {
                    val nextDue = if (repeating) timing.nextAfter(lastFired) else null

                    val event = if (nextDue == null) {
                        awaitPointerEvent(PointerEventPass.Initial)
                    } else {
                        val wait = nextDue - (System.currentTimeMillis() - pressTime)
                        withTimeoutOrNull(wait.coerceAtLeast(1)) {
                            awaitPointerEvent(PointerEventPass.Initial)
                        }
                    }

                    // Finger up, or dragged off the key: both stop the stream.
                    if (event != null && KeyRepeat.cancelled(
                            pressed = event.changes.any { it.pressed },
                            insideKey = event.changes.any { bounds.contains(it.position) },
                        )
                    ) {
                        timing.stop()
                        released = true
                    }

                    if (repeating) {
                        val elapsed = System.currentTimeMillis() - pressTime
                        val due = timing.latestEventAt(elapsed)
                        if (due != null && due != lastFired) {
                            lastFired = due
                            onFire()
                            Feedback.tap(view)
                        }
                    }
                }
                pressed = false

                val heldMs = System.currentTimeMillis() - pressTime
                val ramped = lastFired >= 0
                if (onLongRelease != null && KeyRepeat.copiesOnRelease(heldMs, LONG_PRESS_MS, ramped)) {
                    Feedback.longPress(view)
                    onLongRelease()
                }
            }
        }
}

/** Compose's own long-press threshold — `combinedClickable` waits this long. */
private const val LONG_PRESS_MS = 500L
