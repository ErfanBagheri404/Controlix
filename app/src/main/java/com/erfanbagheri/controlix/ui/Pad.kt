package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.theme.Accent
import com.erfanbagheri.controlix.ui.theme.PaperFaint

/**
 * Remote pad. Two stacked regions under the header:
 *  1. the chevron pad (directional only), small side padding
 *  2. the controls section — VOL / CH columns flanking a 2x2 key grid and a
 *     full-width "…" key that expands the full key set over region 1.
 * The header name is a bordered pill (opens the device switcher); the "…" in
 * the header opens the same action sheet Home shows on long-press.
 */

/** Height of the 2x2 grid + the dots row: 56 + 10 + 56 + 10 + 48. */
private val CONTROL_BLOCK = 180.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PadScreen(
    deviceName: String?,
    onRename: (String) -> Unit,
    remoteId: Int,
    repo: IrCodeRepository?,
    transmitter: IrTransmitter,
    devices: List<SavedDevice>,
    model: DeviceModel,
    toast: ToastState,
    onSwitchDevice: (SavedDevice) -> Unit,
    onEdit: (SavedDevice) -> Unit,
    onShare: (SavedDevice) -> Unit,
    onBack: () -> Unit,
) {
    var lastSent by remember(remoteId) { mutableStateOf<String?>(null) }
    var emitKey by remember(remoteId) { mutableStateOf<Any?>(null) }
    var switcherOpen by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    var expanded by remember(remoteId) { mutableStateOf(false) }
    val view = LocalView.current

    // Custom remotes carry negative ids: their buttons are recipe
    // references resolved from the DB at fire time, never stored codes.
    val context = LocalContext.current
    val recipe = remember(remoteId) {
        if (remoteId >= 0) null
        else BuilderStore(context).load(remoteId)
            .validate { rid, n -> runCatching { repo?.buttonByName(rid, n) != null }.getOrDefault(false) }
    }
    val buttons = remember(remoteId, recipe) {
        if (recipe == null) runCatching { repo?.buttons(remoteId) }.getOrNull() ?: emptyList()
        else recipe.buttons.mapNotNull { e ->
            runCatching { repo?.buttonByName(e.remoteId, e.sourceName) }.getOrNull()
                ?.copy(remoteId = e.remoteId)
        }
    }
    // Borrowed codes: the brand's siblings often carry channel/volume codes
    // this remote lacks. Used when the remote's own buttons fall short.
    // Custom remotes resolve against their own set only — the recipe is
    // exactly what the user picked; borrowing would override their choices.
    val effective = remember(remoteId, buttons, recipe) {
        if (recipe != null) EffectiveButtons.resolve(remoteId, buttons, buttons)
        else {
            val brand = runCatching { repo?.brandIdOf(remoteId) }.getOrNull()
            val siblings = if (brand == null) null
            else runCatching { repo?.brandButtons(brand) }.getOrNull()
            if (siblings.isNullOrEmpty()) emptyList()
            else EffectiveButtons.resolve(remoteId, buttons, siblings)
        }
    }
    // Always resolvable so the header menu opens even if the list is stale.
    val saved = devices.firstOrNull { it.remoteId == remoteId }
        ?: SavedDevice(remoteId, deviceName ?: "Remote", "", "", 0)

    fun fire(name: String) {
        // One tick per activation at the key's own weight — here in the single
        // choke point every IR key passes through, so the tick lands whether
        // or not this remote carries the code. The pad keys carry a no-op
        // pressable so they can't double-tick.
        Feedback.press(view, name)
        // Effective list first: standard keys (incl. digits/d-pad) may be
        // borrowed from brand siblings. Semantic key == the action name the
        // pad passes for CHECKS entries; raw-name lookup covers the rest.
        val resolved = effective.firstOrNull { it.key == name }
        if (resolved != null) {
            if (transmitter.transmitButton(resolved.carrierHz, resolved.pattern)) {
                lastSent = "Sent: ${resolved.name}"
                emitKey = Any()
            } else {
                lastSent = "Not sent"
                toast.show(if (!transmitter.hasIrEmitter()) "This device has no IR blaster." else "Couldn't send. Try again.")
            }
            return
        }
        val p = when (name) {
            "play_pause" -> buttons.firstOrNull { it.name.contains("play", true) }
            "source" -> buttons.firstOrNull { it.name.contains("input", true) }
                ?: buttons.firstOrNull { it.name.contains("source", true) }
            else -> null
        }
        val btn = p ?: buttons.firstOrNull { it.name.equals(name, true) }
            ?: buttons.firstOrNull { it.name.contains(name, true) }
        if (btn != null) {
            if (transmitter.transmitButton(btn.carrierHz, btn.pattern)) {
                lastSent = "Sent: ${btn.name}"
                emitKey = Any()
            } else {
                lastSent = "Not sent"
                toast.show(if (!transmitter.hasIrEmitter()) "This device has no IR blaster." else "Couldn't send. Try again.")
            }
        } else {
            toast.show("This remote has no ${name.replace('_', ' ')} code.")
        }
    }

    Column(
        Modifier.fillMaxSize().applyTopInset().applyBottomInset().padding(horizontal = 24.dp),
    ) {
        // ── Header: back · bordered name pill · overflow ─────────────────
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ActionIconView(
                ActionIcon.Back, 26.dp, MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.pressable(onBack).padding(9.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                    .pressable { switcherOpen = true }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    EmitPulse(emitKey, Modifier.size(20.dp))
                    androidx.compose.foundation.Canvas(Modifier.size(8.dp)) { drawCircle(Accent) }
                }
                Text(deviceName ?: "Remote", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.weight(1f))
            ActionIconView(
                ActionIcon.Dots, 26.dp, MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.pressable { sheetOpen = true }.padding(9.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Region 1: chevron pad — swapped out for the full key set ─────
        ContentSwap(expanded, Modifier.fillMaxWidth().weight(1f)) { open ->
            if (open) {
                ExpandedKeys(::fire, Modifier.fillMaxSize())
            } else {
                ChevronPad(::fire, Modifier.fillMaxSize())
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Region 2: controls section — VOL / keys / CH ─────────────────
        Row(Modifier.fillMaxWidth().height(CONTROL_BLOCK), verticalAlignment = Alignment.CenterVertically) {
            // No capsule: the keys sit directly on the screen surface.
            RockerColumn(ActionIcon.Add, ActionIcon.Minus, "VOL",
                onUp = { fire("volume_up") }, onDown = { fire("volume_down") })

            Spacer(Modifier.width(14.dp))

            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PadBtn(ActionIcon.Home, Modifier.weight(1f).fillMaxHeight()) { fire("home") }
                    PadBtn(ActionIcon.Back, Modifier.weight(1f).fillMaxHeight()) { fire("back") }
                }
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PadBtn(ActionIcon.Mute, Modifier.weight(1f).fillMaxHeight()) { fire("mute") }
                    PadBtn(ActionIcon.Play, Modifier.weight(1f).fillMaxHeight()) { fire("play_pause") }
                }
                PadBtn(ActionIcon.DotsH, Modifier.fillMaxWidth().height(48.dp),
                    feedback = Feedback::tap) { expanded = !expanded }
            }

            Spacer(Modifier.width(14.dp))

            RockerColumn(ActionIcon.ChevronUp, ActionIcon.ChevronDown, "CH",
                onUp = { fire("channel_up") }, onDown = { fire("channel_down") })
        }

        if (lastSent != null) {
            Text(lastSent.orEmpty(), style = MaterialTheme.typography.labelSmall, color = PaperFaint,
                modifier = Modifier.padding(top = 10.dp))
        } else {
            Spacer(Modifier.height(10.dp))
        }
    }

    if (switcherOpen) {
        RemoteSwitcher(devices, remoteId, onDismiss = { switcherOpen = false }) { dev ->
            switcherOpen = false
            if (dev.remoteId != remoteId) onSwitchDevice(dev)
        }
    }

    if (sheetOpen) {
        DeviceActionSheet(
            dev = saved,
            model = model,
            toast = toast,
            onEdit = { sheetOpen = false; onEdit(it) },
            onShare = { sheetOpen = false; onShare(it) },
            onDismiss = { sheetOpen = false },
        )
    }
}

/** Chevrons only — no keys, no OK. Small side padding so the pad breathes. */
@Composable
private fun ChevronPad(fire: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PadBtn(ActionIcon.ChevronUp, Modifier.size(64.dp)) { fire("up") }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PadBtn(ActionIcon.ChevronLeft, Modifier.size(64.dp)) { fire("left") }
            PadBtn(ActionIcon.ChevronRight, Modifier.size(64.dp)) { fire("right") }
        }
        PadBtn(ActionIcon.ChevronDown, Modifier.size(64.dp)) { fire("down") }
    }
}

