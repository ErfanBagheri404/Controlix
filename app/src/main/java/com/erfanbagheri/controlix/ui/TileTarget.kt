package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.data.prefKey
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.quicksettings.TileDevice
import com.erfanbagheri.controlix.quicksettings.TileStore
import com.erfanbagheri.controlix.quicksettings.TileTarget
import com.erfanbagheri.controlix.quicksettings.TileTargetSelection
import com.erfanbagheri.controlix.ui.theme.Accent
import com.erfanbagheri.controlix.ui.theme.PaperFaint

/**
 * Issue #78: choose what the Quick Settings tile fires.
 *
 * Before this the tile fired whichever pad was opened last, so checking the
 * time from a pad silently retargeted it. The picker is the *only* writer of
 * the tile's slot: pick a device, pick one of its keys, and the tile says what
 * it will do before a tap happens.
 *
 * Devices the current DB cannot resolve are not offered — pinning one would
 * produce a tile that cannot fire.
 */
@Composable
fun TileTargetScreen(
    devices: List<SavedDevice>,
    repo: IrCodeRepository?,
    toast: ToastState,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val store = remember { TileStore(ctx) }
    var pinned by remember { mutableStateOf(store.target()) }
    var pickDevice by remember { mutableStateOf<SavedDevice?>(null) }

    // One DB read per screen open.
    val index = remember(repo) { repo?.remoteIndex() }
    val keysFor = remember(repo) { mutableMapOf<Int, List<String>>() }

    fun keyList(dev: SavedDevice): List<String> {
        val r = repo ?: return emptyList()
        return keysFor.getOrPut(dev.remoteId) {
            runCatching {
                val own = r.buttons(dev.remoteId)
                val siblings = r.brandIdOf(dev.remoteId)?.let { r.brandButtons(it) } ?: own
                EffectiveButtons.resolve(dev.remoteId, own, siblings)
                    .map { it.key }
                    .distinct()
            }.getOrDefault(emptyList())
        }
    }

    Column(Modifier.fillMaxSize().applyTopInset().applyBottomInset().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Tile target", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            Text(
                "Done",
                style = MaterialTheme.typography.titleMedium,
                color = Accent,
                modifier = Modifier.pressable(onBack).padding(8.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            pinned?.let { "${it.device.brandName} · ${it.key.replace('_', ' ')}" }
                ?: "Nothing pinned — the tile opens the app.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (pinned != null) MaterialTheme.colorScheme.onSurface else PaperFaint,
        )
        Spacer(Modifier.height(20.dp))

        val chosen = pickDevice
        if (chosen != null) {
            // Key step: only keys this remote actually resolves, so a pin can
            // never name a code the DB does not have.
            SectionHead("${chosen.name} · pick a key")
            val keys = keyList(chosen)
            if (keys.isEmpty()) {
                Text(
                    "This remote resolves no keys, so it cannot be pinned.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PaperFaint,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                items(keys) { key ->
                    val isPinned = pinned?.device == chosen.key &&
                        pinned?.lookupKey == key.lowercase()
                    Row(
                        Modifier.fillMaxWidth().pressable {
                            val t = TileTarget(chosen.key, key)
                            store.pin(t)
                            pinned = t
                            pickDevice = null
                            toast.show("Tile fires ${key.replace('_', ' ')} on ${chosen.name}")
                        }.padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            key.replace('_', ' '),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isPinned) Accent else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    SheetDivider()
                }
            }
            Row(
                Modifier.fillMaxWidth().pressable { pickDevice = null }.padding(vertical = 16.dp),
            ) {
                Text("Back to devices", style = MaterialTheme.typography.titleSmall, color = PaperFaint)
            }
            return@Column
        }

        val available = remember(devices, index) {
            index?.let {
                TileTargetSelection.available(
                    devices.map { d -> TileDevice(d.key, d.name, d.roomSlug) },
                    it,
                ).map { t -> t.key }
            } ?: emptyList()
        }
        val shown = remember(devices, available) { devices.filter { it.key in available } }
        // Issue #81: favourites first, in home-row order, then the devices.
        // Same list the home row renders, so the two can never drift.
        val favorites = remember { FavoritesScenesStore(ctx).loadFavorites() }
        if (favorites.isEmpty() && shown.isEmpty()) {
            Text(
                "No saved remote can be pinned yet. Add a device first.",
                style = MaterialTheme.typography.bodyMedium,
                color = PaperFaint,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            if (favorites.isNotEmpty()) {
                item(key = "head:fav") { SectionHead("Favorites") }
            }
            items(favorites, key = { "f:${it.device.prefKey()}:${it.button}" }) { fav ->
                Row(
                    Modifier.fillMaxWidth().pressable {
                        val t = TileTarget(fav.device, fav.button)
                        store.pin(t)
                        pinned = t
                        toast.show("Tile fires ${fav.display}")
                    }.padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        fav.display,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (pinned?.device == fav.device && pinned?.lookupKey == fav.button.lowercase())
                            Accent else MaterialTheme.colorScheme.onSurface,
                    )
                }
                SheetDivider()
            }
            if (shown.isNotEmpty()) {
                item(key = "head:dev") { SectionHead("Remotes") }
            }
            items(shown, key = { "d:${it.remoteId}" }) { dev ->
                Row(
                    Modifier.fillMaxWidth().pressable { pickDevice = dev }.padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(dev.name, style = MaterialTheme.typography.titleMedium)
                        Text(dev.brand, style = MaterialTheme.typography.labelSmall, color = PaperFaint)
                    }
                    if (pinned?.device == dev.key) {
                        Text(
                            pinned?.key?.replace('_', ' ').orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Accent,
                        )
                    }
                }
                SheetDivider()
            }
        }
    }
}

/** Hairline separator — private helpers elsewhere are not shared. */
@Composable
private fun SheetDivider() {
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    )
}
