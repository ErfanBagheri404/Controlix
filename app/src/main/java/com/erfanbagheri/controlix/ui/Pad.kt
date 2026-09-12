package com.erfanbagheri.controlix.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.theme.Confirm
import com.erfanbagheri.controlix.ui.theme.Ember
import com.erfanbagheri.controlix.ui.theme.Hairline
import com.erfanbagheri.controlix.ui.theme.InkFloat
import com.erfanbagheri.controlix.ui.theme.InkRaised
import com.erfanbagheri.controlix.ui.theme.Paper
import com.erfanbagheri.controlix.ui.theme.PaperDim

/**
 * The pad: a physical-remote mental model, not a flat button grid.
 * Power island top, D-pad center, volume left rail, channel right rail,
 * numbers + colors below. Every press fires the pulse.
 */

private enum class Zone { Power, Vol, Ch, Nav, Media, Num, Input, Color, Misc }

private fun zoneOf(name: String): Zone = when {
    name.matches(Regex("power|on|off|on/off|standby", RegexOption.IGNORE_CASE)) -> Zone.Power
    name.matches(Regex("vol(ume)?(up|\\+|-|down)?|mute|audio", RegexOption.IGNORE_CASE)) -> Zone.Vol
    name.matches(Regex("ch(annel)?(up|\\+|-|down|next|prev(ious)?)?", RegexOption.IGNORE_CASE)) -> Zone.Ch
    name.matches(Regex("up|down|left|right|ok|select|back|return|exit|menu|home|info|guide|list", RegexOption.IGNORE_CASE)) -> Zone.Nav
    name.matches(Regex("play.*|pause|stop|rec(ord)?|ff(ast)?|rew(ind)?|next|prev(ious)?|eject", RegexOption.IGNORE_CASE)) -> Zone.Media
    name.matches(Regex("[0-9]|\\u231b|\\*")) -> Zone.Num
    name.matches(Regex("(f1|a|b|c|d)")) -> Zone.Color
    name.matches(Regex("input|source|hdmi.*|av.*|tv/?(radio|video)|component.*|vga.*|dvi|displayport", RegexOption.IGNORE_CASE)) -> Zone.Input
    else -> Zone.Misc
}

