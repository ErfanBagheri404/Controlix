package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.ui.icons.Lucide

/**
 * Action + category glyphs, drawn from the Lucide set (ISC licensed).
 * Kept as the same call surface the app already used, so every call site
 * renders Lucide without changes.
 *
 * [autoMirror] flips the glyph in RTL. Set it on *reading-direction* chrome
 * (back arrows, row chevrons) so it follows the layout. Leave it off for
 * hardware glyphs — D-pad left/right, volume, power — those name a physical
 * button on the device and must not flip.
 */
@Composable
fun ActionIconView(
    icon: ActionIcon,
    size: Dp = 24.dp,
    tint: Color,
    strokeWidth: Dp = 2.5.dp,
    modifier: Modifier = Modifier,
    autoMirror: Boolean = false,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(
        imageVector = actionVector(icon),
        contentDescription = null,
        tint = tint,
        modifier = modifier
            .size(size)
            .then(if (autoMirror && rtl) Modifier.graphicsLayer { scaleX = -1f } else Modifier),
    )
}

/** ActionIcon -> Lucide vector. Hardware glyphs stay literal, not decorative. */
fun actionVector(icon: ActionIcon) = when (icon) {
    ActionIcon.Power -> Lucide.Power
    ActionIcon.VolUp -> Lucide.VolumeUp
    ActionIcon.VolDown -> Lucide.VolumeDown
    ActionIcon.ChUp -> Lucide.ChevronUp
    ActionIcon.ChDown -> Lucide.ChevronDown
    ActionIcon.Mute -> Lucide.VolumeMute
    ActionIcon.Up -> Lucide.ArrowUp
    ActionIcon.Down -> Lucide.ArrowDown
    ActionIcon.Left -> Lucide.ArrowLeft
    ActionIcon.Right -> Lucide.ArrowRight
    ActionIcon.Ok -> Lucide.CircleDot
    ActionIcon.Back -> Lucide.ArrowLeft
    ActionIcon.Add -> Lucide.Plus
    ActionIcon.Check -> Lucide.Check
    ActionIcon.Cross -> Lucide.X
    ActionIcon.Sweep -> Lucide.PowerOff
    ActionIcon.CameraTest -> Lucide.Camera
    ActionIcon.Trash -> Lucide.Trash
    ActionIcon.ChevRight -> Lucide.ChevronRight
    ActionIcon.Menu -> Lucide.Menu
    ActionIcon.Macros -> Lucide.ListChecks
    ActionIcon.QrScan -> Lucide.QrCode
    ActionIcon.Star -> Lucide.Star
    ActionIcon.Share -> Lucide.Share
    ActionIcon.Edit -> Lucide.Pencil
    ActionIcon.Source -> Lucide.Cast
    ActionIcon.Play -> Lucide.Play
    ActionIcon.Dots -> Lucide.MoreVertical
    ActionIcon.DotsH -> Lucide.Ellipsis
    ActionIcon.Home -> Lucide.House
    ActionIcon.Minus -> Lucide.Minus
    ActionIcon.ChevronUp -> Lucide.ChevronUp
    ActionIcon.ChevronDown -> Lucide.ChevronDown
    ActionIcon.ChevronLeft -> Lucide.ChevronLeft
    ActionIcon.ChevronRight -> Lucide.ChevronRight
    ActionIcon.Info -> Lucide.Info
    ActionIcon.Database -> Lucide.Disc
    ActionIcon.Gauge -> Lucide.Gauge
    // Waveform: the analyzer's mark/space strip.
    ActionIcon.Waveform -> Lucide.AudioLines
    // History: clock face, rewind hand.
    ActionIcon.History -> Lucide.Undo
}
