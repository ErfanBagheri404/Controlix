package com.erfanbagheri.controlix.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.theme.Accent
import com.erfanbagheri.controlix.ui.theme.Danger
import com.erfanbagheri.controlix.ui.theme.Gold
import com.erfanbagheri.controlix.ui.theme.PaperFaint

/** Sheet callbacks live in Nav and are passed through here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    model: DeviceModel,
    repo: IrCodeRepository?,
    transmitter: IrTransmitter,
    onOpenDevice: (SavedDevice) -> Unit,
    onAddDevice: () -> Unit,
    onToggleDrawer: () -> Unit,
    onEdit: (SavedDevice) -> Unit,
    onShare: (SavedDevice) -> Unit,
    toast: ToastState,
) {
    var sheetDevice by remember { mutableStateOf<SavedDevice?>(null) }
    var selectedRoom by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().applyTopInset()) {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).bgTile(14.dp), contentAlignment = Alignment.Center) {
                ActionIconView(ActionIcon.Menu, 22.dp, MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.pressable(onToggleDrawer).padding(8.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("Controlix", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(44.dp).bgTile(14.dp), contentAlignment = Alignment.Center) {
                ActionIconView(ActionIcon.Add, 22.dp, MaterialTheme.colorScheme.primary,
                    modifier = Modifier.pressable(onAddDevice).padding(8.dp))
            }
        }

        val devices = model.devices
        if (devices.isEmpty()) {
            EmptyDeck(onAddDevice)
        } else {
            Spacer(Modifier.height(20.dp))
            val rooms = devices.map { Room.fromSlug(it.roomSlug) }.distinct()
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                val tabs = listOf(null to "All") + rooms.map { it.slug to it.display }
                tabs.forEach { (slug, label) ->
                    Text(label, style = MaterialTheme.typography.titleSmall,
                        color = if (selectedRoom == slug) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.pressable { selectedRoom = slug }.padding(horizontal = 12.dp, vertical = 16.dp))
                }
            }
            ContentSwap(selectedRoom, Modifier.weight(1f)) { room ->
            val shown = devices.filter { room == null || Room.fromSlug(it.roomSlug).slug == room }
            if (shown.isEmpty()) {
                Text("No remotes in this room. Choose All to see your devices.", Modifier.padding(24.dp))
            }
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 24.dp, end = 24.dp, top = 4.dp,
                    bottom = (ScreenChrome.BOTTOM_SPACE_DP + 16).dp,
                ),
            ) {
                items(shown, key = { it.remoteId }) { dev ->
                    DeviceTile(
                        device = dev,
                        repo = repo,
                        transmitter = transmitter,
                        onClick = { if (dev.enabled) onOpenDevice(dev) else toast.show("Remote disabled. Long-press to edit it.") },
                        onLongClick = { sheetDevice = dev },
                    )
                }
            }
            }
        }
    }

    sheetDevice?.let { dev ->
        DeviceActionSheet(
            dev = dev,
            model = model,
            toast = toast,
            onEdit = { onEdit(it) },
            onShare = { onShare(it) },
            onDismiss = { sheetDevice = null },
        )
    }
}

/** The long-press action sheet. Shared by Home tiles and the pad's overflow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceActionSheet(
    dev: SavedDevice,
    model: DeviceModel,
    toast: ToastState,
    onEdit: (SavedDevice) -> Unit,
    onShare: (SavedDevice) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 32.dp)) {
            Text(dev.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(dev.brand, style = MaterialTheme.typography.labelSmall, color = PaperFaint)
            Spacer(Modifier.height(20.dp))
            SheetAction("Edit", "name, room, shortcut") { onDismiss(); onEdit(dev) }
            SheetAction(
                if (dev.pinned) "Unpin" else "Pin",
                if (dev.pinned) "remove the star" else "mark with a star",
            ) { model.togglePin(dev.remoteId); onDismiss(); toast.show(if (dev.pinned) "${dev.name} unpinned" else "${dev.name} pinned") }
            // Custom remotes have no DB id to encode — nothing to scan.
            if (dev.remoteId >= 0) {
                SheetAction("Share", "show QR code") { onDismiss(); onShare(dev) }
            }
            SheetAction("Delete", null, destructive = true) {
                model.remove(dev.remoteId)
                onDismiss()
                toast.show("${dev.name} removed", "Undo") { model.save(dev) }
            }
        }
    }
}

@Composable
private fun SheetAction(label: String, hint: String?, destructive: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(onClick).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).bgTile(12.dp, if (destructive) Danger.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            val icon = when {
                label == "Delete" -> ActionIcon.Trash
                label.contains("Pin", true) -> ActionIcon.Star
                label == "Edit" -> ActionIcon.Edit
                else -> ActionIcon.Share
            }
            ActionIconView(icon, 18.dp, if (destructive) Danger else if (label.contains("Pin", true)) Gold else MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleMedium, color = if (destructive) Danger else MaterialTheme.colorScheme.onSurface)
            hint?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = PaperFaint) }
        }
    }
}

@Composable
private fun EmptyDeck(onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp).pressable(onAdd).bgTile(20.dp).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No devices yet", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Tap to add your first remote.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeviceTile(
    device: SavedDevice,
    repo: IrCodeRepository?,
    transmitter: IrTransmitter,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val enabledAlpha by animateFloatAsState(if (device.enabled) 1f else 0.45f, label = "enabled")
    val glyph = remember(device.categorySlug) { LucideCategoryIcons.forCategory(device.categorySlug) }
    Box {
        Column(
            Modifier.fillMaxWidth().height(150.dp).bgTile(20.dp)
                .combinedPressable(onClick = onClick, onLongClick = onLongClick)
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Box(Modifier.size(42.dp).bgTile(12.dp, Accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    LucideIcon(glyph, 24.dp, Accent)
                }
            }
            Column(Modifier.graphicsLayer { alpha = enabledAlpha }) {
                Text(device.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(device.brand); append(" · "); append(device.buttonCount)
                        if (Room.fromSlug(device.roomSlug) != Room.General) {
                            append(" · "); append(Room.fromSlug(device.roomSlug).display)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall, color = PaperFaint,
                )
            }
        }
        if (device.pinned) {
            // Gold star badge — pinned marker, top-right corner.
            Box(
                Modifier.align(Alignment.TopEnd).padding(14.dp).size(24.dp)
                    .bgTile(12.dp, Gold.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                ActionIconView(ActionIcon.Star, 15.dp, Gold, strokeWidth = 3.dp)
            }
        }
    }
}
