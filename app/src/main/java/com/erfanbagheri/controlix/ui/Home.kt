package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.ui.theme.PaperFaint

data class DeviceEntry(
    val remoteId: Int,
    val name: String,
    val buttonCount: Int,
    val categorySlug: String,
)

/**
 * The couch deck. Header row: hamburger (opens the menu drawer) on the left,
 * plus (add device) at the far right — the only two actions that matter
 * here. Devices live in a 2-column tile grid; tools are in the drawer.
 * No sub-header copy — the deck is the interface.
 */
@Composable
fun HomeScreen(
    devices: List<DeviceEntry>,
    codeCount: Int,
    onOpenMenu: () -> Unit,
    onAddDevice: () -> Unit,
    onOpenDevice: (Int) -> Unit,
    onRemoveDevice: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(56.dp))
        // Masthead — hamburger left, wordmark center, plus right.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIconView(
                ActionIcon.Menu,
                26.dp,
                MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.pressable(onOpenMenu).padding(6.dp),
            )
            Spacer(Modifier.weight(1f))
            Text("Controlix", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            ActionIconView(
                ActionIcon.Add,
                26.dp,
                MaterialTheme.colorScheme.primary,
                modifier = Modifier.pressable(onAddDevice).padding(6.dp),
            )
        }
        Spacer(Modifier.height(24.dp))

        if (devices.isEmpty()) {
            EmptyDeck(onAddDevice)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(devices, key = { "d${it.remoteId}" }) { dev ->
                    DeviceTile(dev, onOpenDevice, onRemoveDevice)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** First-run deck: one invite tile with a plus, sized like a device tile. */
@Composable
private fun EmptyDeck(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .bgTile(16.dp)
                .pressable(onAdd),
            contentAlignment = Alignment.Center,
        ) {
            ActionIconView(ActionIcon.Add, 36.dp, MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Add your first device",
            style = MaterialTheme.typography.bodyMedium,
            color = PaperFaint,
        )
    }
}

/**
 * Device tile: category icon in an accent-tinted chip top-left, name and
 * count bottom-left. Filled surface, no strokes. Long-press removes.
 */
@Composable
private fun DeviceTile(
    dev: DeviceEntry,
    onOpen: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .bgTile(16.dp)
            .combinedPressable(
                onClick = { onOpen(dev.remoteId) },
                onLongClick = { onRemove(dev.remoteId) },
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            MaterialIcon(
                CategoryIcons.forCategory(dev.categorySlug),
                24.dp,
                MaterialTheme.colorScheme.primary,
            )
        }
        Column {
            Text(
                dev.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                "${dev.buttonCount} buttons",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
