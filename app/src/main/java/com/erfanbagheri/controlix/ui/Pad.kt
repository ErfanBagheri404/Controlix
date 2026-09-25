package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.CopiedButton
import com.erfanbagheri.controlix.data.CopiedButtons
import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.data.ManualKeys
import com.erfanbagheri.controlix.data.MediaLayout
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.theme.Accent
import com.erfanbagheri.controlix.ui.theme.Gold
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
    favoritesModel: FavoritesModel,
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
    // Issue #17: manual list open by default — expand → label is then 2 taps.
    var manualOpen by remember(remoteId) { mutableStateOf(true) }
    // Media pad (issue #15): composed only from this remote's own buttons.
    var mediaMode by remember(remoteId) { mutableStateOf(false) }
    var copiedOpen by remember { mutableStateOf(false) }
    var favoritesOpen by remember { mutableStateOf(false) }
    // Local copies pasted onto this remote, from CopiedButtonStore.
    var localCopies by remember(remoteId) { mutableStateOf(copied.local(remoteId)) }
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
    // MCE/RC6 media layout — present/missing decided by ButtonNames only.
    val media = remember(remoteId, buttons) { MediaLayout.compose(buttons.map { it.name }) }
    // Always resolvable so the header menu opens even if the list is stale.
    val saved = devices.firstOrNull { it.remoteId == remoteId }
        ?: SavedDevice(remoteId, deviceName ?: "Remote", "", "", 0)
    // Own buttons only: distinct non-standard labels + "N on pad" counts.
    val summary = remember(remoteId, buttons) { ManualKeys.summarize(buttons) }
    // ponytail: saved.matched exists but no writer sets it false yet (raw
    // remote-id / search picks still default true) — ceiling: until the add
    // flow flags it, "covers zero standard keys" is the only honest
    // not-matched signal (issue #17).
    val manual = summary.extraCount > 0 && (!saved.matched || summary.onPad == 0)

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
        // Exact name first: ManualKeys keeps case-variants ('ASPECT' vs 'Aspect')
        // as distinct codes — case-insensitive lookup could fire the other row's
        // pattern (issue #17).
        val btn = buttons.firstOrNull { it.name == name }
            ?: p ?: buttons.firstOrNull { it.name.equals(name, true) }
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

    fun toggleFavorite(key: String) {
        val wasFavorite = favoritesModel.favorites.any { it.remoteId == remoteId && it.key == key }
        favoritesModel.toggleFavorite(remoteId, key)
        toast.show(
            if (wasFavorite) "${key.replace('_', ' ')} removed from favorites"
            else "${key.replace('_', ' ')} added to favorites"
        )
    }

    Column(
        Modifier.fillMaxSize().applyTopInset().applyBottomInset().padding(horizontal = 24.dp),
    ) {
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
            if (media.hasMediaKeys) {
                Text(
                    if (mediaMode) "PAD" else "MEDIA",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (mediaMode) Accent else PaperFaint,
                    modifier = Modifier.pressable { mediaMode = !mediaMode }.padding(horizontal = 8.dp, vertical = 9.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            ActionIconView(
                ActionIcon.Dots, 26.dp, MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.pressable { sheetOpen = true }.padding(9.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        if (mediaMode) {
            // Media pad replaces both default regions; keyboard mode lives inside it.
            MediaPad(media, ::fire, Modifier.fillMaxWidth().weight(1f))
        } else {
            PadHeader(deviceName ?: "Remote", emitKey,
                onSwitch = { switcherOpen = true },
                onSheet = { sheetOpen = true },
                onBack = onBack)
            Spacer(Modifier.height(20.dp))

            // Landscape gets two panes side by side; portrait stays stacked.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                if (Orientation.useLandscapeLayout(maxWidth.value, maxHeight.value)) {
                    LandscapePad(::fire, ::copyKey, expanded, { expanded = !expanded }, Modifier.fillMaxSize(),
                        manual, summary, manualOpen) { manualOpen = !manualOpen }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        PadBodyStacked(::fire, ::copyKey, expanded, { expanded = !expanded }, Modifier.fillMaxWidth().weight(1f),
                            manual, summary, manualOpen) { manualOpen = !manualOpen }
                        Spacer(Modifier.height(16.dp))
                        PadControlsRow(::fire, ::copyKey, { expanded = !expanded }, Modifier.fillMaxWidth().height(CONTROL_BLOCK))
                    }
                }
            }
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
            onCopiedKeys = if (copied.clipboard == null && localCopies.isEmpty()) null
            else {
                { sheetOpen = false; copiedOpen = true }
            },
            onFavorites = { sheetOpen = false; favoritesOpen = true },
        )
    }

    if (favoritesOpen) {
        FavoriteKeysSheet(
            keys = effective.map { it.key }.distinct().sorted(),
            favoriteKeys = favoritesModel.favorites.filter { it.remoteId == remoteId }.map { it.key }.toSet(),
            onToggle = ::toggleFavorite,
            onDismiss = { favoritesOpen = false },
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

/** Header: back · bordered name pill (device switcher) · overflow (action sheet). */
@Composable
private fun PadHeader(
    name: String,
    emitKey: Any?,
    onSwitch: () -> Unit,
    onSheet: () -> Unit,
    onBack: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ActionIconView(
            ActionIcon.Back, 26.dp, MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.pressable(onBack).padding(9.dp),
        )
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                .pressable(onSwitch)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                EmitPulse(emitKey, Modifier.size(20.dp))
                androidx.compose.foundation.Canvas(Modifier.size(8.dp)) { drawCircle(Accent) }
            }
            Text(name, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.weight(1f))
        ActionIconView(
            ActionIcon.Dots, 26.dp, MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.pressable(onSheet).padding(9.dp),
        )
    }
}

/** Region 1: chevron pad — swapped out for the full key set. */
@Composable
private fun PadBodyStacked(
    fire: (String) -> Unit,
    copy: (String) -> Unit,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    manual: Boolean = false,
    summary: ManualKeys.Summary? = null,
    manualOpen: Boolean = true,
    onToggleManualOpen: () -> Unit = {},
) {
    ContentSwap(expanded, modifier) { open ->
        if (open) {
            if (manual && summary != null) {
                ManualKeyList(summary, fire, copy, Modifier.fillMaxSize(), manualOpen, onToggleManualOpen)
            } else {
                ExpandedKeys(fire, copy, Modifier.fillMaxSize())
            }
        } else {
            ChevronPad(fire, copy, Modifier.fillMaxSize())
        }
    }
}

/**
 * Favorites are added here, not by long-pressing a key: long-press copies the
 * code (issue #10), so both features can't own one gesture. The dots sheet is
 * the only place a key is chosen by name rather than by tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteKeysSheet(
    keys: List<String>,
    favoriteKeys: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("Favorites", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                if (favoriteKeys.isEmpty()) "Tap a key to add it to the home row."
                else "${favoriteKeys.size} on the home row. Tap to remove.",
                style = MaterialTheme.typography.labelSmall,
                color = PaperFaint,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(keys) { key ->
                    val on = key in favoriteKeys
                    Row(
                        Modifier.fillMaxWidth()
                            .combinedPressable(onClick = { onToggle(key) })
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ActionIconView(
                            if (on) ActionIcon.Star else ActionIcon.Add,
                            18.dp,
                            if (on) Gold else PaperFaint,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            key.replace('_', ' ').replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Region 2: VOL / keys / CH. No capsule — keys sit on the screen surface. */
@Composable
private fun PadControlsRow(
    fire: (String) -> Unit,
    copy: (String) -> Unit,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        RockerColumn(ActionIcon.Add, ActionIcon.Minus, "VOL",
            onUp = { fire("volume_up") }, onDown = { fire("volume_down") },
            onLongUp = { copy("volume_up") }, onLongDown = { copy("volume_down") })

        Spacer(Modifier.width(14.dp))

        Column(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PadBtn(ActionIcon.Home, Modifier.weight(1f).fillMaxHeight(), onLongClick = { copy("home") }) { fire("home") }
                PadBtn(ActionIcon.Back, Modifier.weight(1f).fillMaxHeight(), onLongClick = { copy("back") }) { fire("back") }
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PadBtn(ActionIcon.Mute, Modifier.weight(1f).fillMaxHeight(), onLongClick = { copy("mute") }) { fire("mute") }
                PadBtn(ActionIcon.Play, Modifier.weight(1f).fillMaxHeight(), onLongClick = { copy("play_pause") }) { fire("play_pause") }
            }
            PadBtn(ActionIcon.DotsH, Modifier.fillMaxWidth().height(48.dp),
                onLongClick = { onToggleExpand() }) { onToggleExpand() }
        }

        Spacer(Modifier.width(14.dp))

        RockerColumn(ActionIcon.ChevronUp, ActionIcon.ChevronDown, "CH",
            onUp = { fire("channel_up") }, onDown = { fire("channel_down") },
            onLongUp = { copy("channel_up") }, onLongDown = { copy("channel_down") })
    }
}

