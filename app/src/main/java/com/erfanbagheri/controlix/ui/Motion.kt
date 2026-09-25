package com.erfanbagheri.controlix.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
    feedback: (android.view.View?) -> Unit = Feedback::tap,
    enabled: Boolean = true,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val animationsOn = Feedback.animationsOn
    val scale by animateFloatAsState(
        targetValue = if (animationsOn && enabled && pressed) 0.97f else 1f,
        animationSpec = if (animationsOn) tween(Motion.Press, easing = Motion.EaseOut) else snap(),
        label = "press",
    )
    val view = androidx.compose.ui.platform.LocalView.current
    this
        .graphicsLayer {
            scaleX = if (animationsOn) scale else 1f
            scaleY = if (animationsOn) scale else 1f
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick?.let { handler ->
                {
                    Feedback.longPress(view)
                    handler()
                }
            },
            onClick = {
                feedback(view)
                onClick()
            },
        )
}

/** A quiet content step change; no animated size or motion when disabled. */
@Composable
fun <T> ContentSwap(
    target: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val animationsOn = Feedback.animationsOn
    val offset = with(LocalDensity.current) { 8.dp.roundToPx() }
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = {
            if (animationsOn) {
                val enter = fadeIn(tween(Motion.Micro, easing = Motion.EaseOut)) +
                    slideInHorizontally(tween(Motion.Micro, easing = Motion.EaseOut)) { offset }
                val exit = fadeOut(tween(Motion.Micro, easing = Motion.EaseOut)) +
                    slideOutHorizontally(tween(Motion.Micro, easing = Motion.EaseOut)) { -offset }
                (enter togetherWith exit).using(null)
            } else {
                (EnterTransition.None togetherWith ExitTransition.None).using(null)
            }
        },
        label = "contentSwap",
    ) { current ->
        content(current)
    }
}

/** Stagger delay for list entries. */
fun staggerDelay(index: Int): Int = (index * Motion.Stagger).coerceAtMost(360)

/**
 * The press contract: every clickable scales to 0.97 over 120ms ease-out.
 * Scale shrinks children with it — icon and label press together, like a key.
 */
fun Modifier.pressable(onClick: () -> Unit): Modifier = pressable(true, onClick)

fun Modifier.pressable(enabled: Boolean, onClick: () -> Unit): Modifier =
    pressable(enabled, Feedback::tap, onClick)

/** Press with a custom feedback call (ritual yes/no use confirm/deny). */
fun Modifier.pressable(
    enabled: Boolean = true,
    feedback: (android.view.View?) -> Unit = Feedback::tap,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val animationsOn = Feedback.animationsOn
    val scale by animateFloatAsState(
        targetValue = if (animationsOn && enabled && pressed) 0.97f else 1f,
        animationSpec = if (animationsOn) tween(Motion.Press, easing = Motion.EaseOut) else snap(),
        label = "press",
    )
    val view = androidx.compose.ui.platform.LocalView.current
    this
        .graphicsLayer {
            scaleX = if (animationsOn) scale else 1f
            scaleY = if (animationsOn) scale else 1f
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = {
                feedback(view)
                onClick()
            },
        )
}

/**
 * Flat filled tile — no strokes, no elevation. Depth comes from the surface
 * step alone (Ink base -> surface cards -> surfaceVariant keys).
 */
fun Modifier.bgTile(
    radius: Dp = 20.dp,
    color: Color = Color.Unspecified,
): Modifier = composed {
    val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.surface else color
    this.background(c, RoundedCornerShape(radius))
}

/**
 * The signature moment: IR emission visualized — concentric ember arcs
 * expanding from center, one pulse per code send (triggerKey changes).
 * Pure canvas alpha/scale work; skips drawing entirely when settled.
 */
@Composable
fun EmitPulse(triggerKey: Any?, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    val animationsOn = Feedback.animationsOn
    LaunchedEffect(triggerKey, animationsOn) {
        if (animationsOn && triggerKey != null) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(620, easing = Motion.EaseOut))
        } else {
            progress.snapTo(1f)
        }
    }
    val ember = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val p = progress.value
        if (!animationsOn || p >= 1f) return@Canvas
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
