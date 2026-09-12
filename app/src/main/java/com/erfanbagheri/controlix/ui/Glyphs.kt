package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.ui.theme.Ember
import com.erfanbagheri.controlix.ui.theme.Hairline

/**
 * Category line icons drawn via Canvas — no external icon library needed.
 * One function per glyph; each draws a minimal device silhouette in 2dp
 * strokes on a 48-unit viewport, matching docs/DESIGN.md "category imagery."
 */
enum class CategoryGlyph {
    TV, AC, Fan, Projector, Soundbar, AVR, SetTop, Speaker, Disc, Camera,
    Heater, Fireplace, Vacuum, Monitor, Console, Streaming, Humidifier,
    Purifier, Clock, CCTV, Toy, VCR, Chip, Rays,
}

@Composable
fun CategoryIcon(glyph: CategoryGlyph, tint: Color = Ember, size: Dp = 56.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val sw = 2.dp.toPx()
        val s = this.size.minDimension / 48f
        fun px(x: Float, y: Float) = Offset(x * s, y * s)
        fun strokePath(p: Path) = drawPath(p, tint, style = Stroke(width = sw))
        // Decorative dots (speaker cones, knobs) use slightly dimmer tint
        fun dot(x: Float, y: Float) = drawCircle(Hairline, radius = sw, center = px(x, y))
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, start = px(x1, y1), end = px(x2, y2), strokeWidth = sw)

        when (glyph) {
            CategoryGlyph.TV -> {
                val p = Path().apply { moveTo(6f*s,8f*s); lineTo(42f*s,8f*s); lineTo(42f*s,32f*s); lineTo(6f*s,32f*s); close() }
                strokePath(p); line(24f,32f,24f,40f); line(12f,40f,36f,40f)
            }
            CategoryGlyph.AC -> {
                val slab = Path().apply { moveTo(5f*s,10f*s); lineTo(43f*s,10f*s); lineTo(43f*s,22f*s); lineTo(5f*s,22f*s); close() }
                strokePath(slab); line(10f,16f,28f,16f)
                for (x in listOf(12f,22f,32f)) { line(x,28f,x,33f); line(x-2f,33f,x+2f,33f) }
            }
            CategoryGlyph.Fan -> {
                val hub = Path().apply { moveTo(24f*s,21f*s); cubicTo(26.2f*s,21f*s,28f*s,22.8f*s,28f*s,25f*s); cubicTo(28f*s,27.2f*s,26.2f*s,29f*s,24f*s,29f*s); cubicTo(21.8f*s,29f*s,20f*s,27.2f*s,20f*s,25f*s); cubicTo(20f*s,22.8f*s,21.8f*s,21f*s,24f*s,21f*s); close() }
                strokePath(hub)
                for (a in 0..2) { val ang = Math.toRadians((a * 120 - 90).toDouble()); val r = 14f*s; line(24f*s, 25f*s, (24f*s + r*Math.cos(ang)).toFloat(), (25f*s + r*Math.sin(ang)).toFloat()) }
            }
            CategoryGlyph.Projector -> {
                val box = Path().apply { moveTo(6f*s,16f*s); lineTo(34f*s,16f*s); lineTo(34f*s,32f*s); lineTo(6f*s,32f*s); close() }
                strokePath(box); line(34f,20f,34f,28f)
                val beam = Path().apply { moveTo(34f*s,24f*s); lineTo(44f*s,20f*s); lineTo(44f*s,28f*s); close() }
                drawPath(beam, tint.copy(alpha = 0.3f))
            }
            CategoryGlyph.Soundbar -> { val p = Path().apply { moveTo(4f*s,18f*s); lineTo(44f*s,18f*s); lineTo(44f*s,30f*s); lineTo(4f*s,30f*s); close() }; strokePath(p); line(10f,24f,38f,24f) }
            CategoryGlyph.AVR -> { for (y in listOf(12f, 26f)) { val p = Path().apply { moveTo(6f*s,y*s); lineTo(42f*s,y*s); lineTo(42f*s,(y+10f)*s); lineTo(6f*s,(y+10f)*s); close() }; strokePath(p) }; dot(13f,17f); dot(35f,31f) }
            CategoryGlyph.SetTop -> { val p = Path().apply { moveTo(8f*s,26f*s); lineTo(40f*s,26f*s); lineTo(40f*s,38f*s); lineTo(8f*s,38f*s); close() }; strokePath(p); line(12f,30f,20f,30f); dot(33f,32f) }
            CategoryGlyph.Speaker -> { val p = Path().apply { moveTo(12f*s,4f*s); lineTo(36f*s,4f*s); lineTo(36f*s,44f*s); lineTo(12f*s,44f*s); close() }; strokePath(p); dot(24f,18f); dot(24f,36f) }
            CategoryGlyph.Disc -> { val c = Path().apply { moveTo(24f*s,9f*s); cubicTo(33.4f*s,9f*s,39f*s,14.6f*s,39f*s,24f*s); cubicTo(39f*s,33.4f*s,33.4f*s,39f*s,24f*s,39f*s); cubicTo(14.6f*s,39f*s,9f*s,33.4f*s,9f*s,24f*s); cubicTo(9f*s,14.6f*s,14.6f*s,9f*s,24f*s,9f*s); close() }; strokePath(c); dot(24f,24f) }
            CategoryGlyph.Camera -> { val body = Path().apply { moveTo(5f*s,15f*s); lineTo(43f*s,15f*s); lineTo(43f*s,33f*s); lineTo(5f*s,33f*s); close() }; strokePath(body); dot(24f,24f); dot(38f,24f) }
            CategoryGlyph.Heater -> { val p = Path().apply { moveTo(10f*s,12f*s); lineTo(38f*s,12f*s); lineTo(38f*s,36f*s); lineTo(10f*s,36f*s); close() }; strokePath(p); line(15f,24f,33f,24f); line(24f,5f,24f,9f); line(13f,7f,16f,11f); line(35f,7f,32f,11f) }
            CategoryGlyph.Fireplace -> { line(6f,12f,42f,12f); val body = Path().apply { moveTo(10f*s,16f*s); lineTo(38f*s,16f*s); lineTo(36f*s,40f*s); lineTo(12f*s,40f*s); close() }; strokePath(body); val flame = Path().apply { moveTo(24f*s,22f*s); cubicTo(28f*s,26f*s,30f*s,29f*s,30f*s,32f*s); cubicTo(30f*s,35f*s,27.3f*s,37f*s,24f*s,37f*s); cubicTo(20.7f*s,37f*s,18f*s,35f*s,18f*s,32f*s); cubicTo(18f*s,29f*s,20f*s,26f*s,24f*s,22f*s); close() }; drawPath(flame, tint.copy(alpha = 0.3f)) }
            CategoryGlyph.Vacuum -> { val c = Path().apply { moveTo(24f*s,12f*s); cubicTo(33.4f*s,12f*s,39f*s,17.6f*s,39f*s,26f*s); cubicTo(39f*s,34.4f*s,33.4f*s,40f*s,24f*s,40f*s); cubicTo(14.6f*s,40f*s,9f*s,34.4f*s,9f*s,26f*s); cubicTo(9f*s,17.6f*s,14.6f*s,12f*s,24f*s,12f*s); close() }; strokePath(c) }
            CategoryGlyph.Monitor -> { val p = Path().apply { moveTo(5f*s,8f*s); lineTo(43f*s,8f*s); lineTo(43f*s,30f*s); lineTo(5f*s,30f*s); close() }; strokePath(p); line(24f,30f,24f,38f); line(14f,40f,34f,40f) }
            CategoryGlyph.Console -> { val p = Path().apply { moveTo(9f*s,18f*s); cubicTo(4f*s,18f*s,4f*s,36f*s,10f*s,36f*s); cubicTo(14f*s,36f*s,15f*s,30f*s,24f*s,30f*s); cubicTo(33f*s,30f*s,34f*s,36f*s,38f*s,36f*s); cubicTo(44f*s,36f*s,44f*s,18f*s,39f*s,18f*s); close() }; strokePath(p); dot(15f,24f); dot(33f,24f) }
            CategoryGlyph.Streaming -> { val p = Path().apply { moveTo(20f*s,8f*s); lineTo(28f*s,8f*s); lineTo(28f*s,40f*s); lineTo(20f*s,40f*s); close() }; strokePath(p); line(32f,18f,32f,30f); line(37f,15f,37f,33f); line(16f,18f,16f,30f) }
            CategoryGlyph.Humidifier -> { val drop = Path().apply { moveTo(24f*s,8f*s); cubicTo(31f*s,17f*s,35f*s,22f*s,35f*s,28f*s); cubicTo(35f*s,34.4f*s,30.1f*s,39f*s,24f*s,39f*s); cubicTo(17.9f*s,39f*s,13f*s,34.4f*s,13f*s,28f*s); cubicTo(13f*s,22f*s,17f*s,17f*s,24f*s,8f*s); close() }; strokePath(drop); line(20f,30f,20f,34f); line(26f,28f,26f,32f) }
            CategoryGlyph.Purifier -> { val p = Path().apply { moveTo(16f*s,6f*s); lineTo(32f*s,6f*s); lineTo(34f*s,42f*s); lineTo(14f*s,42f*s); close() }; strokePath(p); line(19f,14f,29f,14f); line(19f,20f,29f,20f); line(19f,26f,29f,26f) }
            CategoryGlyph.Clock -> { val c = Path().apply { moveTo(24f*s,8f*s); cubicTo(33.4f*s,8f*s,39f*s,13.6f*s,39f*s,23f*s); cubicTo(39f*s,32.4f*s,33.4f*s,38f*s,24f*s,38f*s); cubicTo(14.6f*s,38f*s,9f*s,20f*s,9f*s,23f*s); cubicTo(9f*s,13.6f*s,14.6f*s,8f*s,24f*s,8f*s); close() }; strokePath(c); line(24f,23f,24f,16f); line(24f,23f,31f,27f) }
            CategoryGlyph.CCTV -> { val dome = Path().apply { moveTo(12f*s,20f*s); lineTo(36f*s,20f*s); cubicTo(36f*s,27f*s,31f*s,32f*s,24f*s,32f*s); cubicTo(17f*s,32f*s,12f*s,27f*s,12f*s,20f*s); close() }; strokePath(dome); dot(24f,26f); line(24f,12f,24f,20f); line(18f,10f,30f,10f) }
            CategoryGlyph.Toy -> { val p = Path().apply { moveTo(19f*s,21f*s); lineTo(29f*s,21f*s); lineTo(29f*s,29f*s); lineTo(19f*s,29f*s); close() }; strokePath(p); line(19f,21f,10f,13f); line(29f,21f,38f,13f); line(19f,29f,10f,37f); line(29f,29f,38f,37f) }
            CategoryGlyph.VCR -> { val p = Path().apply { moveTo(5f*s,14f*s); lineTo(43f*s,14f*s); lineTo(43f*s,34f*s); lineTo(5f*s,34f*s); close() }; strokePath(p); dot(16f,24f); dot(32f,24f); line(24f,14f,24f,34f) }
            CategoryGlyph.Chip -> { val p = Path().apply { moveTo(14f*s,14f*s); lineTo(34f*s,14f*s); lineTo(34f*s,34f*s); lineTo(14f*s,34f*s); close() }; strokePath(p); line(24f,8f,24f,14f); line(24f,34f,24f,40f); line(8f,24f,14f,24f); line(34f,24f,40f,24f) }
            CategoryGlyph.Rays -> { dot(16f,24f); val r = Path().apply { moveTo(20f*s,20f*s); cubicTo(22f*s,22f*s,22f*s,26f*s,20f*s,28f*s) }; strokePath(r); val r2 = Path().apply { moveTo(26f*s,16f*s); cubicTo(30f*s,20f*s,30f*s,28f*s,26f*s,32f*s) }; strokePath(r2); val r3 = Path().apply { moveTo(32f*s,12f*s); cubicTo(38f*s,18f*s,38f*s,30f*s,32f*s,36f*s) }; strokePath(r3) }
        }
    }
}