@Composable
fun PadScreen(
    repo: IrCodeRepository,
    transmitter: IrTransmitter,
    remoteId: Int,
    onBack: () -> Unit,
) {
    val buttons = remember(remoteId) { repo.buttons(remoteId) }
    val byName = remember(buttons) { buttons.associateBy { it.name } }
    var pulseKey by remember { mutableIntStateOf(0) }
    var lastSent by remember { mutableStateOf<String?>(null) }

    fun send(name: String) {
        val b = byName[name] ?: return
        if (transmitter.transmitButton(b.carrierHz, b.pattern)) {
            pulseKey++
            lastSent = name
        }
    }

    fun has(vararg names: String) = names.any { byName.containsKey(it) }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ Devices", color = Ember, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(8.dp))
            Spacer(Modifier.weight(1f))
            lastSent?.let {
                Text("$it sent", style = MaterialTheme.typography.labelSmall, color = Confirm)
            }
        }
        Spacer(Modifier.height(8.dp))

        val power = PulseGlow(pulseKey = pulseKey, size = 150.dp) {}
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            power
        }

        // --- islands ---
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(InkRaised)
                .border(1.dp, Hairline, RoundedCornerShape(28.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Row 1: power + mute
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PadKey("Power", hot = true, enabled = has("power", "Power", "POWER", "on", "ON"), onClick = {
                    send("power"); send("Power"); send("POWER"); send("on"); send("ON")
                }, modifier = Modifier.weight(1f))
                PadKey("Mute", enabled = has("mute", "Mute", "MUTE"), onClick = { send("mute"); send("Mute"); send("MUTE") }, modifier = Modifier.weight(1f))
            }
            // Row 2: nav + volume/channel rails
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                RailKey("Vol+", up = true, enabled = has("vol+", "volup", "volumeup", "Vol+", "VOLUMEUP", "volume up", "VOL+"), onClick = {
                    send("vol+"); send("volup"); send("volumeup"); send("Vol+"); send("volume up"); send("VOL+")
                }, modifier = Modifier.weight(1f))
                // center: d-pad or fallback grid
                Box(Modifier.weight(2.2f), contentAlignment = Alignment.Center) {
                    DPad(
                        onNav = { name -> send(name) },
                        exists = { n -> has(n, n.lowercase(), n.uppercase()) },
                    )
                }
                RailKey("Ch+", up = true, enabled = has("ch+", "chan+", "channelup", "chup", "Ch+", "CHANNELUP", "channel up"), onClick = {
                    send("ch+"); send("chan+"); send("channelup"); send("chup"); send("Ch+"); send("channel up")
                }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RailKey("Vol-", up = false, enabled = has("vol-", "voldown", "volumedown", "Vol-", "volumedown"), onClick = {
                    send("vol-"); send("voldown"); send("volumedown"); send("Vol-")
                }, modifier = Modifier.weight(1f))
                RailKey("Ch-", up = false, enabled = has("ch-", "chan-", "channeldown", "chdown", "Ch-"), onClick = {
                    send("ch-"); send("chan-"); send("channeldown"); send("chdown")
                }, modifier = Modifier.weight(1f))
                Box(Modifier.weight(2.2f))
            }
        }

        Spacer(Modifier.height(10.dp))
        // Numbers + remaining buttons
        LazyColumn(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items((0..9).map { it.toString() }.filter { byName.containsKey(it) }.chunked(3)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { n ->
                        PadKey(n, enabled = true, onClick = { send(n) }, modifier = Modifier.weight(1f))
                    }
                }
            }
            items(buttons.filter { zoneOf(it.name) in setOf(Zone.Input, Zone.Color, Zone.Media, Zone.Nav, Zone.Misc) && it.name.length > 1 }
                .map { it.name }.distinct().chunked(3)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { name ->
                        PadKey(name.replaceFirstChar { it.uppercase() }, short = true, enabled = true,
                            onClick = { send(name) }, modifier = Modifier.weight(1f))
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DPad(exists: (String) -> Boolean, onNav: (String) -> Unit) {
    // ring of five; center = OK.
    Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
        NavKey("↑", Alignment.TopCenter, enabled = exists("up"), onClick = { onNav("up") })
        NavKey("↓", Alignment.BottomCenter, enabled = exists("down"), onClick = { onNav("down") })
        NavKey("←", Alignment.CenterStart, enabled = exists("left"), onClick = { onNav("left") })
        NavKey("→", Alignment.CenterEnd, enabled = exists("right"), onClick = { onNav("right") })
        NavKey("OK", Alignment.Center, enabled = exists("ok") || exists("select"), onClick = {
            onNav("ok"); onNav("select")
        }, size = 58.dp)
    }
}

@Composable
private fun NavKey(label: String, align: Alignment, enabled: Boolean, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 50.dp) {
    Box(Modifier.fillMaxSize(), contentAlignment = align) {
        PressScale(enabled) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(if (enabled) InkFloat else InkRaised.copy(alpha = 0.4f))
                    .border(1.dp, if (enabled) Hairline else Hairline.copy(alpha = 0.3f), CircleShape)
                    .clickable(enabled = enabled, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = if (enabled) Paper else PaperDim.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun RailKey(label: String, up: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PressScale(enabled) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(0.45f)
                .clip(RoundedCornerShape(18.dp))
                .background(if (enabled) InkFloat else InkRaised.copy(alpha = 0.4f))
                .border(1.dp, if (enabled) Hairline else Hairline.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (up) "▲" else "▼", style = MaterialTheme.typography.labelMedium, color = Ember)
                Text(label, style = MaterialTheme.typography.labelSmall, color = PaperDim)
            }
        }
    }
}

@Composable
private fun PadKey(
    label: String,
    hot: Boolean = false,
    short: Boolean = false,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PressScale(enabled) {
        Box(
            modifier = modifier
                .height(if (short) 40.dp else 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (!enabled) InkRaised.copy(alpha = 0.4f) else if (hot) Ember.copy(alpha = 0.16f) else InkFloat)
                .border(
                    1.dp,
                    if (!enabled) Hairline.copy(alpha = 0.3f) else if (hot) Ember.copy(alpha = 0.8f) else Hairline,
                    RoundedCornerShape(14.dp),
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = if (short) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                color = if (!enabled) PaperDim.copy(alpha = 0.4f) else if (hot) Ember else Paper,
                maxLines = 1, textAlign = TextAlign.Center,
            )
        }
    }
}

/** Spring scale on press — motion that answers action. */
@Composable
private fun PressScale(enabled: Boolean, content: @Composable () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 900f),
        label = "press",
    )
    Box(Modifier.scale(scale)) { content() }
}