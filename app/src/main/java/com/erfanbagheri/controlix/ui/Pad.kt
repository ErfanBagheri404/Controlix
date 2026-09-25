package com.erfanbagheri.controlix.ui

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.CopiedButton
import com.erfanbagheri.controlix.data.CopiedButtons
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
    copied: CopiedButtonModel,
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
    var pendingBorrow by remember(remoteId) { mutableStateOf<PendingBorrow?>(null) }
    var provenanceKey by remember(remoteId) { mutableStateOf<String?>(null) }
    val (borrowMemory, updateBorrowMemory) = rememberBorrowedCodeMemory()
    var copiedOpen by remember { mutableStateOf(false) }
    // Local copies pasted onto this remote, from CopiedButtonStore.
    var localCopies by remember(remoteId) { mutableStateOf(copied.local(remoteId)) }
    val view = LocalView.current

    val buttons = remember(remoteId) {
        runCatching { repo?.buttons(remoteId) }.getOrNull() ?: emptyList()
    }
    // Borrowed codes: the brand's siblings often carry channel/volume codes
    // this remote lacks. Used when the remote's own buttons fall short.
    val siblings = remember(remoteId) {
        val brand = runCatching { repo?.brandIdOf(remoteId) }.getOrNull()
        if (brand == null) emptyList()
        else runCatching { repo?.brandButtons(brand) }.getOrNull().orEmpty()
    }
    val effective = remember(remoteId, buttons, siblings, borrowMemory) {
        if (siblings.isEmpty()) emptyList()
        else EffectiveButtons.resolve(remoteId, buttons, siblings, borrowMemory)
    }
    val borrowedKeys = remember(effective) { effective.filter { it.borrowed }.associateBy { it.key } }

    fun showProvenance(key: String) {
        val borrowed = borrowedKeys[key] ?: return
        val source = runCatching { repo?.remoteName(borrowed.remoteId) }.getOrNull()
        val label = "${key.replace('_', ' ').replaceFirstChar { it.uppercase() }} · " +
            borrowedSourceLabel(borrowed, source)
        toast.show(label)
    }

    fun sendResolved(resolved: EffectiveButtons.Resolved) {
        if (transmitter.transmitButton(resolved.carrierHz, resolved.pattern)) {
            lastSent = "Sent: ${resolved.name}"
            emitKey = Any()
        } else {
            lastSent = "Not sent"
            toast.show(if (!transmitter.hasIrEmitter()) "This device has no IR blaster." else "Couldn't send. Try again.")
        }
    }
    // Always resolvable so the header menu opens even if the list is stale.
    val saved = devices.firstOrNull { it.remoteId == remoteId }
        ?: SavedDevice(remoteId, deviceName ?: "Remote", "", "", 0)

    /**
     * The code a pad key would send. A pasted local copy wins, then the
     * effective (borrowed-or-own) code, then the raw name lookup fire() used.
     * One resolution path for both sending and copying.
     */
    fun codeFor(name: String): CopiedButton? {
        CopiedButtons.localForKey(localCopies, name)?.let { return it }
        val resolved = effective.firstOrNull { it.key == name }
        if (resolved != null) return CopiedButton(resolved.name, resolved.carrierHz, resolved.pattern)
        val p = when (name) {
            "play_pause" -> buttons.firstOrNull { it.name.contains("play", true) }
            "source" -> buttons.firstOrNull { it.name.contains("input", true) }
                ?: buttons.firstOrNull { it.name.contains("source", true) }
            else -> null
        }
        val btn = p ?: buttons.firstOrNull { it.name.equals(name, true) }
            ?: buttons.firstOrNull { it.name.contains(name, true) }
        return btn?.let { CopiedButton(it.name, it.carrierHz, it.pattern) }
    }

    fun fire(name: String) {
        // One tick per activation at the key's own weight — here in the single
        // choke point every IR key passes through, so the tick lands whether
        // or not this remote carries the code. The pad keys carry a no-op
        // pressable so they can't double-tick.
        Feedback.press(view, name)
        val code = codeFor(name)
        if (code == null) {
            toast.show("This remote has no ${name.replace('_', ' ')} code.")
            return
        }
        if (transmitter.transmitButton(code.carrierHz, code.pattern)) {
            lastSent = "Sent: ${code.name}"
            emitKey = Any()
        } else {
            lastSent = "Not sent"
            toast.show(if (!transmitter.hasIrEmitter()) "This device has no IR blaster." else "Couldn't send. Try again.")
        }
    }

    /** Long-press a key: put its code on the clipboard for another remote. */
    fun copyKey(name: String) {
        val code = codeFor(name)
        if (code == null) {
            toast.show("This remote has no ${name.replace('_', ' ')} code to copy.")
            return
        }
        copied.copyToClipboard(code)
        toast.show("Copied ${code.name}. Long-press … on another remote to paste it.")
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
                ExpandedKeys(::fire, borrowedKeys, ::showProvenance, ::copyKey, Modifier.fillMaxSize())
            } else {
                ChevronPad(::fire, borrowedKeys, ::showProvenance, ::copyKey, Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(16.dp))

        // ── Region 2: controls section — VOL / keys / CH ─────────────────
        Row(Modifier.fillMaxWidth().height(CONTROL_BLOCK), verticalAlignment = Alignment.CenterVertically) {
            // No capsule: the keys sit directly on the screen surface.
            RockerColumn(ActionIcon.Add, ActionIcon.Minus, "VOL",
                borrowedKeys["volume_up"] != null, borrowedKeys["volume_down"] != null,
                onUp = { fire("volume_up") }, onDown = { fire("volume_down") },
                onLongUp = { copyKey("volume_up") }, onLongDown = { copyKey("volume_down") },
                upOnProvenance = { showProvenance("volume_up") },
                downOnProvenance = { showProvenance("volume_down") })
            Spacer(Modifier.width(14.dp))

            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PadBtn(ActionIcon.Home, Modifier.weight(1f).fillMaxHeight(), borrowed = borrowedKeys["home"] != null,
                        onLongClick = { copyKey("home") },
                        onProvenance = { showProvenance("home") }) { fire("home") }
                    PadBtn(ActionIcon.Back, Modifier.weight(1f).fillMaxHeight(), borrowed = borrowedKeys["back"] != null,
                        onLongClick = { copyKey("back") },
                        onProvenance = { showProvenance("back") }) { fire("back") }
                }
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PadBtn(ActionIcon.Mute, Modifier.weight(1f).fillMaxHeight(), borrowed = borrowedKeys["mute"] != null,
                        onLongClick = { copyKey("mute") },
                        onProvenance = { showProvenance("mute") }) { fire("mute") }
                    PadBtn(ActionIcon.Play, Modifier.weight(1f).fillMaxHeight(), borrowed = borrowedKeys["play_pause"] != null,
                        onLongClick = { copyKey("play_pause") },
                        onProvenance = { showProvenance("play_pause") }) { fire("play_pause") }
                }
                PadBtn(ActionIcon.DotsH, Modifier.fillMaxWidth().height(48.dp)) { expanded = !expanded }
            }

            Spacer(Modifier.width(14.dp))

            RockerColumn(ActionIcon.ChevronUp, ActionIcon.ChevronDown, "CH",
                borrowedKeys["channel_up"] != null, borrowedKeys["channel_down"] != null,
                onUp = { fire("channel_up") }, onDown = { fire("channel_down") },
                onLongUp = { copyKey("channel_up") }, onLongDown = { copyKey("channel_down") },
                upOnProvenance = { showProvenance("channel_up") },
                downOnProvenance = { showProvenance("channel_down") })
        }

        if (lastSent != null) {
            Text(lastSent.orEmpty(), style = MaterialTheme.typography.labelSmall, color = PaperFaint,
                modifier = Modifier.padding(top = 10.dp))
        } else {
            Spacer(Modifier.height(10.dp))
        }
    }

    pendingBorrow?.let { pending ->
        val remoteName = remember(pending.resolved.remoteId) {
            runCatching { repo?.remoteName(pending.resolved.remoteId) }.getOrNull()
        }
        BorrowedCodeConfirmSheet(
            pending = pending,
            source = borrowedSourceLabel(pending.resolved, remoteName),
            onKeep = {
                updateBorrowMemory(borrowMemory.accept(remoteId, pending.resolved.key, pending.candidate))
                pendingBorrow = null
                sendResolved(pending.resolved)
            },
            onReject = {
                updateBorrowMemory(borrowMemory.reject(remoteId, pending.resolved.key, pending.candidate))
                pendingBorrow = null
                toast.show("Noted. A different code will be preferred next time.")
            },
        )
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
            onCopiedKeys = if (copied.clipboard == null && localCopies.isEmpty()) null
            else {
                { sheetOpen = false; copiedOpen = true }
            },
        )
    }

    if (copiedOpen) {
        CopiedKeysSheet(
            clipboard = copied.clipboard,
            local = localCopies,
            onPaste = { btn ->
                localCopies = copied.paste(remoteId, btn)
                copiedOpen = false
                toast.show("Pasted ${btn.name} onto this remote.")
            },
            onRemove = { name ->
                copied.remove(remoteId, name)
                localCopies = copied.local(remoteId)
            },
            onSend = { btn ->
                if (transmitter.transmitButton(btn.carrierHz, btn.pattern)) {
                    lastSent = "Sent: ${btn.name}"
                    emitKey = Any()
                } else {
                    toast.show(if (!transmitter.hasIrEmitter()) "This device has no IR blaster." else "Couldn't send. Try again.")
                }
            },
            onDismiss = { copiedOpen = false },
        )
    }
}

