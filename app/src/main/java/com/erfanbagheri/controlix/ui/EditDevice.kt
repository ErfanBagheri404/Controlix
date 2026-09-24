package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.ui.theme.Ink
import com.erfanbagheri.controlix.ui.theme.Accent
import com.erfanbagheri.controlix.ui.theme.Danger
import com.erfanbagheri.controlix.ui.theme.PaperFaint
import com.erfanbagheri.controlix.widget.WidgetBinding
import com.erfanbagheri.controlix.widget.WidgetStore

/**
 * Device edit screen. Check (save) + back at top, then name input with
 * room presets, category readout, shortcut toggle, enable toggle, and
 * delete.
 */
@Composable
fun EditDeviceScreen(
    device: SavedDevice,
    model: DeviceModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(device.name) }
    var roomIdx by remember { mutableIntStateOf(Room.indexFor(device.roomSlug)) }
    var shortcutOn by remember { mutableStateOf(device.pinned) }
    var enabledOn by remember { mutableStateOf(device.enabled) }
    val ctx = LocalContext.current

    fun save() {
        val updated = device.copy(
            name = name.ifBlank { device.name },
            roomSlug = Room.entries[roomIdx].slug,
            pinned = shortcutOn,
            enabled = enabledOn,
        )
        model.save(updated)
        // "Add to home screen" doubles as the widget target: pin the tile
        // and bind the single-button widget to Power. Only clear the target
        // when it belongs to this device — never clobber another's binding.
        // ponytail: key picker (mute/vol) comes with the widget settings
        // screen, not before.
        val current = WidgetStore.target(ctx)
        when {
            shortcutOn -> WidgetStore.setTarget(ctx, WidgetBinding.Target(device.remoteId, "power"))
            current?.remoteId == device.remoteId -> WidgetStore.setTarget(ctx, null)
        }
        onDone()
    }

    Column(
        Modifier.fillMaxSize().applyTopInset().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ActionIconView(ActionIcon.Back, 26.dp, MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.pressable(onBack).padding(6.dp))
            Spacer(Modifier.weight(1f))
            ActionIconView(ActionIcon.Check, 26.dp, Accent,
                modifier = Modifier.pressable(::save).padding(6.dp))
        }

        Spacer(Modifier.height(28.dp))
        Text("Edit device", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(24.dp))

        Text("Name", style = MaterialTheme.typography.labelLarge, color = PaperFaint)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            placeholder = { Text("e.g. Living room TV") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))
        Text("Room", style = MaterialTheme.typography.labelLarge, color = PaperFaint)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Room.entries.forEach { room ->
                val selected = roomIdx == room.ordinal
                Text(
                    room.display,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Accent else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .bgTile(14.dp, if (selected) Accent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
                        .pressable { roomIdx = room.ordinal }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Category", style = MaterialTheme.typography.labelLarge, color = PaperFaint)
        Spacer(Modifier.height(8.dp))
        Text(device.categorySlug.replace('_', ' ').replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyLarge)

        Spacer(Modifier.height(24.dp))
        ToggleRow("Add to home screen", "Pin shortcut button on tile", shortcutOn) { shortcutOn = it }
        Spacer(Modifier.height(12.dp))
        ToggleRow("Enabled", "Disabled devices are dimmed", enabledOn) { enabledOn = it }

        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth().pressable { model.remove(device.remoteId); onDone() }.padding(vertical = 14.dp)) {
            Text("Delete device", style = MaterialTheme.typography.titleMedium, color = Danger)
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ToggleRow(label: String, hint: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().bgTile(16.dp).pressable { onToggle(!checked) }.padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = PaperFaint)
        }
        Spacer(Modifier.width(12.dp))
        // Pill toggle: track + always-contrasting thumb (never same hue).
        val trackColor = if (checked) Accent else MaterialTheme.colorScheme.surfaceVariant
        val thumbColor = if (checked) Ink else MaterialTheme.colorScheme.onSurface
        Canvas(Modifier.size(44.dp, 24.dp)) {
            val r = size.height / 2f
            drawRoundRect(trackColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
            val cx = if (checked) size.width - r else r
            drawCircle(thumbColor, r * 0.66f, center = Offset(cx, r))
        }
    }
}
