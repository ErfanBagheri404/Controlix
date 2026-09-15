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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.ButtonNames
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.theme.Accent
import com.erfanbagheri.controlix.ui.theme.InkFloat
import com.erfanbagheri.controlix.ui.theme.PaperFaint

/**
 * Control screen. Top: back left, device-name dropdown center, power right.
 * Body: D-pad area with corner cursor chevrons + center OK, VOL/CH pill
 * columns on each side, bottom row grid of extra buttons.
 *
 * The device-name dropdown is a modal sheet listing all saved remotes —
 * tap one to switch instantly (no nav pop, same screen).
 */
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
    var emitKey by remember { mutableStateOf<Any?>(null) }
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
            else -> null
        }
        val btn = p ?: buttons.firstOrNull { it.name.equals(name, true) }
            ?: buttons.firstOrNull { it.name.contains(name, true) }
        if (btn != null) {
            transmitter.transmitButton(btn.carrierHz, btn.pattern)
            lastSent = btn.name
            emitKey = Any()
            Feedback.send(null)
        }
    }

    Column(
        Modifier.fillMaxSize().applyTopInset().padding(horizontal = 24.dp),
    ) {
        // Top bar: back · device name dropdown · power.
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ActionIconView(ActionIcon.Back, 26.dp, MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.pressable(onBack).padding(6.dp))
            // Device name dropdown — center.
            Row(Modifier.weight(1f).pressable { switcherOpen = true }.padding(8.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(deviceName ?: "Remote", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(6.dp))
                ActionIconView(ActionIcon.ChevRight, 14.dp, PaperFaint)
            }
            // Power button — top right.
            Box(
                Modifier.size(44.dp).bgTile(22.dp, Accent.copy(alpha = 0.18f))
                    .pressable { fire("power") },
                contentAlignment = Alignment.Center,
            ) {
                ActionIconView(ActionIcon.Power, 24.dp, Accent)
            }
        }

        Spacer(Modifier.height(20.dp))

        // D-pad area with corner chevrons.
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            // Corner cursor chevrons — each points to its own diagonal edge.
            val chevSize = 18.dp
            val chevColor = MaterialTheme.colorScheme.onSurfaceVariant
            Box(Modifier.padding(start = 24.dp, top = 24.dp)) {
                ActionIconView(ActionIcon.Up, chevSize, chevColor, modifier = Modifier.pressable { fire("cursor_up") })
            }
            Box(Modifier.padding(end = 24.dp, top = 24.dp), contentAlignment = Alignment.TopEnd) {
                ActionIconView(ActionIcon.Right, chevSize, chevColor, modifier = Modifier.pressable { fire("cursor_right") })
            }
            Box(Modifier.padding(start = 24.dp, bottom = 24.dp), contentAlignment = Alignment.BottomStart) {
                ActionIconView(ActionIcon.Left, chevSize, chevColor, modifier = Modifier.pressable { fire("cursor_left") })
            }
            Box(Modifier.padding(end = 24.dp, bottom = 24.dp), contentAlignment = Alignment.BottomEnd) {
                ActionIconView(ActionIcon.Down, chevSize, chevColor, modifier = Modifier.pressable { fire("cursor_down") })
            }

            // D-pad: VOL column left, circle center, CH column right.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                // VOL column.
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DPadKey("Vol Up") { fire("volume_up") }
                    Text("VOL", style = MaterialTheme.typography.labelSmall, color = PaperFaint)
                    DPadKey("Vol Down") { fire("volume_down") }
                }
                // Center circle: up / down / left / right / OK.
                Box(contentAlignment = Alignment.Center) {
                    // The circle background.
                    Box(Modifier.size(180.dp).bgTile(90.dp, InkFloat), contentAlignment = Alignment.Center) {
                        Text("OK", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                    // Directional keys around the circle.
                    Box(Modifier.size(180.dp).padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
                        ActionIconView(ActionIcon.Up, 24.dp, MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.pressable { fire("up") })
                    }
                    Box(Modifier.size(180.dp).padding(bottom = 6.dp), contentAlignment = Alignment.BottomCenter) {
                        ActionIconView(ActionIcon.Down, 24.dp, MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.pressable { fire("down") })
                    }
                    Box(Modifier.size(180.dp).padding(start = 6.dp), contentAlignment = Alignment.CenterStart) {
                        ActionIconView(ActionIcon.Left, 24.dp, MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.pressable { fire("left") })
                    }
                    Box(Modifier.size(180.dp).padding(end = 6.dp), contentAlignment = Alignment.CenterEnd) {
                        ActionIconView(ActionIcon.Right, 24.dp, MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.pressable { fire("right") })
                    }
                }
                // CH column.
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DPadKey("Ch Up") { fire("channel_up") }
                    Text("CH", style = MaterialTheme.typography.labelSmall, color = PaperFaint)
                    DPadKey("Ch Down") { fire("channel_down") }
                }
            }
        }

        // Bottom button row: Home · Mute · Back.
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ExtraKey("Home") { fire("home") }
            ExtraKey("Mute") { fire("mute") }
            ExtraKey("Back") { fire("back") }
        }

        // Last-sent readout.
        if (lastSent != null) {
            Text("Last sent: $lastSent", style = MaterialTheme.typography.labelSmall, color = PaperFaint,
                modifier = Modifier.padding(bottom = 8.dp))
        }
    }

    // Device switcher sheet.
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
                        modifier = Modifier.fillMaxWidth().pressable {
                            switcherOpen = false
                            onSwitchDevice(dev)
                        }.padding(vertical = 14.dp),
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun DPadKey(label: String, onPress: () -> Unit) {
    Box(
        Modifier.size(52.dp).bgTile(14.dp, MaterialTheme.colorScheme.surfaceVariant)
            .pressable(onPress),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.first().toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ExtraKey(label: String, onPress: () -> Unit) {
    Box(
        Modifier.size(72.dp).bgTile(16.dp, MaterialTheme.colorScheme.surfaceVariant)
            .pressable(onPress),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