/** Chevrons only — no keys, no OK. Small side padding so the pad breathes. */
@Composable
private fun ChevronPad(
    fire: (String) -> Unit,
    borrowedKeys: Map<String, EffectiveButtons.Resolved>,
    onProvenance: ((String) -> Unit)? = null,
    copy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PadBtn(ActionIcon.ChevronUp, Modifier.size(64.dp), borrowed = borrowedKeys["up"] != null,
            onLongClick = { copy("up") },
            onProvenance = onProvenance?.let { p -> { p("up") } }) { fire("up") }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PadBtn(ActionIcon.ChevronLeft, Modifier.size(64.dp), borrowed = borrowedKeys["left"] != null,
                onLongClick = { copy("left") },
                onProvenance = onProvenance?.let { p -> { p("left") } }) { fire("left") }
            PadBtn(ActionIcon.ChevronRight, Modifier.size(64.dp), borrowed = borrowedKeys["right"] != null,
                onLongClick = { copy("right") },
                onProvenance = onProvenance?.let { p -> { p("right") } }) { fire("right") }
        }
        PadBtn(ActionIcon.ChevronDown, Modifier.size(64.dp), borrowed = borrowedKeys["down"] != null,
            onLongClick = { copy("down") },
            onProvenance = onProvenance?.let { p -> { p("down") } }) { fire("down") }
    }
}

