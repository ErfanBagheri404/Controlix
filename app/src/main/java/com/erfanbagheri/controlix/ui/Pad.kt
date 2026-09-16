package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.ButtonNames
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.theme.Accent
import com.erfanbagheri.controlix.ui.theme.PaperFaint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PadScreen(
    deviceName: String?,
    onRename: (String) -> Unit,
    remoteId: Int,
    repo: IrCodeRepository?,
    transmitter: IrTransmitter,
    devices: List<SavedDevice>,
    onSwitchDevice: (SavedDevice) -> Unit,
    onBack: () -> Unit,
) {
    var lastSent by remember { mutableStateOf<String?>(null) }
    var switcherOpen by remember { mutableStateOf(false) }

    val buttons = remember(remoteId) {
        runCatching { repo?.buttons(remoteId) }.getOrNull() ?: emptyList()
    }
    fun fire(name: String) {
        val p = when (name) {
            "power" -> buttons.firstOrNull { ButtonNames.power(it.name) }
            "volume_up" -> buttons.firstOrNull { ButtonNames.volUp(it.name) }
            "volume_down" -> buttons.firstOrNull { ButtonNames.volDown(it.name) }
            "channel_up" -> buttons.firstOrNull { ButtonNames.chUp(it.name) }
            "channel_down" -> buttons.firstOrNull { ButtonNames.chDown(it.name) }
            "mute" -> buttons.firstOrNull { ButtonNames.mute(it.name) }
            "play_pause" -> buttons.firstOrNull { it.name.contains("play", true) }
            "source" -> buttons.firstOrNull { it.name.contains("input", true) }
                ?: buttons.firstOrNull { it.name.contains("source", true) }
            else -> null
        }
        val btn = p ?: buttons.firstOrNull { it.name.equals(name, true) }
            ?: buttons.firstOrNull { it.name.contains(name, true) }
        if (btn != null) {
            transmitter.transmitButton(btn.carrierHz, btn.pattern)
            lastSent = btn.name
            Feedback.send(null)
        }
    }

    Column(
        Modifier.fillMaxSize().applyTopInset().padding(horizontal = 24.dp),
    ) {
        // ── Top bar: back · status pill · power ──────────────────────────
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.Canvas(Modifier.size(10.dp)) {
                    drawCircle(Accent)
                }
                Text(deviceName ?: "Remote", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { fire("power") }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.Power, contentDescription = "Power",
                    tint = Accent, modifier = Modifier.size(26.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── VOL / CH pills + center cluster ──────────────────────────────
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            // VOL pill
            VChPill(label = "VOL", down = Icons.Rounded.Remove, up = Icons.Rounded.Add,
                onDown = { fire("volume_down") }, onUp = { fire("volume_up") },
                modifier = Modifier.weight(1f))

            Spacer(Modifier.width(12.dp))

            // Center cluster
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PadBtn(Icons.Rounded.SettingsInputAntenna, 42.dp, 20.dp) { fire("source") }
                    PadBtn(Icons.Rounded.Mic, 42.dp, 20.dp) { fire("mute") }
                }
                PadBtn(Icons.Rounded.PlayArrow, 60.dp, 28.dp, accent = true) { fire("play_pause") }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PadBtn(Icons.AutoMirrored.Rounded.ArrowBack, 42.dp, 20.dp) { fire("back") }
                    PadBtn(Icons.Rounded.Home, 42.dp, 20.dp) { fire("home") }
                }
                PadBtn(Icons.Rounded.Menu, 36.dp, 16.dp) { fire("menu") }
            }

            Spacer(Modifier.width(12.dp))

            // CH pill
            VChPill(label = "CH", down = Icons.Rounded.KeyboardArrowDown, up = Icons.Rounded.KeyboardArrowUp,
                onDown = { fire("channel_down") }, onUp = { fire("channel_up") },
                modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        if (lastSent != null) {
            Text("Last sent: $lastSent", style = MaterialTheme.typography.labelSmall, color = PaperFaint,
                modifier = Modifier.padding(bottom = 8.dp))
        }
    }

    if (switcherOpen) {
        ModalBottomSheet(
            onDismissRequest = { switcherOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 32.dp)) {
                Text("Switch remote", style = MaterialTheme.typography.titleMedium, color = PaperFaint)
                Spacer(Modifier.height(16.dp))
                devices.forEach { dev ->
                    val isCurrent = dev.remoteId == remoteId
                    Text(
                        "${dev.name}  ·  ${dev.brand}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isCurrent) Accent else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun VChPill(label: String, down: ImageVector, up: ImageVector,
    onDown: () -> Unit, onUp: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().height(140.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween) {
        PadBtn(up, 48.dp, 22.dp) { onUp() }
        Text(label, style = MaterialTheme.typography.titleSmall, color = PaperFaint)
        PadBtn(down, 48.dp, 22.dp) { onDown() }
    }
}

@Composable
private fun PadBtn(icon: ImageVector, size: Dp, iconSize: Dp,
    accent: Boolean = false, onClick: () -> Unit) {
    val bg = if (accent) Accent else MaterialTheme.colorScheme.surfaceVariant
    val tint = if (accent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface
    Box(contentAlignment = Alignment.Center,
        modifier = Modifier.size(size)
            .bgTile(if (accent) 30.dp else 24.dp, bg)
            .pressable(onClick)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}
