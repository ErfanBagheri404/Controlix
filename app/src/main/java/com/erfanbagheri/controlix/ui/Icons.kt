package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One hand-authored line-icon family, shared grammar: 48-unit grid, 2.5-unit
 * stroke, round caps/joins, no emoji, no unicode text glyphs (craft floor).
 */
enum class CategoryGlyph { TV, AC, Fan, Projector, Soundbar, AVR, SetTop, Speaker, Disc, Camera, Heater, Fireplace, Vacuum, Monitor, Console, Streaming, Humidifier, Purifier, Clock, VCR, Rays, Toy, Chip }

enum class ActionIcon { Power, VolUp, VolDown, ChUp, ChDown, Mute, Up, Down, Left, Right, Ok, Back, Add, Check, Cross, Sweep, CameraTest, Trash, ChevRight, Menu, Macros, QrScan, Star, Share, Edit, Source, Play, Dots, DotsH, Minus, ChevronUp, ChevronDown, ChevronLeft, ChevronRight, Home, Info, Gauge }

@Composable
fun CategoryIcon(
    glyph: CategoryGlyph,
    size: Dp = 48.dp,
    tint: Color,
    strokeWidth: Dp = 2.5.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        IconScope(this, strokeWidth.toPx(), tint).category(glyph)
    }
}