/** The full key set, laid out on the same grid rhythm as the controls section. */
@Composable
private fun ExpandedKeys(
    fire: (String) -> Unit,
    borrowedKeys: Map<String, EffectiveButtons.Resolved>,
    onProvenance: ((String) -> Unit)? = null,
    copy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    KeyTile(key, Modifier.weight(1f).fillMaxHeight(), borrowedKeys[key] != null,
                        onLongClick = { copy(key) },
                        onProvenance = onProvenance?.let { p -> { p(key) } }) { fire(key) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PadBtn(ActionIcon.Power, Modifier.weight(1f).fillMaxHeight(), borrowed = borrowedKeys["power"] != null,
                onLongClick = { copy("power") },
                onProvenance = onProvenance?.let { p -> { p("power") } }) { fire("power") }
            PadBtn(ActionIcon.Menu, Modifier.weight(1f).fillMaxHeight(), borrowed = borrowedKeys["menu"] != null,
                onLongClick = { copy("menu") },
                onProvenance = onProvenance?.let { p -> { p("menu") } }) { fire("menu") }
            PadBtn(ActionIcon.Info, Modifier.weight(1f).fillMaxHeight(), borrowed = borrowedKeys["info"] != null,
                onLongClick = { copy("info") },
                onProvenance = onProvenance?.let { p -> { p("info") } }) { fire("info") }
        }
    }
}

