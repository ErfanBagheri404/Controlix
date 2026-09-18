package com.erfanbagheri.controlix.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp

/**
 * Lucide icon set (https://lucide.dev) — ISC license, redistributable.
 * 24x24 viewBox, stroke-only geometry at 2px with round caps and joins.
 * Generated from upstream SVG by src/main/python/gen_lucide_icons.py —
 * do not hand-edit; re-run the generator instead.
 *
 * Stroke colour is ignored at draw time: Compose's Icon() tints the vector,
 * so every glyph follows MaterialTheme.colorScheme like any other icon.
 */
private fun lucideIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathData(block),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()


object Lucide {
    val AirVent: ImageVector
        get() =         lucideIcon("air-vent") {
                    moveTo(18f, 17.5f)
                    curveTo(18.9374f, 18.2032f, 19.2606f, 19.4632f, 18.7774f, 20.5308f)
                    curveTo(18.2941f, 21.5984f, 17.1342f, 22.187f, 15.9872f, 21.9468f)
                    curveTo(14.8403f, 21.7065f, 14.0141f, 20.7018f, 14f, 19.53f)
                    lineTo(14f, 12f)
                    moveTo(6f, 12f)
                    lineTo(4f, 12f)
                    curveTo(2.8954f, 12f, 2f, 11.1046f, 2f, 10f)
                    lineTo(2f, 5f)
                    curveTo(2f, 3.8954f, 2.8954f, 3f, 4f, 3f)
                    lineTo(20f, 3f)
                    curveTo(21.1046f, 3f, 22f, 3.8954f, 22f, 5f)
                    lineTo(22f, 10f)
                    curveTo(22f, 11.1046f, 21.1046f, 12f, 20f, 12f)
                    lineTo(18f, 12f)
                    moveTo(6f, 8f)
                    lineTo(18f, 8f)
                    moveTo(6.6f, 15.572f)
                    curveTo(5.9518f, 16.2074f, 5.8127f, 17.1997f, 6.2614f, 17.9888f)
                    curveTo(6.71f, 18.7779f, 7.6338f, 19.1659f, 8.5114f, 18.9338f)
                    curveTo(9.3889f, 18.7017f, 10.0001f, 17.9077f, 10f, 17f)
                    lineTo(10f, 12f)
    }
    val ArrowDown: ImageVector
        get() =         lucideIcon("arrow-down") {
                    moveTo(12f, 5f)
                    lineTo(12f, 19f)
                    moveTo(19f, 12f)
                    lineTo(12f, 19f)
                    lineTo(5f, 12f)
    }
    val ArrowLeft: ImageVector
        get() =         lucideIcon("arrow-left") {
                    moveTo(12f, 19f)
                    lineTo(5f, 12f)
                    lineTo(12f, 5f)
                    moveTo(19f, 12f)
                    lineTo(5f, 12f)
    }
    val ArrowRight: ImageVector
        get() =         lucideIcon("arrow-right") {
                    moveTo(5f, 12f)
                    lineTo(19f, 12f)
                    moveTo(12f, 5f)
                    lineTo(19f, 12f)
                    lineTo(12f, 19f)
    }
    val ArrowUp: ImageVector
        get() =         lucideIcon("arrow-up") {
                    moveTo(5f, 12f)
                    lineTo(12f, 5f)
                    lineTo(19f, 12f)
                    moveTo(12f, 19f)
                    lineTo(12f, 5f)
    }
    val AudioLines: ImageVector
        get() =         lucideIcon("audio-lines") {
                    moveTo(2f, 10f)
                    lineTo(2f, 13f)
                    moveTo(6f, 6f)
                    lineTo(6f, 17f)
                    moveTo(10f, 3f)
                    lineTo(10f, 21f)
                    moveTo(14f, 8f)
                    lineTo(14f, 15f)
                    moveTo(18f, 5f)
                    lineTo(18f, 18f)
                    moveTo(22f, 10f)
                    lineTo(22f, 13f)
    }
    val Camera: ImageVector
        get() =         lucideIcon("camera") {
                    moveTo(13.997f, 4f)
                    curveTo(14.732f, 4f, 15.4079f, 4.4032f, 15.757f, 5.05f)
                    lineTo(16.243f, 5.95f)
                    curveTo(16.5921f, 6.5968f, 17.268f, 7f, 18.003f, 7f)
                    lineTo(20f, 7f)
                    curveTo(21.1046f, 7f, 22f, 7.8954f, 22f, 9f)
                    lineTo(22f, 18f)
                    curveTo(22f, 19.1046f, 21.1046f, 20f, 20f, 20f)
                    lineTo(4f, 20f)
                    curveTo(2.8954f, 20f, 2f, 19.1046f, 2f, 18f)
                    lineTo(2f, 9f)
                    curveTo(2f, 7.8954f, 2.8954f, 7f, 4f, 7f)
                    lineTo(5.997f, 7f)
                    curveTo(6.7313f, 7f, 7.4065f, 6.5977f, 7.756f, 5.952f)
                    lineTo(8.245f, 5.048f)
                    curveTo(8.5945f, 4.4023f, 9.2697f, 4f, 10.004f, 4f)
                    close()
                    moveTo(15f, 13f)
                    curveTo(15f, 14.6569f, 13.6569f, 16f, 12f, 16f)
                    curveTo(10.3431f, 16f, 9f, 14.6569f, 9f, 13f)
                    curveTo(9f, 11.3431f, 10.3431f, 10f, 12f, 10f)
                    curveTo(13.6569f, 10f, 15f, 11.3431f, 15f, 13f)
                    close()
    }
    val Cast: ImageVector
        get() =         lucideIcon("cast") {
                    moveTo(2f, 8f)
                    lineTo(2f, 6f)
                    curveTo(2f, 4.8954f, 2.8954f, 4f, 4f, 4f)
                    lineTo(20f, 4f)
                    curveTo(21.1046f, 4f, 22f, 4.8954f, 22f, 6f)
                    lineTo(22f, 18f)
                    curveTo(22f, 19.1046f, 21.1046f, 20f, 20f, 20f)
                    lineTo(14f, 20f)
                    moveTo(2f, 12f)
                    curveTo(6.219f, 12.4477f, 9.5523f, 15.781f, 10f, 20f)
                    moveTo(2f, 16f)
                    curveTo(4.0319f, 16.3784f, 5.6216f, 17.9681f, 6f, 20f)
                    moveTo(2f, 20f)
                    lineTo(2.01f, 20f)
    }
    val Check: ImageVector
        get() =         lucideIcon("check") {
                    moveTo(20f, 6f)
                    lineTo(9f, 17f)
                    lineTo(4f, 12f)
    }
    val ChevronDown: ImageVector
        get() =         lucideIcon("chevron-down") {
                    moveTo(6f, 9f)
                    lineTo(12f, 15f)
                    lineTo(18f, 9f)
    }
    val ChevronRight: ImageVector
        get() =         lucideIcon("chevron-right") {
                    moveTo(9f, 18f)
                    lineTo(15f, 12f)
                    lineTo(9f, 6f)
    }
    val ChevronUp: ImageVector
        get() =         lucideIcon("chevron-up") {
                    moveTo(18f, 15f)
                    lineTo(12f, 9f)
                    lineTo(6f, 15f)
    }
    val CircleDot: ImageVector
        get() =         lucideIcon("circle-dot") {
                    moveTo(13f, 12f)
                    curveTo(13f, 12.5523f, 12.5523f, 13f, 12f, 13f)
                    curveTo(11.4477f, 13f, 11f, 12.5523f, 11f, 12f)
                    curveTo(11f, 11.4477f, 11.4477f, 11f, 12f, 11f)
                    curveTo(12.5523f, 11f, 13f, 11.4477f, 13f, 12f)
                    close()
                    moveTo(22f, 12f)
                    curveTo(22f, 17.5228f, 17.5228f, 22f, 12f, 22f)
                    curveTo(6.4772f, 22f, 2f, 17.5228f, 2f, 12f)
                    curveTo(2f, 6.4772f, 6.4772f, 2f, 12f, 2f)
                    curveTo(17.5228f, 2f, 22f, 6.4772f, 22f, 12f)
                    close()
    }
    val Clock: ImageVector
        get() =         lucideIcon("clock") {
                    moveTo(12f, 6f)
                    lineTo(12f, 12f)
                    lineTo(16f, 14f)
                    moveTo(22f, 12f)
                    curveTo(22f, 17.5228f, 17.5228f, 22f, 12f, 22f)
                    curveTo(6.4772f, 22f, 2f, 17.5228f, 2f, 12f)
                    curveTo(2f, 6.4772f, 6.4772f, 2f, 12f, 2f)
                    curveTo(17.5228f, 2f, 22f, 6.4772f, 22f, 12f)
                    close()
    }
    val Cpu: ImageVector
        get() =         lucideIcon("cpu") {
                    moveTo(12f, 20f)
                    lineTo(12f, 22f)
                    moveTo(12f, 2f)
                    lineTo(12f, 4f)
                    moveTo(17f, 20f)
                    lineTo(17f, 22f)
                    moveTo(17f, 2f)
                    lineTo(17f, 4f)
                    moveTo(2f, 12f)
                    lineTo(4f, 12f)
                    moveTo(2f, 17f)
                    lineTo(4f, 17f)
                    moveTo(2f, 7f)
                    lineTo(4f, 7f)
                    moveTo(20f, 12f)
                    lineTo(22f, 12f)
                    moveTo(20f, 17f)
                    lineTo(22f, 17f)
                    moveTo(20f, 7f)
                    lineTo(22f, 7f)
                    moveTo(7f, 20f)
                    lineTo(7f, 22f)
                    moveTo(7f, 2f)
                    lineTo(7f, 4f)
    }
    val Disc: ImageVector
        get() =         lucideIcon("disc") {
                    moveTo(22f, 12f)
                    curveTo(22f, 17.5228f, 17.5228f, 22f, 12f, 22f)
                    curveTo(6.4772f, 22f, 2f, 17.5228f, 2f, 12f)
                    curveTo(2f, 6.4772f, 6.4772f, 2f, 12f, 2f)
                    curveTo(17.5228f, 2f, 22f, 6.4772f, 22f, 12f)
                    close()
                    moveTo(14f, 12f)
                    curveTo(14f, 13.1046f, 13.1046f, 14f, 12f, 14f)
                    curveTo(10.8954f, 14f, 10f, 13.1046f, 10f, 12f)
                    curveTo(10f, 10.8954f, 10.8954f, 10f, 12f, 10f)
                    curveTo(13.1046f, 10f, 14f, 10.8954f, 14f, 12f)
                    close()
    }
    val Droplet: ImageVector
        get() =         lucideIcon("droplet") {
                    moveTo(12f, 22f)
                    curveTo(15.866f, 22f, 19f, 18.866f, 19f, 15f)
                    curveTo(19f, 13f, 18f, 11.1f, 16f, 9.5f)
                    curveTo(14f, 7.9f, 12.5f, 5.5f, 12f, 3f)
                    curveTo(11.5f, 5.5f, 10f, 7.9f, 8f, 9.5f)
                    curveTo(6f, 11.1f, 5f, 13f, 5f, 15f)
                    curveTo(5f, 18.866f, 8.134f, 22f, 12f, 22f)
                    close()
    }
    val Fan: ImageVector
        get() =         lucideIcon("fan") {
                    moveTo(10.827f, 16.379f)
                    curveTo(8.6864f, 17.4607f, 6.1101f, 17.1835f, 4.2486f, 15.6711f)
                    curveTo(2.3872f, 14.1587f, 1.5884f, 11.6937f, 2.209f, 9.377f)
                    lineTo(7.621f, 10.827f)
                    curveTo(6.5393f, 8.6864f, 6.8165f, 6.1101f, 8.3289f, 4.2486f)
                    curveTo(9.8413f, 2.3872f, 12.3063f, 1.5884f, 14.623f, 2.209f)
                    lineTo(13.173f, 7.621f)
                    curveTo(15.3136f, 6.5393f, 17.8899f, 6.8165f, 19.7514f, 8.3289f)
                    curveTo(21.6128f, 9.8413f, 22.4116f, 12.3063f, 21.791f, 14.623f)
                    lineTo(16.379f, 13.173f)
                    curveTo(17.4607f, 15.3136f, 17.1835f, 17.8899f, 15.6711f, 19.7514f)
                    curveTo(14.1587f, 21.6128f, 11.6937f, 22.4116f, 9.377f, 21.791f)
                    lineTo(10.827f, 16.379f)
                    close()
                    moveTo(12f, 12f)
                    lineTo(12f, 12.01f)
    }
    val Filter: ImageVector
        get() =         lucideIcon("list-filter") {
                    moveTo(2f, 5f)
                    lineTo(22f, 5f)
                    moveTo(6f, 12f)
                    lineTo(18f, 12f)
                    moveTo(9f, 19f)
                    lineTo(15f, 19f)
    }
    val Flame: ImageVector
        get() =         lucideIcon("flame") {
                    moveTo(12f, 3f)
                    curveTo(12.6667f, 5.6667f, 14f, 7.8333f, 16f, 9.5f)
                    curveTo(18f, 11.1667f, 19f, 13f, 19f, 15f)
                    curveTo(19f, 18.866f, 15.866f, 22f, 12f, 22f)
                    curveTo(8.134f, 22f, 5f, 18.866f, 5f, 15f)
                    curveTo(5f, 13.9181f, 5.3509f, 12.8655f, 6f, 12f)
                    curveTo(6f, 13.3807f, 7.1193f, 14.5f, 8.5f, 14.5f)
                    curveTo(9.8807f, 14.5f, 11f, 13.3807f, 11f, 12f)
                    curveTo(11f, 10f, 9.5f, 9f, 9.5f, 7f)
                    curveTo(9.5f, 5.6667f, 10.3333f, 4.3333f, 12f, 3f)
    }
    val Gamepad: ImageVector
        get() =         lucideIcon("gamepad-2") {
                    moveTo(17.32f, 5f)
                    lineTo(6.68f, 5f)
                    curveTo(4.63f, 5.0005f, 2.9121f, 6.5508f, 2.702f, 8.59f)
                    curveTo(2.696f, 8.642f, 2.692f, 8.691f, 2.685f, 8.742f)
                    curveTo(2.604f, 9.416f, 2f, 14.456f, 2f, 16f)
                    curveTo(2f, 17.6569f, 3.3431f, 19f, 5f, 19f)
                    curveTo(6f, 19f, 6.5f, 18.5f, 7f, 18f)
                    lineTo(8.414f, 16.586f)
                    curveTo(8.789f, 16.2109f, 9.2976f, 16.0001f, 9.828f, 16f)
                    lineTo(14.172f, 16f)
                    curveTo(14.7024f, 16.0001f, 15.211f, 16.2109f, 15.586f, 16.586f)
                    lineTo(17f, 18f)
                    curveTo(17.5f, 18.5f, 18f, 19f, 19f, 19f)
                    curveTo(20.6569f, 19f, 22f, 17.6569f, 22f, 16f)
                    curveTo(22f, 14.455f, 21.396f, 9.416f, 21.315f, 8.742f)
                    curveTo(21.308f, 8.692f, 21.304f, 8.642f, 21.298f, 8.591f)
                    curveTo(21.0883f, 6.5514f, 19.3704f, 5.0005f, 17.32f, 5f)
                    close()
                    moveTo(6f, 11f)
                    lineTo(10f, 11f)
                    moveTo(8f, 9f)
                    lineTo(8f, 13f)
                    moveTo(15f, 12f)
                    lineTo(15.01f, 12f)
                    moveTo(18f, 10f)
                    lineTo(18.01f, 10f)
    }
    val Gauge: ImageVector
        get() =         lucideIcon("gauge") {
                    moveTo(12f, 14f)
                    lineTo(16f, 10f)
                    moveTo(3.34f, 19f)
                    curveTo(0.9133f, 14.7972f, 1.8544f, 9.4588f, 5.572f, 6.3392f)
                    curveTo(9.2896f, 3.2197f, 14.7104f, 3.2197f, 18.428f, 6.3392f)
                    curveTo(22.1456f, 9.4588f, 23.0867f, 14.7972f, 20.66f, 19f)
    }
    val Heater: ImageVector
        get() =         lucideIcon("heater") {
                    moveTo(11f, 8f)
                    curveTo(13f, 5f, 9f, 5f, 11f, 2f)
                    moveTo(15.5f, 8f)
                    curveTo(17.5f, 5f, 13.5f, 5f, 15.5f, 2f)
                    moveTo(6f, 10f)
                    lineTo(6.01f, 10f)
                    moveTo(6f, 14f)
                    lineTo(6.01f, 14f)
                    moveTo(10f, 16f)
                    lineTo(10f, 12f)
                    moveTo(14f, 16f)
                    lineTo(14f, 12f)
                    moveTo(18f, 16f)
                    lineTo(18f, 12f)
                    moveTo(20f, 6f)
                    curveTo(21.1046f, 6f, 22f, 6.8954f, 22f, 8f)
                    lineTo(22f, 18f)
                    curveTo(22f, 19.1046f, 21.1046f, 20f, 20f, 20f)
                    lineTo(4f, 20f)
                    curveTo(2.8954f, 20f, 2f, 19.1046f, 2f, 18f)
                    lineTo(2f, 8f)
                    curveTo(2f, 6.8954f, 2.8954f, 6f, 4f, 6f)
                    lineTo(7f, 6f)
                    moveTo(5f, 20f)
                    lineTo(5f, 22f)
                    moveTo(19f, 20f)
                    lineTo(19f, 22f)
    }
    val Info: ImageVector
        get() =         lucideIcon("info") {
                    moveTo(12f, 16f)
                    lineTo(12f, 12f)
                    moveTo(12f, 8f)
                    lineTo(12.01f, 8f)
                    moveTo(22f, 12f)
                    curveTo(22f, 17.5228f, 17.5228f, 22f, 12f, 22f)
                    curveTo(6.4772f, 22f, 2f, 17.5228f, 2f, 12f)
                    curveTo(2f, 6.4772f, 6.4772f, 2f, 12f, 2f)
                    curveTo(17.5228f, 2f, 22f, 6.4772f, 22f, 12f)
                    close()
    }
    val Lamp: ImageVector
        get() =         lucideIcon("lamp") {
                    moveTo(12f, 12f)
                    lineTo(12f, 18f)
                    moveTo(4.077f, 10.615f)
                    curveTo(3.9482f, 10.9237f, 3.9823f, 11.2763f, 4.1678f, 11.5546f)
                    curveTo(4.3532f, 11.8329f, 4.6656f, 12f, 5f, 12f)
                    lineTo(19f, 12f)
                    curveTo(19.3344f, 12f, 19.6468f, 11.8329f, 19.8322f, 11.5546f)
                    curveTo(20.0177f, 11.2763f, 20.0518f, 10.9237f, 19.923f, 10.615f)
                    lineTo(16.846f, 3.231f)
                    curveTo(16.5356f, 2.4857f, 15.8074f, 2.0001f, 15f, 2f)
                    lineTo(9f, 2f)
                    curveTo(8.1928f, 1.9999f, 7.4648f, 2.4851f, 7.154f, 3.23f)
                    close()
                    moveTo(8f, 20f)
                    curveTo(8f, 18.8954f, 8.8954f, 18f, 10f, 18f)
                    lineTo(14f, 18f)
                    curveTo(15.1046f, 18f, 16f, 18.8954f, 16f, 20f)
                    lineTo(16f, 21f)
                    curveTo(16f, 21.5523f, 15.5523f, 22f, 15f, 22f)
                    lineTo(9f, 22f)
                    curveTo(8.4477f, 22f, 8f, 21.5523f, 8f, 21f)
                    close()
    }
    val ListChecks: ImageVector
        get() =         lucideIcon("list-checks") {
                    moveTo(13f, 5f)
                    lineTo(21f, 5f)
                    moveTo(13f, 12f)
                    lineTo(21f, 12f)
                    moveTo(13f, 19f)
                    lineTo(21f, 19f)
                    moveTo(3f, 17f)
                    lineTo(5f, 19f)
                    lineTo(9f, 15f)
                    moveTo(3f, 7f)
                    lineTo(5f, 9f)
                    lineTo(9f, 5f)
    }
    val Menu: ImageVector
        get() =         lucideIcon("menu") {
                    moveTo(4f, 5f)
                    lineTo(20f, 5f)
                    moveTo(4f, 12f)
                    lineTo(20f, 12f)
                    moveTo(4f, 19f)
                    lineTo(20f, 19f)
    }
    val MicOff: ImageVector
        get() =         lucideIcon("mic-off") {
                    moveTo(12f, 19f)
                    lineTo(12f, 22f)
                    moveTo(15f, 9.34f)
                    lineTo(15f, 5f)
                    curveTo(14.9916f, 3.6134f, 14.0341f, 2.4132f, 12.684f, 2.0971f)
                    curveTo(11.3339f, 1.781f, 9.9431f, 2.4313f, 9.32f, 3.67f)
                    moveTo(16.95f, 16.95f)
                    curveTo(14.948f, 18.9522f, 11.937f, 19.5512f, 9.3211f, 18.4676f)
                    curveTo(6.7053f, 17.3841f, 4.9998f, 14.8314f, 5f, 12f)
                    lineTo(5f, 10f)
                    moveTo(18.89f, 13.23f)
                    curveTo(18.9628f, 12.824f, 18.9996f, 12.4124f, 19f, 12f)
                    lineTo(19f, 10f)
                    moveTo(2f, 2f)
                    lineTo(22f, 22f)
                    moveTo(9f, 9f)
                    lineTo(9f, 12f)
                    curveTo(9.0011f, 13.2126f, 9.732f, 14.3053f, 10.8523f, 14.7691f)
                    curveTo(11.9726f, 15.233f, 13.2621f, 14.9769f, 14.12f, 14.12f)
    }
    val Minus: ImageVector
        get() =         lucideIcon("minus") {
                    moveTo(5f, 12f)
                    lineTo(19f, 12f)
    }
    val Monitor: ImageVector
        get() =         lucideIcon("monitor") {
                    moveTo(8f, 21f)
                    lineTo(16f, 21f)
                    moveTo(12f, 17f)
                    lineTo(12f, 21f)
    }
    val Moon: ImageVector
        get() =         lucideIcon("moon") {
                    moveTo(20.985f, 12.486f)
                    curveTo(20.7239f, 17.3235f, 16.6804f, 21.0863f, 11.8366f, 20.9994f)
                    curveTo(6.9929f, 20.9125f, 3.087f, 17.007f, 2.9996f, 12.1633f)
                    curveTo(2.9121f, 7.3195f, 6.6745f, 3.2757f, 11.512f, 3.014f)
                    curveTo(11.917f, 2.992f, 12.129f, 3.474f, 11.914f, 3.817f)
                    curveTo(10.4332f, 6.1863f, 10.7837f, 9.2641f, 12.7593f, 11.2397f)
                    curveTo(14.7349f, 13.2153f, 17.8127f, 13.5658f, 20.182f, 12.085f)
                    curveTo(20.526f, 11.87f, 21.007f, 12.081f, 20.985f, 12.486f)
    }
    val MoreVertical: ImageVector
        get() =         lucideIcon("ellipsis-vertical") {
                    moveTo(13f, 12f)
                    curveTo(13f, 12.5523f, 12.5523f, 13f, 12f, 13f)
                    curveTo(11.4477f, 13f, 11f, 12.5523f, 11f, 12f)
                    curveTo(11f, 11.4477f, 11.4477f, 11f, 12f, 11f)
                    curveTo(12.5523f, 11f, 13f, 11.4477f, 13f, 12f)
                    close()
                    moveTo(13f, 5f)
                    curveTo(13f, 5.5523f, 12.5523f, 6f, 12f, 6f)
                    curveTo(11.4477f, 6f, 11f, 5.5523f, 11f, 5f)
                    curveTo(11f, 4.4477f, 11.4477f, 4f, 12f, 4f)
                    curveTo(12.5523f, 4f, 13f, 4.4477f, 13f, 5f)
                    close()
                    moveTo(13f, 19f)
                    curveTo(13f, 19.5523f, 12.5523f, 20f, 12f, 20f)
                    curveTo(11.4477f, 20f, 11f, 19.5523f, 11f, 19f)
                    curveTo(11f, 18.4477f, 11.4477f, 18f, 12f, 18f)
                    curveTo(12.5523f, 18f, 13f, 18.4477f, 13f, 19f)
                    close()
    }
    val Music: ImageVector
        get() =         lucideIcon("music") {
                    moveTo(9f, 18f)
                    lineTo(9f, 5f)
                    lineTo(21f, 3f)
                    lineTo(21f, 16f)
                    moveTo(9f, 18f)
                    curveTo(9f, 19.6569f, 7.6569f, 21f, 6f, 21f)
                    curveTo(4.3431f, 21f, 3f, 19.6569f, 3f, 18f)
                    curveTo(3f, 16.3431f, 4.3431f, 15f, 6f, 15f)
                    curveTo(7.6569f, 15f, 9f, 16.3431f, 9f, 18f)
                    close()
                    moveTo(21f, 16f)
                    curveTo(21f, 17.6569f, 19.6569f, 19f, 18f, 19f)
                    curveTo(16.3431f, 19f, 15f, 17.6569f, 15f, 16f)
                    curveTo(15f, 14.3431f, 16.3431f, 13f, 18f, 13f)
                    curveTo(19.6569f, 13f, 21f, 14.3431f, 21f, 16f)
                    close()
    }
    val Palette: ImageVector
        get() =         lucideIcon("palette") {
                    moveTo(12f, 22f)
                    curveTo(6.4772f, 22f, 2f, 17.5228f, 2f, 12f)
                    curveTo(2f, 6.4772f, 6.4772f, 2f, 12f, 2f)
                    curveTo(17.5228f, 2f, 22f, 6.0294f, 22f, 11f)
                    curveTo(22f, 13.7614f, 19.7614f, 16f, 17f, 16f)
                    lineTo(14.75f, 16f)
                    curveTo(14.0871f, 16f, 13.4812f, 16.3745f, 13.1848f, 16.9674f)
                    curveTo(12.8883f, 17.5602f, 12.9523f, 18.2697f, 13.35f, 18.8f)
                    lineTo(13.65f, 19.2f)
                    curveTo(14.0477f, 19.7303f, 14.1117f, 20.4398f, 13.8152f, 21.0326f)
                    curveTo(13.5188f, 21.6255f, 12.9129f, 22f, 12.25f, 22f)
                    close()
                    moveTo(14f, 6.5f)
                    curveTo(14f, 6.7761f, 13.7761f, 7f, 13.5f, 7f)
                    curveTo(13.2239f, 7f, 13f, 6.7761f, 13f, 6.5f)
                    curveTo(13f, 6.2239f, 13.2239f, 6f, 13.5f, 6f)
                    curveTo(13.7761f, 6f, 14f, 6.2239f, 14f, 6.5f)
                    close()
                    moveTo(18f, 10.5f)
                    curveTo(18f, 10.7761f, 17.7761f, 11f, 17.5f, 11f)
                    curveTo(17.2239f, 11f, 17f, 10.7761f, 17f, 10.5f)
                    curveTo(17f, 10.2239f, 17.2239f, 10f, 17.5f, 10f)
                    curveTo(17.7761f, 10f, 18f, 10.2239f, 18f, 10.5f)
                    close()
                    moveTo(7f, 12.5f)
                    curveTo(7f, 12.7761f, 6.7761f, 13f, 6.5f, 13f)
                    curveTo(6.2239f, 13f, 6f, 12.7761f, 6f, 12.5f)
                    curveTo(6f, 12.2239f, 6.2239f, 12f, 6.5f, 12f)
                    curveTo(6.7761f, 12f, 7f, 12.2239f, 7f, 12.5f)
                    close()
                    moveTo(9f, 7.5f)
                    curveTo(9f, 7.7761f, 8.7761f, 8f, 8.5f, 8f)
                    curveTo(8.2239f, 8f, 8f, 7.7761f, 8f, 7.5f)
                    curveTo(8f, 7.2239f, 8.2239f, 7f, 8.5f, 7f)
                    curveTo(8.7761f, 7f, 9f, 7.2239f, 9f, 7.5f)
                    close()
    }
    val Pencil: ImageVector
        get() =         lucideIcon("pencil") {
                    moveTo(21.174f, 6.812f)
                    curveTo(22.275f, 5.7113f, 22.2752f, 3.9265f, 21.1745f, 2.8255f)
                    curveTo(20.0738f, 1.7245f, 18.289f, 1.7243f, 17.188f, 2.825f)
                    lineTo(3.842f, 16.174f)
                    curveTo(3.6098f, 16.4055f, 3.4381f, 16.6905f, 3.342f, 17.004f)
                    lineTo(2.021f, 21.356f)
                    curveTo(1.9683f, 21.5322f, 2.0167f, 21.7231f, 2.1468f, 21.853f)
                    curveTo(2.2769f, 21.9829f, 2.4679f, 22.0309f, 2.644f, 21.978f)
                    lineTo(6.997f, 20.658f)
                    curveTo(7.3102f, 20.5628f, 7.5952f, 20.3921f, 7.827f, 20.161f)
                    close()
                    moveTo(15f, 5f)
                    lineTo(19f, 9f)
    }
    val Play: ImageVector
        get() =         lucideIcon("circle-play") {
                    moveTo(9f, 9.003f)
                    curveTo(8.9989f, 8.6417f, 9.1928f, 8.3078f, 9.5073f, 8.1298f)
                    curveTo(9.8217f, 7.9518f, 10.2077f, 7.9572f, 10.517f, 8.144f)
                    lineTo(15.514f, 11.141f)
                    curveTo(15.8166f, 11.3214f, 16.002f, 11.6477f, 16.002f, 12f)
                    curveTo(16.002f, 12.3523f, 15.8166f, 12.6786f, 15.514f, 12.859f)
                    lineTo(10.517f, 15.856f)
                    curveTo(10.2075f, 16.0429f, 9.8213f, 16.0482f, 9.5068f, 15.87f)
                    curveTo(9.1923f, 15.6917f, 8.9985f, 15.3575f, 9f, 14.996f)
                    close()
                    moveTo(22f, 12f)
                    curveTo(22f, 17.5228f, 17.5228f, 22f, 12f, 22f)
                    curveTo(6.4772f, 22f, 2f, 17.5228f, 2f, 12f)
                    curveTo(2f, 6.4772f, 6.4772f, 2f, 12f, 2f)
                    curveTo(17.5228f, 2f, 22f, 6.4772f, 22f, 12f)
                    close()
    }
    val Plus: ImageVector
        get() =         lucideIcon("plus") {
                    moveTo(5f, 12f)
                    lineTo(19f, 12f)
                    moveTo(12f, 5f)
                    lineTo(12f, 19f)
    }
    val Power: ImageVector
        get() =         lucideIcon("power") {
                    moveTo(12f, 2f)
                    lineTo(12f, 12f)
                    moveTo(18.4f, 6.6f)
                    curveTo(21.9086f, 10.1099f, 21.9138f, 15.7975f, 18.4117f, 19.3138f)
                    curveTo(14.9096f, 22.8302f, 9.222f, 22.848f, 5.6979f, 19.3537f)
                    curveTo(2.1739f, 15.8593f, 2.1435f, 10.1718f, 5.63f, 6.64f)
    }
    val PowerOff: ImageVector
        get() =         lucideIcon("power-off") {
                    moveTo(18.36f, 6.64f)
                    curveTo(20.5464f, 8.8275f, 21.4565f, 11.9844f, 20.77f, 15f)
                    moveTo(6.16f, 6.16f)
                    curveTo(3.5548f, 8.3481f, 2.4061f, 11.8182f, 3.1912f, 15.1286f)
                    curveTo(3.9763f, 18.439f, 6.561f, 21.0237f, 9.8714f, 21.8088f)
                    curveTo(13.1818f, 22.5939f, 16.6519f, 21.4452f, 18.84f, 18.84f)
                    moveTo(12f, 2f)
                    lineTo(12f, 6f)
                    moveTo(2f, 2f)
                    lineTo(22f, 22f)
    }
    val Projector: ImageVector
        get() =         lucideIcon("projector") {
                    moveTo(5f, 7f)
                    lineTo(3f, 5f)
                    moveTo(9f, 6f)
                    lineTo(9f, 3f)
                    moveTo(13f, 7f)
                    lineTo(15f, 5f)
                    moveTo(11.83f, 12f)
                    lineTo(20f, 12f)
                    curveTo(21.1046f, 12f, 22f, 12.8954f, 22f, 14f)
                    lineTo(22f, 18f)
                    curveTo(22f, 19.1046f, 21.1046f, 20f, 20f, 20f)
                    lineTo(4f, 20f)
                    curveTo(2.8954f, 20f, 2f, 19.1046f, 2f, 18f)
                    lineTo(2f, 14f)
                    curveTo(2f, 12.8954f, 2.8954f, 12f, 4f, 12f)
                    lineTo(6.17f, 12f)
                    moveTo(16f, 16f)
                    lineTo(18f, 16f)
                    moveTo(12f, 13f)
                    curveTo(12f, 14.6569f, 10.6569f, 16f, 9f, 16f)
                    curveTo(7.3431f, 16f, 6f, 14.6569f, 6f, 13f)
                    curveTo(6f, 11.3431f, 7.3431f, 10f, 9f, 10f)
                    curveTo(10.6569f, 10f, 12f, 11.3431f, 12f, 13f)
                    close()
    }
    val QrCode: ImageVector
        get() =         lucideIcon("qr-code") {
                    moveTo(21f, 16f)
                    lineTo(18f, 16f)
                    curveTo(16.8954f, 16f, 16f, 16.8954f, 16f, 18f)
                    lineTo(16f, 21f)
                    moveTo(21f, 21f)
                    lineTo(21f, 21.01f)
                    moveTo(12f, 7f)
                    lineTo(12f, 10f)
                    curveTo(12f, 11.1046f, 11.1046f, 12f, 10f, 12f)
                    lineTo(7f, 12f)
                    moveTo(3f, 12f)
                    lineTo(3.01f, 12f)
                    moveTo(12f, 3f)
                    lineTo(12.01f, 3f)
                    moveTo(12f, 16f)
                    lineTo(12f, 16.01f)
                    moveTo(16f, 12f)
                    lineTo(17f, 12f)
                    moveTo(21f, 12f)
                    lineTo(21f, 12.01f)
                    moveTo(12f, 21f)
                    lineTo(12f, 20f)
    }
    val Radio: ImageVector
        get() =         lucideIcon("radio") {
                    moveTo(16.247f, 7.761f)
                    curveTo(18.5853f, 10.1033f, 18.5853f, 13.8967f, 16.247f, 16.239f)
                    moveTo(19.075f, 4.933f)
                    curveTo(22.9748f, 8.8373f, 22.9748f, 15.1627f, 19.075f, 19.067f)
                    moveTo(4.925f, 19.067f)
                    curveTo(1.0252f, 15.1627f, 1.0252f, 8.8373f, 4.925f, 4.933f)
                    moveTo(7.753f, 16.239f)
                    curveTo(5.4147f, 13.8967f, 5.4147f, 10.1033f, 7.753f, 7.761f)
                    moveTo(14f, 12f)
                    curveTo(14f, 13.1046f, 13.1046f, 14f, 12f, 14f)
                    curveTo(10.8954f, 14f, 10f, 13.1046f, 10f, 12f)
                    curveTo(10f, 10.8954f, 10.8954f, 10f, 12f, 10f)
                    curveTo(13.1046f, 10f, 14f, 10.8954f, 14f, 12f)
                    close()
    }
    val ScanLine: ImageVector
        get() =         lucideIcon("scan-line") {
                    moveTo(3f, 7f)
                    lineTo(3f, 5f)
                    curveTo(3f, 3.8954f, 3.8954f, 3f, 5f, 3f)
                    lineTo(7f, 3f)
                    moveTo(17f, 3f)
                    lineTo(19f, 3f)
                    curveTo(20.1046f, 3f, 21f, 3.8954f, 21f, 5f)
                    lineTo(21f, 7f)
                    moveTo(21f, 17f)
                    lineTo(21f, 19f)
                    curveTo(21f, 20.1046f, 20.1046f, 21f, 19f, 21f)
                    lineTo(17f, 21f)
                    moveTo(7f, 21f)
                    lineTo(5f, 21f)
                    curveTo(3.8954f, 21f, 3f, 20.1046f, 3f, 19f)
                    lineTo(3f, 17f)
                    moveTo(7f, 12f)
                    lineTo(17f, 12f)
    }
    val Settings: ImageVector
        get() =         lucideIcon("settings") {
                    moveTo(9.671f, 4.136f)
                    curveTo(9.7852f, 2.9348f, 10.7939f, 2.0174f, 12.0005f, 2.0174f)
                    curveTo(13.2071f, 2.0174f, 14.2158f, 2.9348f, 14.33f, 4.136f)
                    curveTo(14.3972f, 4.8959f, 14.8307f, 5.5754f, 15.4915f, 5.9567f)
                    curveTo(16.1523f, 6.3379f, 16.9574f, 6.3731f, 17.649f, 6.051f)
                    curveTo(18.7452f, 5.5533f, 20.0402f, 5.9686f, 20.6425f, 7.0111f)
                    curveTo(21.2448f, 8.0536f, 20.9577f, 9.3829f, 19.979f, 10.084f)
                    curveTo(19.3547f, 10.522f, 18.983f, 11.2369f, 18.983f, 11.9995f)
                    curveTo(18.983f, 12.7621f, 19.3547f, 13.477f, 19.979f, 13.915f)
                    curveTo(20.9577f, 14.6161f, 21.2448f, 15.9454f, 20.6425f, 16.9879f)
                    curveTo(20.0402f, 18.0304f, 18.7452f, 18.4457f, 17.649f, 17.948f)
                    curveTo(16.9574f, 17.6259f, 16.1523f, 17.6611f, 15.4915f, 18.0423f)
                    curveTo(14.8307f, 18.4236f, 14.3972f, 19.1031f, 14.33f, 19.863f)
                    curveTo(14.2158f, 21.0642f, 13.2071f, 21.9816f, 12.0005f, 21.9816f)
                    curveTo(10.7939f, 21.9816f, 9.7852f, 21.0642f, 9.671f, 19.863f)
                    curveTo(9.6039f, 19.1028f, 9.1703f, 18.423f, 8.5092f, 18.0417f)
                    curveTo(7.8481f, 17.6604f, 7.0427f, 17.6254f, 6.351f, 17.948f)
                    curveTo(5.2548f, 18.4457f, 3.9598f, 18.0304f, 3.3575f, 16.9879f)
                    curveTo(2.7552f, 15.9454f, 3.0423f, 14.6161f, 4.021f, 13.915f)
                    curveTo(4.6453f, 13.477f, 5.017f, 12.7621f, 5.017f, 11.9995f)
                    curveTo(5.017f, 11.2369f, 4.6453f, 10.522f, 4.021f, 10.084f)
                    curveTo(3.0437f, 9.3826f, 2.7574f, 8.0544f, 3.359f, 7.0127f)
                    curveTo(3.9606f, 5.971f, 5.254f, 5.5551f, 6.35f, 6.051f)
                    curveTo(7.0416f, 6.3731f, 7.8467f, 6.3379f, 8.5075f, 5.9567f)
                    curveTo(9.1683f, 5.5754f, 9.6018f, 4.8959f, 9.669f, 4.136f)
                    moveTo(15f, 12f)
                    curveTo(15f, 13.6569f, 13.6569f, 15f, 12f, 15f)
                    curveTo(10.3431f, 15f, 9f, 13.6569f, 9f, 12f)
                    curveTo(9f, 10.3431f, 10.3431f, 9f, 12f, 9f)
                    curveTo(13.6569f, 9f, 15f, 10.3431f, 15f, 12f)
                    close()
    }
    val Share: ImageVector
        get() =         lucideIcon("share-2") {
                    moveTo(21f, 5f)
                    curveTo(21f, 6.6569f, 19.6569f, 8f, 18f, 8f)
                    curveTo(16.3431f, 8f, 15f, 6.6569f, 15f, 5f)
                    curveTo(15f, 3.3431f, 16.3431f, 2f, 18f, 2f)
                    curveTo(19.6569f, 2f, 21f, 3.3431f, 21f, 5f)
                    close()
                    moveTo(9f, 12f)
                    curveTo(9f, 13.6569f, 7.6569f, 15f, 6f, 15f)
                    curveTo(4.3431f, 15f, 3f, 13.6569f, 3f, 12f)
                    curveTo(3f, 10.3431f, 4.3431f, 9f, 6f, 9f)
                    curveTo(7.6569f, 9f, 9f, 10.3431f, 9f, 12f)
                    close()
                    moveTo(21f, 19f)
                    curveTo(21f, 20.6569f, 19.6569f, 22f, 18f, 22f)
                    curveTo(16.3431f, 22f, 15f, 20.6569f, 15f, 19f)
                    curveTo(15f, 17.3431f, 16.3431f, 16f, 18f, 16f)
                    curveTo(19.6569f, 16f, 21f, 17.3431f, 21f, 19f)
                    close()
                    moveTo(8.59f, 13.51f)
                    lineTo(15.42f, 17.49f)
                    moveTo(15.41f, 6.51f)
                    lineTo(8.59f, 10.49f)
    }
    val Snowflake: ImageVector
        get() =         lucideIcon("snowflake") {
                    moveTo(10f, 20f)
                    lineTo(8.75f, 17.5f)
                    lineTo(6f, 18f)
                    moveTo(10f, 4f)
                    lineTo(8.75f, 6.5f)
                    lineTo(6f, 6f)
                    moveTo(14f, 20f)
                    lineTo(15.25f, 17.5f)
                    lineTo(18f, 18f)
                    moveTo(14f, 4f)
                    lineTo(15.25f, 6.5f)
                    lineTo(18f, 6f)
                    moveTo(17f, 21f)
                    lineTo(14f, 15f)
                    lineTo(10f, 15f)
                    moveTo(17f, 3f)
                    lineTo(14f, 9f)
                    lineTo(15.5f, 12f)
                    moveTo(2f, 12f)
                    lineTo(8.5f, 12f)
                    lineTo(10f, 9f)
                    moveTo(20f, 10f)
                    lineTo(18.5f, 12f)
                    lineTo(20f, 14f)
                    moveTo(22f, 12f)
                    lineTo(15.5f, 12f)
                    lineTo(14f, 15f)
                    moveTo(4f, 10f)
                    lineTo(5.5f, 12f)
                    lineTo(4f, 14f)
                    moveTo(7f, 21f)
                    lineTo(10f, 15f)
                    lineTo(8.5f, 12f)
                    moveTo(7f, 3f)
                    lineTo(10f, 9f)
                    lineTo(14f, 9f)
    }
    val Speaker: ImageVector
        get() =         lucideIcon("speaker") {
                    moveTo(12f, 6f)
                    lineTo(12.01f, 6f)
                    moveTo(12f, 14f)
                    lineTo(12.01f, 14f)
                    moveTo(16f, 14f)
                    curveTo(16f, 16.2091f, 14.2091f, 18f, 12f, 18f)
                    curveTo(9.7909f, 18f, 8f, 16.2091f, 8f, 14f)
                    curveTo(8f, 11.7909f, 9.7909f, 10f, 12f, 10f)
                    curveTo(14.2091f, 10f, 16f, 11.7909f, 16f, 14f)
                    close()
    }
    val Star: ImageVector
        get() =         lucideIcon("star") {
                    moveTo(11.525f, 2.295f)
                    curveTo(11.6144f, 2.1144f, 11.7985f, 2.0001f, 12f, 2.0001f)
                    curveTo(12.2015f, 2.0001f, 12.3856f, 2.1144f, 12.475f, 2.295f)
                    lineTo(14.785f, 6.974f)
                    curveTo(15.0939f, 7.5991f, 15.6901f, 8.0327f, 16.38f, 8.134f)
                    lineTo(21.546f, 8.89f)
                    curveTo(21.7457f, 8.9189f, 21.9116f, 9.0587f, 21.974f, 9.2506f)
                    curveTo(22.0364f, 9.4425f, 21.9845f, 9.6531f, 21.84f, 9.794f)
                    lineTo(18.104f, 13.432f)
                    curveTo(17.6038f, 13.9194f, 17.3754f, 14.6216f, 17.493f, 15.31f)
                    lineTo(18.375f, 20.45f)
                    curveTo(18.4103f, 20.6496f, 18.3286f, 20.8519f, 18.1645f, 20.971f)
                    curveTo(18.0005f, 21.0901f, 17.7829f, 21.1053f, 17.604f, 21.01f)
                    lineTo(12.986f, 18.582f)
                    curveTo(12.3683f, 18.2577f, 11.6307f, 18.2577f, 11.013f, 18.582f)
                    lineTo(6.396f, 21.01f)
                    curveTo(6.2171f, 21.1047f, 6f, 21.0893f, 5.8363f, 20.9702f)
                    curveTo(5.6726f, 20.8512f, 5.591f, 20.6493f, 5.626f, 20.45f)
                    lineTo(6.507f, 15.311f)
                    curveTo(6.6251f, 14.6223f, 6.3966f, 13.9195f, 5.896f, 13.432f)
                    lineTo(2.16f, 9.795f)
                    curveTo(2.0143f, 9.6543f, 1.9616f, 9.4428f, 2.0241f, 9.2502f)
                    curveTo(2.0866f, 9.0575f, 2.2534f, 8.9174f, 2.454f, 8.889f)
                    lineTo(7.619f, 8.134f)
                    curveTo(8.3097f, 8.0335f, 8.9068f, 7.5998f, 9.216f, 6.974f)
                    close()
    }
    val Sun: ImageVector
        get() =         lucideIcon("sun") {
                    moveTo(12f, 2f)
                    lineTo(12f, 4f)
                    moveTo(12f, 20f)
                    lineTo(12f, 22f)
                    moveTo(4.93f, 4.93f)
                    lineTo(6.34f, 6.34f)
                    moveTo(17.66f, 17.66f)
                    lineTo(19.07f, 19.07f)
                    moveTo(2f, 12f)
                    lineTo(4f, 12f)
                    moveTo(20f, 12f)
                    lineTo(22f, 12f)
                    moveTo(6.34f, 17.66f)
                    lineTo(4.93f, 19.07f)
                    moveTo(19.07f, 4.93f)
                    lineTo(17.66f, 6.34f)
                    moveTo(16f, 12f)
                    curveTo(16f, 14.2091f, 14.2091f, 16f, 12f, 16f)
                    curveTo(9.7909f, 16f, 8f, 14.2091f, 8f, 12f)
                    curveTo(8f, 9.7909f, 9.7909f, 8f, 12f, 8f)
                    curveTo(14.2091f, 8f, 16f, 9.7909f, 16f, 12f)
                    close()
    }
    val Thermometer: ImageVector
        get() =         lucideIcon("thermometer") {
                    moveTo(14f, 4f)
                    lineTo(14f, 14.54f)
                    curveTo(15.5679f, 15.4452f, 16.3323f, 17.2906f, 15.8637f, 19.0394f)
                    curveTo(15.3951f, 20.7881f, 13.8104f, 22.0041f, 12f, 22.0041f)
                    curveTo(10.1896f, 22.0041f, 8.6049f, 20.7881f, 8.1363f, 19.0394f)
                    curveTo(7.6677f, 17.2906f, 8.4321f, 15.4452f, 10f, 14.54f)
                    lineTo(10f, 4f)
                    curveTo(10f, 2.8954f, 10.8954f, 2f, 12f, 2f)
                    curveTo(13.1046f, 2f, 14f, 2.8954f, 14f, 4f)
                    close()
    }
    val ToyBrick: ImageVector
        get() =         lucideIcon("toy-brick") {
                    moveTo(10f, 8f)
                    lineTo(10f, 5f)
                    curveTo(10f, 4.4f, 9.6f, 4f, 9f, 4f)
                    lineTo(6f, 4f)
                    curveTo(5.4477f, 4f, 5f, 4.4477f, 5f, 5f)
                    lineTo(5f, 8f)
                    moveTo(19f, 8f)
                    lineTo(19f, 5f)
                    curveTo(19f, 4.4f, 18.6f, 4f, 18f, 4f)
                    lineTo(15f, 4f)
                    curveTo(14.4477f, 4f, 14f, 4.4477f, 14f, 5f)
                    lineTo(14f, 8f)
    }
    val Trash: ImageVector
        get() =         lucideIcon("trash") {
                    moveTo(10f, 11f)
                    lineTo(10f, 17f)
                    moveTo(14f, 11f)
                    lineTo(14f, 17f)
                    moveTo(19f, 6f)
                    lineTo(19f, 20f)
                    curveTo(19f, 21.1046f, 18.1046f, 22f, 17f, 22f)
                    lineTo(7f, 22f)
                    curveTo(5.8954f, 22f, 5f, 21.1046f, 5f, 20f)
                    lineTo(5f, 6f)
                    moveTo(3f, 6f)
                    lineTo(21f, 6f)
                    moveTo(8f, 6f)
                    lineTo(8f, 4f)
                    curveTo(8f, 2.8954f, 8.8954f, 2f, 10f, 2f)
                    lineTo(14f, 2f)
                    curveTo(15.1046f, 2f, 16f, 2.8954f, 16f, 4f)
                    lineTo(16f, 6f)
    }
    val Tv: ImageVector
        get() =         lucideIcon("tv") {
                    moveTo(17f, 2f)
                    lineTo(12f, 7f)
                    lineTo(7f, 2f)
    }
    val Undo: ImageVector
        get() =         lucideIcon("undo-2") {
                    moveTo(9f, 14f)
                    lineTo(4f, 9f)
                    lineTo(9f, 4f)
                    moveTo(4f, 9f)
                    lineTo(14.5f, 9f)
                    curveTo(17.5376f, 9f, 20f, 11.4624f, 20f, 14.5f)
                    curveTo(20f, 17.5376f, 17.5376f, 20f, 14.5f, 20f)
                    lineTo(11f, 20f)
    }
    val Vibrate: ImageVector
        get() =         lucideIcon("vibrate") {
                    moveTo(2f, 8f)
                    lineTo(4f, 10f)
                    lineTo(2f, 12f)
                    lineTo(4f, 14f)
                    lineTo(2f, 16f)
                    moveTo(22f, 8f)
                    lineTo(20f, 10f)
                    lineTo(22f, 12f)
                    lineTo(20f, 14f)
                    lineTo(22f, 16f)
    }
    val Video: ImageVector
        get() =         lucideIcon("video") {
                    moveTo(16f, 13f)
                    lineTo(21.223f, 16.482f)
                    curveTo(21.3764f, 16.5841f, 21.5735f, 16.5935f, 21.736f, 16.5065f)
                    curveTo(21.8985f, 16.4196f, 21.9999f, 16.2503f, 22f, 16.066f)
                    lineTo(22f, 7.87f)
                    curveTo(22.0001f, 7.6909f, 21.9043f, 7.5255f, 21.7491f, 7.4363f)
                    curveTo(21.5938f, 7.3471f, 21.4027f, 7.3477f, 21.248f, 7.438f)
                    lineTo(16f, 10.5f)
    }
    val VolumeDown: ImageVector
        get() =         lucideIcon("volume-1") {
                    moveTo(11f, 4.702f)
                    curveTo(10.9996f, 4.4172f, 10.8278f, 4.1606f, 10.5647f, 4.0516f)
                    curveTo(10.3015f, 3.9427f, 9.9986f, 4.0028f, 9.797f, 4.204f)
                    lineTo(6.413f, 7.587f)
                    curveTo(6.1492f, 7.8524f, 5.7902f, 8.0011f, 5.416f, 8f)
                    lineTo(3f, 8f)
                    curveTo(2.4477f, 8f, 2f, 8.4477f, 2f, 9f)
                    lineTo(2f, 15f)
                    curveTo(2f, 15.5523f, 2.4477f, 16f, 3f, 16f)
                    lineTo(5.416f, 16f)
                    curveTo(5.7902f, 15.9989f, 6.1492f, 16.1476f, 6.413f, 16.413f)
                    lineTo(9.796f, 19.797f)
                    curveTo(9.9976f, 19.999f, 10.3012f, 20.0596f, 10.5649f, 19.9503f)
                    curveTo(10.8286f, 19.841f, 11.0004f, 19.5835f, 11f, 19.298f)
                    close()
                    moveTo(16f, 9f)
                    curveTo(17.3333f, 10.7778f, 17.3333f, 13.2222f, 16f, 15f)
    }
    val VolumeMute: ImageVector
        get() =         lucideIcon("volume-x") {
                    moveTo(11f, 4.702f)
                    curveTo(11.0046f, 4.4147f, 10.8333f, 4.1538f, 10.5678f, 4.044f)
                    curveTo(10.3024f, 3.9341f, 9.9968f, 3.9975f, 9.797f, 4.204f)
                    lineTo(6.413f, 7.587f)
                    curveTo(6.1492f, 7.8524f, 5.7902f, 8.0011f, 5.416f, 8f)
                    lineTo(3f, 8f)
                    curveTo(2.4477f, 8f, 2f, 8.4477f, 2f, 9f)
                    lineTo(2f, 15f)
                    curveTo(2f, 15.5523f, 2.4477f, 16f, 3f, 16f)
                    lineTo(5.416f, 16f)
                    curveTo(5.7902f, 15.9989f, 6.1492f, 16.1476f, 6.413f, 16.413f)
                    lineTo(9.796f, 19.797f)
                    curveTo(9.9958f, 20.0044f, 10.3021f, 20.0683f, 10.5681f, 19.958f)
                    curveTo(10.8341f, 19.8478f, 11.0055f, 19.5859f, 11f, 19.298f)
                    close()
                    moveTo(16.5f, 14.5f)
                    lineTo(21.5f, 9.5f)
                    moveTo(16.5f, 9.5f)
                    lineTo(21.5f, 14.5f)
    }
    val VolumeUp: ImageVector
        get() =         lucideIcon("volume-2") {
                    moveTo(11f, 4.702f)
                    curveTo(10.9996f, 4.4172f, 10.8278f, 4.1606f, 10.5647f, 4.0516f)
                    curveTo(10.3015f, 3.9427f, 9.9986f, 4.0028f, 9.797f, 4.204f)
                    lineTo(6.413f, 7.587f)
                    curveTo(6.1492f, 7.8524f, 5.7902f, 8.0011f, 5.416f, 8f)
                    lineTo(3f, 8f)
                    curveTo(2.4477f, 8f, 2f, 8.4477f, 2f, 9f)
                    lineTo(2f, 15f)
                    curveTo(2f, 15.5523f, 2.4477f, 16f, 3f, 16f)
                    lineTo(5.416f, 16f)
                    curveTo(5.7902f, 15.9989f, 6.1492f, 16.1476f, 6.413f, 16.413f)
                    lineTo(9.796f, 19.797f)
                    curveTo(9.9976f, 19.999f, 10.3012f, 20.0596f, 10.5649f, 19.9503f)
                    curveTo(10.8286f, 19.841f, 11.0004f, 19.5835f, 11f, 19.298f)
                    close()
                    moveTo(16f, 9f)
                    curveTo(17.3333f, 10.7778f, 17.3333f, 13.2222f, 16f, 15f)
                    moveTo(19.364f, 18.364f)
                    curveTo(21.0519f, 16.6762f, 22.0001f, 14.387f, 22.0001f, 12f)
                    curveTo(22.0001f, 9.613f, 21.0519f, 7.3238f, 19.364f, 5.636f)
    }
    val Wifi: ImageVector
        get() =         lucideIcon("wifi") {
                    moveTo(12f, 20f)
                    lineTo(12.01f, 20f)
                    moveTo(2f, 8.82f)
                    curveTo(7.694f, 3.7271f, 16.306f, 3.7271f, 22f, 8.82f)
                    moveTo(5f, 12.859f)
                    curveTo(8.8884f, 9.0476f, 15.1116f, 9.0476f, 19f, 12.859f)
                    moveTo(8.5f, 16.429f)
                    curveTo(10.4442f, 14.5233f, 13.5558f, 14.5233f, 15.5f, 16.429f)
    }
    val Wind: ImageVector
        get() =         lucideIcon("wind") {
                    moveTo(12.8f, 19.6f)
                    curveTo(13.4961f, 20.1221f, 14.45f, 20.1342f, 15.1591f, 19.6298f)
                    curveTo(15.8683f, 19.1255f, 16.1699f, 18.2205f, 15.9052f, 17.3916f)
                    curveTo(15.6405f, 16.5627f, 14.8702f, 16f, 14f, 16f)
                    lineTo(2f, 16f)
                    moveTo(17.5f, 8f)
                    curveTo(18.2054f, 7.0595f, 19.4708f, 6.7375f, 20.5399f, 7.2265f)
                    curveTo(21.609f, 7.7156f, 22.193f, 8.8834f, 21.9427f, 10.0321f)
                    curveTo(21.6925f, 11.1809f, 20.6757f, 12f, 19.5f, 12f)
                    lineTo(2f, 12f)
                    moveTo(9.8f, 4.4f)
                    curveTo(10.4961f, 3.8779f, 11.45f, 3.8658f, 12.1591f, 4.3702f)
                    curveTo(12.8683f, 4.8745f, 13.1699f, 5.7795f, 12.9052f, 6.6084f)
                    curveTo(12.6405f, 7.4373f, 11.8702f, 8f, 11f, 8f)
                    lineTo(2f, 8f)
    }
    val Workflow: ImageVector
        get() =         lucideIcon("workflow") {
                    moveTo(7f, 11f)
                    lineTo(7f, 15f)
                    curveTo(7f, 16.1046f, 7.8954f, 17f, 9f, 17f)
                    lineTo(13f, 17f)
    }
    val X: ImageVector
        get() =         lucideIcon("x") {
                    moveTo(18f, 6f)
                    lineTo(6f, 18f)
                    moveTo(6f, 6f)
                    lineTo(18f, 18f)
    }
    val Zap: ImageVector
        get() =         lucideIcon("zap") {
                    moveTo(15.914f, 4f)
                    curveTo(16.147f, 3.3396f, 15.8922f, 2.6059f, 15.2999f, 2.2322f)
                    curveTo(14.7077f, 1.8586f, 13.9357f, 1.9444f, 13.44f, 2.439f)
                    lineTo(4.44f, 11.439f)
                    curveTo(4.0108f, 11.8679f, 3.8823f, 12.5131f, 4.1143f, 13.0737f)
                    curveTo(4.3464f, 13.6343f, 4.8933f, 13.9999f, 5.5f, 14f)
                    lineTo(9.502f, 14f)
                    curveTo(9.6641f, 14.0002f, 9.816f, 14.079f, 9.9096f, 14.2113f)
                    curveTo(10.0032f, 14.3436f, 10.0268f, 14.5131f, 9.973f, 14.666f)
                    lineTo(8.086f, 20f)
                    curveTo(7.8529f, 20.6606f, 8.108f, 21.3946f, 8.7007f, 21.7681f)
                    curveTo(9.2933f, 22.1417f, 10.0656f, 22.0553f, 10.561f, 21.56f)
                    lineTo(19.561f, 12.56f)
                    curveTo(19.9896f, 12.1309f, 20.1176f, 11.4859f, 19.8854f, 10.9257f)
                    curveTo(19.6532f, 10.3654f, 19.1065f, 10.0001f, 18.5f, 10f)
                    lineTo(14.503f, 10f)
                    curveTo(14.3405f, 10.0002f, 14.188f, 9.9215f, 14.0941f, 9.7888f)
                    curveTo(14.0003f, 9.6562f, 13.9767f, 9.4862f, 14.031f, 9.333f)
                    close()
    }}
