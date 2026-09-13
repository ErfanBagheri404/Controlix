package com.erfanbagheri.controlix.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Motion tokens — the app-wide animation grammar.
 * Strong ease-out curves (never ease-in on UI). Durations stay under 300ms;
 * exits are faster than enters.
 */
object Motion {
    val EaseOut = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)
    val EaseInOut = CubicBezierEasing(0.645f, 0.045f, 0.355f, 1f)
    const val Press = 120          // button-press feedback
    const val Micro = 180          // chips, small pops
    const val Route = 240          // screen enter
    const val RouteExit = 160      // screen exit — faster than enter
    const val Stagger = 45         // delay between siblings (30-80ms band)
}

/**
 * Click + long-press: same press-scale contract, plus a haptic and a distinct
 * long-press handler (device removal). Long-press waits 500ms.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedPressable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(Motion.Press, easing = Motion.EaseOut),
        label = "press",
    )
    val view = androidx.compose.ui.platform.LocalView.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick?.let { handler ->
                {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    handler()
                }
            },
            onClick = onClick,
        )
}

/** Stagger delay for list entries. */
fun staggerDelay(index: Int): Int = (index * Motion.Stagger).coerceAtMost(360)

/**
 * The press contract: every clickable scales to 0.97 over 120ms ease-out.
 * Scale shrinks children with it — icon and label press together, like a key.
 */
fun Modifier.pressable(onClick: () -> Unit): Modifier = pressable(true, onClick)

fun Modifier.pressable(enabled: Boolean, onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(Motion.Press, easing = Motion.EaseOut),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/**
 * Flat surface with a 1px hairline stroke — the shared tile treatment.
 * No shadows, no elevation: depth comes from the ink step + hairline.
 */
fun Modifier.hairlineTile(radius: Dp = 20.dp): Modifier = composed {
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    this
        .background(surface, RoundedCornerShape(radius))
        .drawWithContent {
            drawContent()
            val r = radius.toPx()
            drawRoundRect(
                color = outline,
                topLeft = Offset(0.5f, 0.5f),
                size = Size(size.width - 1f, size.height - 1f),
                cornerRadius = CornerRadius(r, r),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
}

/**
 * The signature moment: IR emission visualized — concentric ember arcs
 * expanding from center, one pulse per code send (triggerKey changes).
 * Pure canvas alpha/scale work; skips drawing entirely when settled.
 */
@Composable
fun EmitPulse(triggerKey: Any?, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(triggerKey) {
        if (triggerKey != null) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(620, easing = Motion.EaseOut))
        }
    }
    val ember = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val maxR = size.minDimension / 2f
        for (i in 0 until 3) {
            val t = (p * 1.15f) - (i * 0.18f)
            if (t <= 0f || t >= 1f) continue
            val r = maxR * t
            drawArc(
                color = ember.copy(alpha = (1f - t) * 0.5f),
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(size.width / 2f - r, size.height / 2f - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = size.minDimension * 0.026f),
            )
        }
    }
}

/** Section label: mono small caps, dim. */
@Composable
fun SectionHead(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 2.dp, top = 18.dp, bottom = 4.dp),
    )
}