/** Landscape: d-pad pane left, vol/ch + keys pane right — gamepad grip. */
@Composable
private fun LandscapePad(
    fire: (String) -> Unit,
    copy: (String) -> Unit,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    manual: Boolean = false,
    summary: ManualKeys.Summary? = null,
    manualOpen: Boolean = true,
    onToggleManualOpen: () -> Unit = {},
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PadBodyStacked(fire, copy, expanded, onToggleExpand, Modifier.weight(1f).fillMaxHeight(),
            manual, summary, manualOpen, onToggleManualOpen)
        Spacer(Modifier.width(16.dp))
        PadControlsRow(fire, copy, onToggleExpand, Modifier.weight(1f).height(CONTROL_BLOCK))
    }
}

/** Chevrons only — no keys, no OK. Small side padding so the pad breathes. */
@Composable
private fun ChevronPad(fire: (String) -> Unit, copy: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PadBtn(ActionIcon.ChevronUp, Modifier.size(64.dp), onLongClick = { copy("up") }) { fire("up") }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PadBtn(ActionIcon.ChevronLeft, Modifier.size(64.dp), onLongClick = { copy("left") }) { fire("left") }
            PadBtn(ActionIcon.ChevronRight, Modifier.size(64.dp), onLongClick = { copy("right") }) { fire("right") }
        }
        PadBtn(ActionIcon.ChevronDown, Modifier.size(64.dp), onLongClick = { copy("down") }) { fire("down") }
    }
}

