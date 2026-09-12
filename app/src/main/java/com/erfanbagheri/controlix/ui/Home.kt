package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ui.theme.Confirm
import com.erfanbagheri.controlix.ui.theme.Ember
import com.erfanbagheri.controlix.ui.theme.Hairline
import com.erfanbagheri.controlix.ui.theme.InkRaised
import com.erfanbagheri.controlix.ui.theme.Paper
import com.erfanbagheri.controlix.ui.theme.PaperDim

/**
 * Home = the devices you set up (Mi-Remote-style "my devices") + the two
 * automation tools. Empty state is an invitation, not a shrug.
 */
@Composable
fun HomeScreen(
    devices: List<SavedDevice>,
    repo: IrCodeRepository?,
    onAddDevice: () -> Unit,
    onOpenDevice: (SavedDevice) -> Unit,
    onSweep: () -> Unit,
    onSelfTest: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { Spacer(Modifier.height(28.dp)) }
        item {
            Column {
                Text("Controlix", style = MaterialTheme.typography.displaySmall, color = Paper)
                Spacer(Modifier.height(4.dp))
                val codes = repo?.buttonCount() ?: 0
                Text(
                    if (codes > 0) "$codes codes, no internet, no account"
                    else "No codes bundled in this build",
                    style = MaterialTheme.typography.labelMedium,
                    color = PaperDim,
                )
            }
        }

        if (devices.isEmpty()) {
            item {
                // The one big statement: add your first device.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(InkRaised)
                        .border(1.dp, Hairline, RoundedCornerShape(28.dp))
                        .clickable(onClick = onAddDevice)
                        .padding(28.dp),
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Ember.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            PowerGlyph(size = 30.dp, tint = Ember)
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("Add your TV", style = MaterialTheme.typography.headlineMedium, color = Paper)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Pick the brand, press power until the screen goes dark. Ten seconds, and it works offline forever.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = PaperDim,
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Start", style = MaterialTheme.typography.labelLarge, color = Ember)
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Ember.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("→", color = Ember, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Your devices", style = MaterialTheme.typography.headlineSmall, color = Paper)
                    Text(
                        "Add another",
                        style = MaterialTheme.typography.labelMedium,
                        color = Ember,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onAddDevice)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            items(devices) { dev ->
                DeviceTile(dev) { onOpenDevice(dev) }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tools", style = MaterialTheme.typography.headlineSmall, color = Paper)
                ToolRow("Power-off sweep", "Shut down every TV in range", enabled = repo != null) { onSweep() }
                ToolRow("IR self-test", "Check the blaster through your camera", enabled = true) { onSelfTest() }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun DeviceTile(dev: SavedDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(InkRaised)
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Ember.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            CategoryIcon(dev.glyph, size = 36.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(dev.name, style = MaterialTheme.typography.titleLarge, color = Paper)
            Text(
                dev.brand + if (dev.buttonCount > 0) " · ${dev.buttonCount} buttons" else "",
                style = MaterialTheme.typography.labelMedium, color = PaperDim,
            )
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = PaperDim)
    }
}

@Composable
private fun ToolRow(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(InkRaised.copy(alpha = if (enabled) 1f else 0.4f))
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Paper, modifier = Modifier.weight(1f))
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = PaperDim, textAlign = TextAlign.End)
    }
}