package com.erfanbagheri.controlix.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.ui.theme.Ember
import kotlin.math.min

/**
 * The signature mark: a power symbol whose ring opens at the top with a
 * line dropping through the gap, same geometry as the launcher icon.
 */
@Composable
fun PowerGlyph(
    size: Dp = 48.dp,
    tint: Color = Ember,
    strokeWidth: Dp = 3.dp,
) {
    Canvas(modifier = Modifier.size(size)) {
        val sw = strokeWidth.toPx()
        val d = this.size.minDimension
        val c = Offset(d / 2f, d / 2f)
        val r = d * 0.30f
        // Ring with a 70-degree gap centred on the top.
        drawArc(
            color = tint,
            startAngle = -55f,
            sweepAngle = 290f,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(width = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
        // Stem through the gap, from above the ring to just inside it.
        drawLine(
            color = tint,
            start = Offset(c.x, c.y - r * 1.42f),
            end = Offset(c.x, c.y - r * 0.12f),
            strokeWidth = sw,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

/**
 * The pulse: concentric rings that expand and fade each time a code is
 * sent. `pulseKey` increments on every transmission; zero renders nothing.
 * This is the only non-user-triggered motion in the app, and it always
 * answers a real transmit.
 */
@Composable
fun PulseGlow(
    pulseKey: Int,
    size: Dp = 220.dp,
    color: Color = Ember,
    content: @Composable () -> Unit,
) {
    val ring = remember { Animatable(0f) }
    var lastKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(pulseKey) {
        if (pulseKey == 0) return@LaunchedEffect
        if (pulseKey == lastKey) return@LaunchedEffect
        lastKey = pulseKey
        ring.snapTo(0f)
        ring.animateTo(1f, tween(900, easing = LinearEasing))
    }

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val d = this.size.minDimension
            val t = ring.value
            if (t > 0f && t < 1f) {
                val r = d * 0.30f + d * 0.20f * t
                val alpha = (1f - t) * 0.55f
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = r,
                    center = Offset(d / 2f, d / 2f),
                    style = Stroke(width = min(6f, 3.dp.toPx() * (1f - t * 0.5f))),
                )
                val r2 = d * 0.30f + d * 0.11f * t
                drawCircle(
                    color = color.copy(alpha = alpha * 0.5f),
                    radius = r2,
                    center = Offset(d / 2f, d / 2f),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        content()
    }
}