/** The full key set, laid out on the same grid rhythm as the controls section. */
@Composable
private fun ExpandedKeys(fire: (String) -> Unit, copy: (String) -> Unit, modifier: Modifier = Modifier) {
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
                    KeyTile(key, Modifier.weight(1f).fillMaxHeight(), onLongClick = { copy(key) }) { fire(key) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PadBtn(ActionIcon.Power, Modifier.weight(1f).fillMaxHeight(), onLongClick = { copy("power") }) { fire("power") }
            PadBtn(ActionIcon.Menu, Modifier.weight(1f).fillMaxHeight(), onLongClick = { copy("menu") }) { fire("menu") }
            PadBtn(ActionIcon.Info, Modifier.weight(1f).fillMaxHeight(), onLongClick = { copy("info") }) { fire("info") }
        }
    }
}

/**
 * Issue #17, manual mode: flat "N on pad · M more" header toggling the
 * remote's own non-standard labels — each row fires its own pattern through
 * [fire]. Replaces the fixed grid; the expand "…" key stays the only way in.
 */
@Composable
private fun ManualKeyList(
    summary: ManualKeys.Summary,
    fire: (String) -> Unit,
    copy: (String) -> Unit,
    modifier: Modifier = Modifier,
    open: Boolean,
    onToggle: () -> Unit,
) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().pressable(onClick = onToggle).padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${summary.onPad} on pad · ${summary.extraCount} more",
                style = MaterialTheme.typography.titleMedium,
            )
            ActionIconView(
                if (open) ActionIcon.ChevronUp else ActionIcon.ChevronDown,
                18.dp,
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        if (open) {
            LazyColumn(Modifier.weight(1f)) {
                items(summary.extras, key = { it.label }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().combinedPressable(
                            onClick = { fire(entry.label) },
                            onLongClick = { copy(entry.label) },
                        ).padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(entry.label, style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/** Keyboard mode: flat key grid, each tap fires that remote key's real pattern. */
@Composable
private fun KeyboardGrid(
    rows: List<List<String>>,
    fire: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    KeyTile(
                        label = key,
                        modifier = Modifier.weight(1f).height(46.dp),
                    ) { fire(key) }
                }
                // Nav strips can carry 4 keys — never negative-fill.
                repeat((3 - row.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MediaPad(
    layout: MediaLayout.Layout,
    fire: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var keyboard by remember { mutableStateOf(false) }

    Column(modifier) {
        // Mode toggle: flat custom switch (hairline track + sliding dot), not a stock Switch.
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (keyboard) "KEYBOARD" else "MEDIA PAD",
                style = MaterialTheme.typography.labelSmall,
                color = PaperFaint,
            )
            Spacer(Modifier.weight(1f))
            MediaToggle(keyboard) { keyboard = !keyboard }
        }

        Spacer(Modifier.height(10.dp))
        Hairline()
        Spacer(Modifier.height(10.dp))

        if (keyboard) {
            KeyboardGrid(layout.keyboardRows, fire, Modifier.weight(1f))
        } else {
            MediaLayoutBody(layout, fire, Modifier.weight(1f))
        }
    }
}

/** Hairline divider, one physical pixel at any density. */
@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)))
}

/** Flat custom toggle: 34x18 hairline track, 14dp dot; no rounded chrome. */
@Composable
private fun MediaToggle(checked: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .width(38.dp)
            .height(20.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .background(
                if (checked) Accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(10.dp),
            )
            .pressable(onToggle),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 2.dp)
                .size(14.dp)
                .background(if (checked) Accent else MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(7.dp)),
        )
    }
}