/** Digit / word key — same tile surface as the icon keys. */
@Composable
private fun KeyTile(
    label: String,
    modifier: Modifier = Modifier,
    borrowed: Boolean = false,
    onProvenance: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(modifier) {
        Box(
            Modifier.fillMaxSize().bgTile(20.dp).combinedPressable(
                onClick = onClick,
                onLongClick = onLongClick ?: onProvenance,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(label.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
        }
        if (borrowed) Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { BorrowedBadge() }
    }
}

/** VOL / CH pair. Bare keys stacked around a mono label — no background capsule. */
@Composable
private fun RockerColumn(
    up: ActionIcon,
    down: ActionIcon,
    label: String,
    upBorrowed: Boolean = false,
    downBorrowed: Boolean = false,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLongUp: (() -> Unit)? = null,
    onLongDown: (() -> Unit)? = null,
    upOnProvenance: (() -> Unit)? = null,
    downOnProvenance: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxHeight().width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        RockerKey(up, borrowed = upBorrowed, onLongClick = onLongUp) { onUp() }
        Text(label, style = MaterialTheme.typography.labelSmall, color = PaperFaint)
        RockerKey(down, borrowed = downBorrowed, onLongClick = onLongDown) { onDown() }
    }
}

/** Rocker key: immediate send, hold-to-repeat, long-press copy when supplied. */
@Composable
private fun RockerKey(
    icon: ActionIcon,
    borrowed: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onFire: () -> Unit,
) {
    Box(Modifier.size(56.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .bgTile(20.dp, MaterialTheme.colorScheme.surfaceVariant)
                .rockerPressable(repeatEnabled = Feedback.rockerRepeatOn, onFire = onFire)
                .combinedPressable(onClick = {}, onLongClick = onLongClick),
            contentAlignment = Alignment.Center,
        ) {
            ActionIconView(icon, 24.dp, MaterialTheme.colorScheme.onSurface)
        }
        if (borrowed) Box(Modifier.align(Alignment.TopEnd)) { BorrowedBadge() }
    }
}

@Composable
private fun PadBtn(
    icon: ActionIcon,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    feedback: (android.view.View?) -> Unit = {},
    borrowed: Boolean = false,
    onProvenance: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .bgTile(20.dp, if (accent) Accent else MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (onLongClick != null || onProvenance != null)
                        Modifier.combinedPressable(onClick = onClick, onLongClick = onLongClick ?: onProvenance)
                    else Modifier.pressable(feedback = feedback, onClick = onClick)
                ),
            contentAlignment = Alignment.Center,
        ) {
            ActionIconView(icon, 24.dp, MaterialTheme.colorScheme.onSurface)
        }
        if (borrowed) Box(Modifier.align(Alignment.TopEnd).padding(5.dp)) { BorrowedBadge() }
    }
}

/**
 * Paste target (issue #10): the clipboard code plus this remote's local
 * copies. Paste adds the clipboard code to the remote's own list; a local
 * copy can be sent or removed. Empty state explains the gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopiedKeysSheet(
    clipboard: CopiedButton?,
    local: List<CopiedButton>,
    onPaste: (CopiedButton) -> Unit,
    onRemove: (String) -> Unit,
    onSend: (CopiedButton) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("Copied keys", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            if (clipboard == null) {
                Text(
                    "Nothing copied yet. Long-press a key on another remote to copy its code here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PaperFaint,
                )
            } else {
                SheetKeyRow(
                    label = "Paste ${clipboard.name}",
                    hint = "${clipboard.carrierHz} Hz · ${clipboard.pattern.size} marks",
                    onClick = { onPaste(clipboard) },
                )
            }
            if (local.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("On this remote", style = MaterialTheme.typography.labelSmall, color = PaperFaint)
                local.forEach { btn ->
                    SheetKeyRow(
                        label = btn.name,
                        hint = "${btn.carrierHz} Hz · tap to send",
                        onClick = { onSend(btn) },
                        onLongClick = { onRemove(btn.name) },
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SheetKeyRow(
    label: String,
    hint: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .combinedPressable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = PaperFaint)
        }
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
