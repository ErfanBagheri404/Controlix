package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HeatPump
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/**
 * Material vector icons for the places a recognizable product silhouette
 * beats our hand-drawn line family: category tiles and device tiles.
 * Hand-drawn set stays for pad keys and actions (vol/ch/power) — those
 * are hardware glyphs, not products.
 */
object CategoryIcons {
    fun forCategory(slug: String): ImageVector = when (slug) {
        "tvs", "tv_tuner", "universal_tv_remotes" -> Icons.Filled.LiveTv
        "acs" -> Icons.Filled.Tune
        "air_purifiers", "humidifiers", "fans" -> Icons.Filled.Air
        "projectors" -> Icons.Filled.MeetingRoom
        "monitors", "computers", "touchscreen_displays" -> Icons.Filled.DesktopWindows
        "soundbars", "speakers" -> Icons.Filled.Speaker
        "audio_and_video_receivers", "head_units", "car_multimedia" -> Icons.Filled.Headphones
        "cable_boxes", "dvb-t", "converters", "multimedia" -> Icons.Filled.SettingsRemote
        "cd_players", "dvd_players", "blu-ray", "laserdisc", "minidisc", "vcr" -> Icons.Filled.Language
        "cameras", "cctv" -> Icons.Filled.PhotoCamera
        "heaters", "fireplaces" -> Icons.Filled.LocalFireDepartment
        "vacuum_cleaners", "dust_collectors", "window_cleaners" -> Icons.Filled.HeatPump
        "consoles", "toys" -> Icons.Filled.Toys
        "streaming_devices", "kvm", "digital_signs" -> Icons.Filled.DesktopWindows
        "clocks", "picture_frames" -> Icons.Filled.AccessTime
        "iodn_irblaster" -> Icons.Filled.Cable
        else -> Icons.Filled.Computer
    }
}

@Composable
fun MaterialIcon(
    image: ImageVector,
    iconSize: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Image(
        imageVector = image,
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(iconSize),
    )
}