/** Transport row, D-pad with OK, digit pad, back/exit rows — present keys only. */
@Composable
private fun MediaLayoutBody(
    layout: MediaLayout.Layout,
    fire: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (layout.section("transport").isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                layout.section("transport").forEach { key ->
                    PadBtn(ActionIcon.Play, Modifier.weight(1f).height(52.dp)) { fire(key) }
                }
            }
        }

        if (layout.section("dpad").isNotEmpty()) {
            val dpad = layout.section("dpad")
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if ("up" in dpad) PadBtn(ActionIcon.ChevronUp, Modifier.width(68.dp).height(44.dp)) { fire("up") }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if ("left" in dpad) PadBtn(ActionIcon.ChevronLeft, Modifier.size(52.dp)) { fire("left") }
                    if ("ok" in dpad) PadBtn(ActionIcon.Ok, Modifier.size(64.dp), accent = true) { fire("ok") }
                    if ("right" in dpad) PadBtn(ActionIcon.ChevronRight, Modifier.size(52.dp)) { fire("right") }
                }
                if ("down" in dpad) PadBtn(ActionIcon.ChevronDown, Modifier.width(68.dp).height(44.dp)) { fire("down") }
            }
        }

        if (layout.digitRows.isNotEmpty()) {
            layout.digitRows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        KeyTile(
                            label = key,
                            modifier = Modifier.weight(1f).height(46.dp),
                        ) { fire(key) }
                    }
                    // Nav strips can carry 4 keys — never negative-fill.
                    repeat((3 - row.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Digit / word key — same tile surface as the icon keys. */
@Composable
private fun KeyTile(
    label: String,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier.bgTile(20.dp).combinedPressable(onClick = onClick, onLongClick = onLongClick),
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
    onLongUp: (() -> Unit)? = null,
    onLongDown: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxHeight().width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        RockerKey(up, onLongUp) { onUp() }
        Text(label, style = MaterialTheme.typography.labelSmall, color = PaperFaint)
        RockerKey(down, onLongDown) { onDown() }
    }
}

/** Rocker key: immediate send, hold-to-repeat, long-press copy when supplied. */
@Composable
private fun RockerKey(icon: ActionIcon, onLongClick: (() -> Unit)? = null, onFire: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .bgTile(20.dp, MaterialTheme.colorScheme.surfaceVariant)
            .rockerPressable(repeatEnabled = Feedback.rockerRepeatOn, onFire = onFire)
            .combinedPressable(onClick = {}, onLongClick = onLongClick),
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
    onLongClick: (() -> Unit)? = null,
    feedback: (android.view.View?) -> Unit = {},
    onClick: () -> Unit,
) {
    Box(
        modifier.bgTile(20.dp, if (accent) Accent else MaterialTheme.colorScheme.surfaceVariant)
            .combinedPressable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        ActionIconView(icon, 24.dp, MaterialTheme.colorScheme.onSurface)
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