/** Scale-normalized drawing context on the 48-grid. */
private class IconScope(
    private val s: DrawScope,
    private val w: Float,
    private val c: Color,
) {
    private fun px(v: Float) = v / 48f * s.size.minDimension
    private fun P(vararg pts: Pair<Float, Float>) = Path().apply {
        moveTo(px(pts.first().first), px(pts.first().second))
        pts.drop(1).forEach { lineTo(px(it.first), px(it.second)) }
    }
    private fun R(x1: Float, y1: Float, x2: Float, y2: Float) = Path().apply {
        moveTo(px(x1), px(y1)); lineTo(px(x2), px(y1)); lineTo(px(x2), px(y2)); lineTo(px(x1), px(y2)); close()
    }
    private fun ring(x: Float, y: Float, r: Float) = Rect(px(x - r), px(y - r), px(x + r), px(y + r))
    private fun ovalStroke(x: Float, y: Float, r: Float) = s.drawOval(c, topLeft = Offset(px(x - r), px(y - r)), size = Size(px(r * 2), px(r * 2)), style = Stroke(width = w, cap = StrokeCap.Round))
    private fun stroke(vararg paths: Path) = paths.forEach {
        s.drawPath(it, c, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    private fun seg(x1: Float, y1: Float, x2: Float, y2: Float) =
        s.drawLine(c, Offset(px(x1), px(y1)), Offset(px(x2), px(y2)), w, StrokeCap.Round)
    private fun dot(x: Float, y: Float, r: Float = 2.2f) =
        s.drawCircle(c, px(r), Offset(px(x), px(y)))
    private fun arc(x: Float, y: Float, r: Float, from: Float, sweep: Float) =
        s.drawArc(c, from, sweep, false, Offset(px(x - r), px(y - r)), Size(px(r * 2), px(r * 2)), style = Stroke(width = w, cap = StrokeCap.Round))

    fun category(g: CategoryGlyph) {
        when (g) {
            CategoryGlyph.TV -> { stroke(R(6f, 9f, 42f, 33f)); seg(24f, 33f, 24f, 39f); seg(14f, 40f, 34f, 40f) }
            CategoryGlyph.AC -> { stroke(R(5f, 9f, 43f, 22f)); seg(10f, 16f, 26f, 16f); listOf(12f, 22f, 32f).forEach { x -> seg(x, 27f, x, 33f); seg(x - 2f, 33f, x + 2f, 34f) } }
            CategoryGlyph.Fan -> { ovalStroke(24f, 24f, 15f); arc(24f, 24f, 6f, -90f, 360f); seg(24f, 24f, 24f, 12f); seg(24f, 24f, 34.4f, 30f); seg(24f, 24f, 13.6f, 30f) }
            CategoryGlyph.Projector -> { stroke(R(8f, 16f, 32f, 30f)); seg(32f, 20f, 32f, 26f); val beam = Path().apply { moveTo(px(34f), px(23f)); lineTo(px(43f), px(17f)); lineTo(px(43f), px(29f)); close() }; s.drawPath(beam, c.copy(alpha = 0.35f)) }
            CategoryGlyph.Soundbar -> { stroke(R(5f, 19f, 43f, 29f)); seg(11f, 24f, 37f, 24f) }
            CategoryGlyph.AVR -> { stroke(R(7f, 11f, 41f, 21f), R(7f, 27f, 41f, 37f)); dot(13f, 16f); dot(35f, 32f) }
            CategoryGlyph.SetTop -> { stroke(R(9f, 25f, 39f, 37f)); seg(13f, 31f, 21f, 31f); dot(32f, 31f, 1.8f) }
            CategoryGlyph.Speaker -> { stroke(R(13f, 6f, 35f, 42f)); dot(24f, 18f, 3.4f); dot(24f, 32f, 4.6f) }
            CategoryGlyph.Disc -> { ovalStroke(24f, 24f, 15f); dot(24f, 24f, 2.2f) }
            CategoryGlyph.Camera -> { stroke(R(6f, 15f, 42f, 33f)); ovalStroke(24f, 24f, 6f); seg(16f, 15f, 19f, 10f); seg(29f, 10f, 32f, 15f) }
            CategoryGlyph.Heater -> { stroke(R(10f, 12f, 38f, 36f)); seg(16f, 24f, 32f, 24f); seg(24f, 5f, 24f, 9f); seg(15f, 7f, 18f, 11f); seg(33f, 7f, 30f, 11f) }
            CategoryGlyph.Fireplace -> { seg(8f, 12f, 40f, 12f); stroke(R(11f, 16f, 37f, 38f)); val fl = Path().apply { moveTo(px(24f), px(23f)); cubicTo(px(29f), px(27f), px(30f), px(30f), px(30f), px(32f)); cubicTo(px(30f), px(35.5f), px(27.3f), px(37f), px(24f), px(37f)); cubicTo(px(20.7f), px(37f), px(18f), px(35.5f), px(18f), px(32f)); cubicTo(px(18f), px(30f), px(19f), px(27f), px(24f), px(23f)); close() }; s.drawPath(fl, c.copy(alpha = 0.35f)) }
            CategoryGlyph.Vacuum -> { ovalStroke(24f, 26f, 14f); dot(24f, 26f, 2f); arc(24f, 26f, 8f, -60f, 120f) }
            CategoryGlyph.Monitor -> { stroke(R(6f, 8f, 42f, 30f)); seg(24f, 30f, 24f, 37f); seg(15f, 39f, 33f, 39f) }
            CategoryGlyph.Console -> { val p = Path().apply { moveTo(px(9f), px(18f)); cubicTo(px(3f), px(19f), px(3f), px(35f), px(10f), px(36f)); cubicTo(px(14f), px(36f), px(15f), px(30f), px(24f), px(30f)); cubicTo(px(33f), px(30f), px(34f), px(36f), px(38f), px(36f)); cubicTo(px(45f), px(35f), px(45f), px(19f), px(39f), px(18f)); close() }; stroke(p); dot(15f, 25f, 2f); seg(30.5f, 25f, 35.5f, 25f); seg(33f, 22.5f, 33f, 27.5f) }
            CategoryGlyph.Streaming -> { stroke(R(19f, 10f, 29f, 38f)); seg(34f, 19f, 34f, 29f); seg(39f, 16f, 39f, 32f); seg(14f, 19f, 14f, 29f) }
            CategoryGlyph.Humidifier -> { val drop = Path().apply { moveTo(px(24f), px(8f)); cubicTo(px(31f), px(17f), px(35f), px(22f), px(35f), px(28f)); cubicTo(px(35f), px(34f), px(30f), px(39f), px(24f), px(39f)); cubicTo(px(18f), px(39f), px(13f), px(34f), px(13f), px(28f)); cubicTo(px(13f), px(22f), px(17f), px(17f), px(24f), px(8f)); close() }; stroke(drop) }
            CategoryGlyph.Purifier -> { stroke(Path().apply { moveTo(px(16f), px(8f)); lineTo(px(32f), px(8f)); lineTo(px(34f), px(40f)); lineTo(px(14f), px(40f)); close() }); seg(19f, 15f, 29f, 15f); seg(19f, 21f, 29f, 21f); seg(19f, 27f, 29f, 27f) }
            CategoryGlyph.Clock -> { ovalStroke(24f, 24f, 14f); seg(24f, 24f, 24f, 15f); seg(24f, 24f, 30f, 28f) }
            CategoryGlyph.VCR -> { stroke(R(6f, 15f, 42f, 33f)); dot(16f, 24f, 3f); dot(32f, 24f, 3f); seg(24f, 15f, 24f, 33f) }
            CategoryGlyph.Rays -> { arc(24f, 24f, 6f, 240f, 60f); arc(24f, 24f, 12f, 230f, 80f); arc(24f, 24f, 18f, 222f, 96f) }
            CategoryGlyph.Toy -> { stroke(R(19f, 19f, 29f, 29f)); seg(19f, 19f, 11f, 11f); seg(29f, 19f, 37f, 11f); seg(19f, 29f, 11f, 37f); seg(29f, 29f, 37f, 37f) }
            CategoryGlyph.Chip -> { stroke(R(15f, 15f, 33f, 33f)); seg(24f, 8f, 24f, 15f); seg(24f, 33f, 24f, 40f); seg(8f, 24f, 15f, 24f); seg(33f, 24f, 40f, 24f); seg(18f, 8f, 18f, 15f); seg(30f, 8f, 30f, 15f); seg(18f, 33f, 18f, 40f); seg(30f, 33f, 30f, 40f) }
        }
    }

    fun action(a: ActionIcon) {
        when (a) {
            ActionIcon.Power -> { arc(24f, 26f, 13f, -60f, 240f); seg(24f, 8f, 24f, 24f) }
            ActionIcon.VolUp -> { seg(24f, 8f, 24f, 40f); seg(12f, 20f, 24f, 8f); seg(36f, 20f, 24f, 8f) }
            ActionIcon.VolDown -> { seg(24f, 8f, 24f, 40f); seg(12f, 28f, 24f, 40f); seg(36f, 28f, 24f, 40f) }
            // Chevron + twin bars: channel-up / channel-down.
            ActionIcon.ChUp -> { seg(8f, 20f, 24f, 8f); seg(40f, 20f, 24f, 8f); seg(8f, 30f, 24f, 18f); seg(40f, 30f, 24f, 18f); seg(16f, 40f, 32f, 40f) }
            ActionIcon.ChDown -> { seg(8f, 28f, 24f, 40f); seg(40f, 28f, 24f, 40f); seg(8f, 18f, 24f, 30f); seg(40f, 18f, 24f, 30f); seg(16f, 8f, 32f, 8f) }
            ActionIcon.Mute -> { speaker(); seg(9f, 9f, 39f, 39f) }
            ActionIcon.Up -> { seg(24f, 38f, 24f, 10f); seg(12f, 22f, 24f, 10f); seg(36f, 22f, 24f, 10f) }
            ActionIcon.Down -> { seg(24f, 10f, 24f, 38f); seg(12f, 26f, 24f, 38f); seg(36f, 26f, 24f, 38f) }
            ActionIcon.Left -> { seg(38f, 24f, 10f, 24f); seg(22f, 12f, 10f, 24f); seg(22f, 36f, 10f, 24f) }
            ActionIcon.Right -> { seg(10f, 24f, 38f, 24f); seg(26f, 12f, 38f, 24f); seg(26f, 36f, 38f, 24f) }
            ActionIcon.Ok -> dot(24f, 24f, 5f)
            ActionIcon.Back -> { seg(34f, 8f, 14f, 24f); seg(14f, 24f, 34f, 40f) }
            ActionIcon.ChevRight -> { seg(18f, 10f, 32f, 24f); seg(32f, 24f, 18f, 38f) }
            ActionIcon.Add -> { seg(24f, 8f, 24f, 40f); seg(8f, 24f, 40f, 24f) }
            // Hamburger: three rails.
            ActionIcon.Menu -> { seg(8f, 14f, 40f, 14f); seg(8f, 24f, 40f, 24f); seg(8f, 34f, 40f, 34f) }
            // Macro chain: linked nodes.
            ActionIcon.Macros -> { ovalStroke(14f, 14f, 5f); ovalStroke(34f, 24f, 5f); ovalStroke(14f, 34f, 5f); seg(18f, 16.5f, 29.5f, 22f); seg(29.5f, 26.5f, 18f, 32f) }
            ActionIcon.Check -> { seg(8f, 26f, 19f, 38f); seg(19f, 38f, 40f, 11f) }
            ActionIcon.Cross -> { seg(10f, 10f, 38f, 38f); seg(38f, 10f, 10f, 38f) }
            ActionIcon.Sweep -> { arc(24f, 26f, 6f, 240f, 60f); arc(24f, 26f, 12f, 235f, 70f); arc(24f, 26f, 18f, 230f, 80f) }
            ActionIcon.CameraTest -> { stroke(R(6f, 15f, 42f, 33f)); ovalStroke(24f, 24f, 6f) }
            // QR frame: three finder squares + dot.
            ActionIcon.QrScan -> { stroke(R(8f, 8f, 20f, 20f)); stroke(R(28f, 8f, 40f, 20f)); stroke(R(8f, 28f, 20f, 40f)); dot(34f, 34f, 3.5f) }
            ActionIcon.Trash -> { stroke(Path().apply { moveTo(px(12f), px(14f)); lineTo(px(14f), px(40f)); lineTo(px(34f), px(40f)); lineTo(px(36f), px(14f)) }); seg(8f, 14f, 40f, 14f); seg(19f, 14f, 19f, 8f); seg(29f, 14f, 29f, 8f); seg(18f, 20f, 18f, 34f); seg(24f, 20f, 24f, 34f); seg(30f, 20f, 30f, 34f) }
            // Pin star: 5-point outline.
            ActionIcon.Star -> {
                stroke(Path().apply {
                    moveTo(px(24f), px(7f)); lineTo(px(28.1f), px(18.3f)); lineTo(px(40.2f), px(18.8f))
                    lineTo(px(30.7f), px(26.2f)); lineTo(px(34f), px(37.8f)); lineTo(px(24f), px(31f))
                    lineTo(px(14f), px(37.8f)); lineTo(px(17.3f), px(26.2f)); lineTo(px(7.8f), px(18.8f))
                    lineTo(px(19.9f), px(18.3f)); close()
                })
            }
            // Share: three nodes + links.
            ActionIcon.Share -> { dot(12f, 24f, 3f); dot(36f, 12f, 3f); dot(36f, 36f, 3f); seg(14.5f, 22f, 33f, 13.5f); seg(14.5f, 26f, 33f, 34.5f) }
            // Edit pencil.
            ActionIcon.Edit -> {
                stroke(Path().apply {
                    moveTo(px(30f), px(8f)); lineTo(px(40f), px(18f)); lineTo(px(20f), px(38f)); lineTo(px(10f), px(28f)); close()
                })
            }
            // Source: square + outgoing arrow.
            ActionIcon.Source -> { stroke(R(8f, 14f, 28f, 34f)); seg(28f, 24f, 42f, 24f); seg(34f, 16f, 42f, 24f); seg(34f, 32f, 42f, 24f) }
            // Media play triangle.
            ActionIcon.Play -> {
                stroke(Path().apply {
                    moveTo(px(17f), px(9f)); lineTo(px(17f), px(39f)); lineTo(px(37f), px(24f)); close()
                })
            }
            // Menu dots.
            ActionIcon.Dots -> { dot(10f, 24f); dot(24f, 24f); dot(38f, 24f) }
            ActionIcon.DotsH -> { dot(12f, 24f); dot(24f, 24f); dot(36f, 24f) }
            ActionIcon.Minus -> seg(12f, 24f, 36f, 24f)
            ActionIcon.Home -> { stroke(R(9f, 20f, 39f, 38f)); seg(4f, 20f, 24f, 6f); seg(44f, 20f, 24f, 6f); seg(17f, 36f, 17f, 27f); seg(31f, 36f, 31f, 27f); seg(17f, 27f, 31f, 27f) }
            ActionIcon.ChevronRight -> { seg(14f, 10f, 28f, 24f); seg(14f, 38f, 28f, 24f) }
            ActionIcon.ChevronUp -> { seg(10f, 28f, 24f, 14f); seg(38f, 28f, 24f, 14f) }
            ActionIcon.ChevronDown -> { seg(10f, 20f, 24f, 34f); seg(38f, 20f, 24f, 34f) }
            ActionIcon.ChevronLeft -> { seg(28f, 10f, 14f, 24f); seg(14f, 24f, 28f, 38f) }
            ActionIcon.Info -> { ovalStroke(24f, 24f, 16f); seg(24f, 22f, 24f, 32f); dot(24f, 15f, 1.8f) }
            // Gauge: arc, ticks, needle.
            ActionIcon.Gauge -> { arc(24f, 28f, 16f, 200f, 140f); seg(11f, 22f, 14f, 27f); seg(24f, 12f, 24f, 18f); seg(37f, 22f, 34f, 27f); seg(24f, 28f, 33f, 18f); dot(24f, 28f, 2.5f) }
        }
    }

    private fun speaker() {
        stroke(Path().apply {
            moveTo(px(10f), px(20f)); lineTo(px(18f), px(20f)); lineTo(px(28f), px(11f)); lineTo(px(28f), px(37f)); lineTo(px(18f), px(28f)); lineTo(px(10f), px(28f)); close()
        })
    }
}