/** The full key set, laid out on the same grid rhythm as the controls section. */
@Composable
private fun ExpandedKeys(fire: (String) -> Unit, modifier: Modifier = Modifier) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("exit", "0", "guide"),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    KeyTile(key, Modifier.weight(1f).fillMaxHeight()) { fire(key) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PadBtn(ActionIcon.Power, Modifier.weight(1f).fillMaxHeight()) { fire("power") }
            PadBtn(ActionIcon.Menu, Modifier.weight(1f).fillMaxHeight()) { fire("menu") }
            PadBtn(ActionIcon.Info, Modifier.weight(1f).fillMaxHeight()) { fire("info") }
        }
    }
}

/** Digit / word key — same tile surface as the icon keys. */
@Composable
private fun KeyTile(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.bgTile(20.dp).pressable(feedback = {}, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
    }
}

/** VOL / CH pair. Bare keys stacked around a mono label — no background capsule. */
@Composable
private fun RockerColumn(
    up: ActionIcon,
    down: ActionIcon,
    label: String,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Column(
        Modifier.fillMaxHeight().width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        RockerKey(up) { onUp() }
        Text(label, style = MaterialTheme.typography.labelSmall, color = PaperFaint)
        RockerKey(down) { onDown() }
    }
}

/** Rocker key: immediate send, then hold-to-repeat per issue #11 settings. */
@Composable
private fun RockerKey(icon: ActionIcon, onFire: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .bgTile(20.dp, MaterialTheme.colorScheme.surfaceVariant)
            .rockerPressable(repeatEnabled = Feedback.rockerRepeatOn, onFire = onFire),
        contentAlignment = Alignment.Center,
    ) {
        ActionIconView(icon, 24.dp, MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PadBtn(
    icon: ActionIcon,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    feedback: (android.view.View?) -> Unit = {},
    onClick: () -> Unit,
) {
    Box(
        modifier.bgTile(20.dp, if (accent) Accent else MaterialTheme.colorScheme.surfaceVariant)
            .pressable(feedback = feedback, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ActionIconView(icon, 24.dp, MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteSwitcher(
    devices: List<SavedDevice>,
    remoteId: Int,
    onDismiss: () -> Unit,
    onPick: (SavedDevice) -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                    modifier = Modifier.fillMaxWidth().pressable { onPick(dev) }.padding(vertical = 14.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
