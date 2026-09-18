package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.ui.icons.Lucide

/**
 * Lucide icon set — ISC licensed, redistributable, stroke-only 24px glyphs.
 * Replaces the Material product silhouettes for categories and the pad's
 * action keys, giving the whole app one coherent line family.
 */
@Composable
fun LucideIcon(
    image: ImageVector,
    iconSize: Dp = 24.dp,
    tint: Color,
    strokeWidth: Dp = 2.dp,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = image,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(iconSize),
    )
}

/** Category slug -> Lucide glyph. */
object LucideCategoryIcons {
    fun forCategory(slug: String): ImageVector = when (slug) {
        "tvs", "tv_tuner", "universal_tv_remotes" -> Lucide.Tv
        "acs", "thermostats" -> Lucide.Snowflake
        "air_purifiers", "humidifiers", "fans", "air_conditioners" -> Lucide.Wind
        "heaters", "fireplaces" -> Lucide.Flame
        "projectors" -> Lucide.Projector
        "monitors", "computers", "touchscreen_displays" -> Lucide.Monitor
        "soundbars", "speakers" -> Lucide.Speaker
        "audio_and_video_receivers", "head_units", "car_multimedia" -> Lucide.AudioLines
        "cable_boxes", "dvb-t", "converters", "multimedia" -> Lucide.Radio
        "cd_players", "dvd_players", "blu-ray", "laserdisc", "minidisc", "vcr" -> Lucide.Disc
        "cameras", "cctv" -> Lucide.Camera
        "consoles", "toys" -> Lucide.Gamepad
        "streaming_devices", "kvm", "digital_signs" -> Lucide.Cast
        "clocks", "picture_frames" -> Lucide.Clock
        "vacuum_cleaners", "dust_collectors", "window_cleaners" -> Lucide.Zap
        "iodn_irblaster" -> Lucide.Zap
        else -> Lucide.Cpu
    }
}
