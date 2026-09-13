package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class DeviceEntry(
    val remoteId: Int,
    val name: String,
    val buttonCount: Int,
    val categorySlug: String,
)

/**
 * The couch deck: one list, three sections — devices, add, tools.
 * No cards, no elevation, no gradients. Hierarchy comes from spacing,
 * type scale, and the single ember accent. Rows stay tall for thumb reach.
 */
@Composable
fun HomeScreen(
    devices: List<DeviceEntry>,
    codeCount: Int,
    onOpenDevice: (Int) -> Unit,
    onAddDevice: () -> Unit,
    onSweep: () -> Unit,
    onSelfTest: () -> Unit,
    onRemoveDevice: (Int) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        // Masthead — wordmark + live DB census in mono.
        item(key = "masthead") {
            Column(Modifier.padding(top = 56.dp, bottom = 8.dp)) {
                Text("Controlix", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${String.format("%,d", codeCount)} codes, no internet, no account",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        if (devices.isNotEmpty()) {
            item(key = "devhead") { SectionHead("Your devices") }
            items(devices, key = { "d${it.remoteId}" }) { dev ->
                DeviceRow(dev, onOpenDevice, onRemoveDevice)
            }
        }

        item(key = "add") { AddRow(onAddDevice) }

        item(key = "toolshead") { SectionHead("Tools") }
        item(key = "sweep") {
            ToolRow(
                "Power-off sweep",
                "Fire every TV power code we know",
                ActionIcon.Sweep,
                onSweep,
            )
        }
        item(key = "selftest") {
            ToolRow(
                "IR self-test",
                "Check the blaster with any camera",
                ActionIcon.CameraTest,
                onSelfTest,
            )
        }
        item(key = "tail") { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun DeviceRow(
    dev: DeviceEntry,
    onOpen: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedPressable(
                onClick = { onOpen(dev.remoteId) },
                onLongClick = { onRemove(dev.remoteId) },
            )
            .padding(vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(glyphFor(dev.categorySlug), 28.dp, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(dev.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "${dev.buttonCount} buttons",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        ActionIconView(ActionIcon.ChevRight, 20.dp, MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun AddRow(onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onAdd).padding(vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        ActionIconView(ActionIcon.Add, 22.dp, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(
            "Add device",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ToolRow(title: String, sub: String, icon: ActionIcon, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onOpen).padding(vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIconView(icon, 22.dp, MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        ActionIconView(ActionIcon.ChevRight, 18.dp, MaterialTheme.colorScheme.outline)
    }
}
