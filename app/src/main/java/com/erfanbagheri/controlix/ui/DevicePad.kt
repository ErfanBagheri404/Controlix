package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.R
import com.erfanbagheri.controlix.data.ButtonNames
import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.data.FontScale
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.data.SendLog
import com.erfanbagheri.controlix.data.SentEntry
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.tr
import com.erfanbagheri.controlix.ui.theme.Accent
import com.erfanbagheri.controlix.ui.theme.PaperFaint

/**
 * Pad for every device family that is neither TV-like nor AC.
 *
 * The TV pad (d-pad + VOL/CH + keypad) and the AC pad (mode/temperature
 * state machine) are both wrong for a fan, a soundbar or an LED strip:
 * those remotes have no channel, no d-pad, and no (mode,temp) grid. This
 * pad renders rows straight from the codes the remote actually carries, so
 * one layout serves all of them honestly.
 *
 * Data-driven: [sectionsFor] turns (family, buttons) into labelled rows and
 * [DevicePadScreen] renders whatever comes back. Adding a family is a slug
 * in [familyFor] plus a row order — not a new screen.
 *
 * Honest rules (same as AcPad):
 *  - a key that does not exist is never shown as sendable
 *  - every section is omitted when the remote carries no key for it
 *  - codes are never borrowed from sibling remotes — a sibling's state
 *    machine is not this remote's
 *
 * ponytail: family is inferred from the category slug alone; no per-brand
 * metadata. Upgrade path: a `family` column on remote/brand once the DB
 * carries it.
 */
enum class PadFamily {
    /** Fans, heaters, fireplaces: speed + swing + timer + light. */
    CLIMATE,

    /** Purifiers, humidifiers: mode + fan + mist/humidity + timer + light. */
    AIR_TREATMENT,

    /** Soundbars, speakers, receivers, CD/DVD/laserdisc, head units. */
    AUDIO,

    /** Projectors, monitors, displays: d-pad + menu + source + tools. */
    DISPLAY,

    /** LED strips and lighting: colours + effects + brightness. */
    LIGHTING,

    /** Vacuums, dust collectors, window cleaners: cleaning + suction + dock. */
    CLEANING,

    /** Set-top boxes, cable, streaming, consoles, KVM, everything TV-shaped. */
    TV,
    ;

    companion object {
        fun familyFor(categorySlug: String): PadFamily = when {
            categorySlug.startsWith("acs") -> CLIMATE
            categorySlug.startsWith("fans") ||
                categorySlug.startsWith("heaters") ||
                categorySlug.startsWith("fireplaces") -> CLIMATE
            categorySlug.startsWith("air_purifiers") ||
                categorySlug.startsWith("humidifiers") -> AIR_TREATMENT
            categorySlug.startsWith("soundbars") ||
                categorySlug.startsWith("speakers") ||
                categorySlug.startsWith("audio_and_video_receivers") ||
                categorySlug.startsWith("cd_players") ||
                categorySlug.startsWith("minidisc") ||
                categorySlug.startsWith("laserdisc") ||
                categorySlug.startsWith("head_units") -> AUDIO
            categorySlug.startsWith("projectors") ||
                categorySlug.startsWith("monitors") ||
                categorySlug.startsWith("touchscreen_displays") ||
                categorySlug.startsWith("digital_signs") ||
                categorySlug.startsWith("whiteboards") -> DISPLAY
            categorySlug.startsWith("led_lighting") ||
                categorySlug.startsWith("clocks") || categorySlug.startsWith("blinds") -> LIGHTING
            categorySlug.startsWith("vacuum_cleaners") ||
                categorySlug.startsWith("dust_collectors") ||
                categorySlug.startsWith("window_cleaners") -> CLEANING
            else -> TV
        }
    }
}

/** How a row draws. Stepper is the only one that pairs two keys. */
enum class PadRowStyle { STEPPER, CHIPS, D_PAD, KEYPAD }

/**
 * How big a chip row's keys draw.
 *
 * Mi Remote and every other universal remote give the primary action a
 * bigger target than the secondary ones — a fan's Speed row reads as a
 * control, not as a list of links. Text-only chips gave all 47 keys of a
 * projector the same visual weight, which is why the pad looked like a
 * table of contents rather than a remote.
 */
enum class PadKeySize {
    /** Half-width tiles — the pad's main actions (speed, mode, colours). */
    LARGE,
    /** Narrow pills — supporting keys (timers, effects, navigation). */
    SMALL,
    /** Square, one per grid cell — numeric pads and colour swatches. */
    SQUARE,
}

/** One labelled row: the remote's own keys, in the order the pad shows them. */
data class PadRow(
    val labelRes: Int,
    val style: PadRowStyle,
    /** Left key of a STEPPER, or null. */
    val downKey: EffectiveButtons.Resolved? = null,
    /** Right key of a STEPPER, or null. */
    val upKey: EffectiveButtons.Resolved? = null,
    /** Read-out between a stepper's two keys, if any. */
    val readout: String? = null,
    /** Row contents for CHIPS / D_PAD / KEYPAD. */
    val keys: List<EffectiveButtons.Resolved> = emptyList(),
    /** How the keys draw. Only CHIPS reads it. */
    val keySize: PadKeySize = PadKeySize.SMALL,
    /**
     * Overrides the section heading with the remote's own word.
     *
     * A compound mode row is labelled by the mode half it was grouped under
     * ("Heat", "Vent") rather than the generic "Mode" string — the label the
     * DB carries is more specific than anything we can localize, and one
     * generic heading over four groups is how the fan pad ended up with 22
     * identical-looking chips.
     */
    val labelOverride: String? = null,
    /**
     * Text drawn on each chip instead of the raw DB label.
     *
     * Parallel to [keys]. A grouped mode row shows only the duration ("5m")
     * because the heading already says "Heat"; repeating it on every chip
     * doubles the row's width for no information.
     */
    val chipLabels: List<String>? = null,
)

/**
 * Builds the pad's rows for one remote.
 *
 * Every predicate comes from [ButtonNames] and is matched against the
 * remote's own labels only. Order matters: the first predicate that claims
 * a label wins, so broad ones (power, timer) are tested before narrow ones.
 */
fun sectionsFor(
    family: PadFamily,
    available: List<EffectiveButtons.Resolved>,
): List<PadRow> {
    if (available.isEmpty()) return emptyList()
    val claimed = HashSet<String>()
    val rows = mutableListOf<PadRow>()

    // Claim by predicate; a label can only land in one row.
    fun claim(pred: (String) -> Boolean): List<EffectiveButtons.Resolved> {
        val hits = available.filter { pred(it.name) && claimed.add(it.name) }
        return hits.distinctBy { it.name }
    }

    // Power first — every family leads with it. A bare "Off" is an off key,
    // never the on key (same rule the AC pad uses).
    val powerKeys = claim { ButtonNames.power(it) && !ButtonNames.powerOff(it) }
    val offKeys = claim { ButtonNames.powerOff(it) }
    if (powerKeys.isNotEmpty() || offKeys.isNotEmpty()) {
        // A stepper only when the remote really has both directions; a lone
        // Off key is a plain key, not half a control.
        if (powerKeys.isNotEmpty() && offKeys.isNotEmpty()) {
            rows += PadRow(
                labelRes = R.string.ac_power,
                style = PadRowStyle.STEPPER,
                downKey = offKeys.first(),
                upKey = powerKeys.first(),
            )
        } else {
            rows += PadRow(
                labelRes = R.string.ac_power,
                style = PadRowStyle.CHIPS,
                keys = (powerKeys + offKeys).take(24),
            )
        }
    }

    // Temperature: a real +/- pair only when both keys exist.
    val tUp = claim { ButtonNames.tempUp(it) }
    val tDown = claim { ButtonNames.tempDown(it) }
    if (tUp.isNotEmpty() && tDown.isNotEmpty()) {
        rows += PadRow(
            labelRes = R.string.appliance_temp,
            style = PadRowStyle.STEPPER,
            downKey = tDown.first(),
            upKey = tUp.first(),
        )
    }

    // Mode keys: group compound labels by their mode head ("Heat", "Vent", "Dry",
    // "HeatDry") so a fan with 22 mode+duration keys reads as four short rows
    // instead of one 22-chip wall. A bare mode word (or a non-compound label)
    // keeps the generic "Mode" heading and the raw label, as before.
    val modeKeys = claim { ButtonNames.modeKey(it) }
    if (modeKeys.isNotEmpty()) {
        val groups = linkedMapOf<String, MutableList<EffectiveButtons.Resolved>>()
        for (k in modeKeys) {
            val head = ButtonNames.modeHead(k.name)?.replaceFirstChar { it.uppercase() }
            if (head == null) {
                groups.getOrPut("Mode") { mutableListOf() } += k
            } else {
                groups.getOrPut(head) { mutableListOf() } += k
            }
        }
        for ((head, ks) in groups) {
            val isGrouped = head != "Mode" && ks.all { ButtonNames.compoundTail(it.name) != null }
            rows += PadRow(
                labelRes = R.string.appliance_mode,
                style = PadRowStyle.CHIPS,
                keys = ks.take(24),
                keySize = PadKeySize.LARGE,
                labelOverride = head,
                chipLabels = if (isGrouped) ks.map { ButtonNames.modeTail(it.name) ?: it.name } else null,
            )
        }
    }

    val speedKeys = claim {
        ButtonNames.speedLevel(it) || ButtonNames.speedUp(it) || ButtonNames.speedDown(it)
    }
    if (speedKeys.isNotEmpty()) rows += PadRow(R.string.appliance_speed, PadRowStyle.CHIPS, keys = speedKeys.take(24), keySize = PadKeySize.LARGE)

    val oscKeys = claim { ButtonNames.oscillate(it) }
    if (oscKeys.isNotEmpty()) rows += PadRow(R.string.appliance_oscillate, PadRowStyle.CHIPS, keys = oscKeys.take(24), keySize = PadKeySize.LARGE)

    val timerKeys = claim { ButtonNames.timerKey(it) }
    if (timerKeys.isNotEmpty()) rows += PadRow(R.string.appliance_timer, PadRowStyle.CHIPS, keys = timerKeys.take(24), keySize = PadKeySize.LARGE)

    val lightKeys = claim { ButtonNames.lightKey(it) }
    if (lightKeys.isNotEmpty()) rows += PadRow(R.string.appliance_light, PadRowStyle.CHIPS, keys = lightKeys.take(24), keySize = PadKeySize.LARGE)

    // Family-specific middle sections.
    when (family) {
        PadFamily.AIR_TREATMENT -> {
            val mist = claim { ButtonNames.humidityKey(it) }
            if (mist.isNotEmpty()) rows += PadRow(R.string.appliance_humidity, PadRowStyle.CHIPS, keys = mist.take(24), keySize = PadKeySize.LARGE)
        }

        PadFamily.AUDIO -> {
            val vol = claim { ButtonNames.volUp(it) }
            val volDn = claim { ButtonNames.volDown(it) }
            val mute = claim { ButtonNames.mute(it) }
            if (vol.isNotEmpty() && volDn.isNotEmpty()) {
                rows += PadRow(R.string.appliance_volume, PadRowStyle.STEPPER, downKey = volDn.first(), upKey = vol.first())
            }
            if (mute.isNotEmpty()) rows += PadRow(R.string.appliance_more, PadRowStyle.CHIPS, keys = mute.take(24))
            val src = claim { ButtonNames.audioSourceKey(it) }
                        if (src.isNotEmpty()) rows += PadRow(R.string.appliance_input, PadRowStyle.CHIPS, keys = src.take(24), keySize = PadKeySize.LARGE)
            val sound = claim { ButtonNames.audioModeKey(it) }
            if (sound.isNotEmpty()) rows += PadRow(R.string.appliance_sound_modes, PadRowStyle.CHIPS, keys = sound.take(24), keySize = PadKeySize.LARGE)
            val tone = claim { ButtonNames.bassTrebleKey(it) }
            if (tone.isNotEmpty()) rows += PadRow(R.string.appliance_more, PadRowStyle.CHIPS, keys = tone.take(24))
            val play = claim { ButtonNames.transportKey(it) }
            if (play.isNotEmpty()) rows += PadRow(R.string.appliance_playback, PadRowStyle.CHIPS, keys = play.take(24), keySize = PadKeySize.LARGE)
        }

        PadFamily.DISPLAY -> {
            val dpad = listOfNotNull(
                claim { ButtonNames.up(it) }.firstOrNull(),
                claim { ButtonNames.left(it) }.firstOrNull(),
                claim { ButtonNames.ok(it) }.firstOrNull(),
                claim { ButtonNames.right(it) }.firstOrNull(),
                claim { ButtonNames.down(it) }.firstOrNull(),
            )
            if (dpad.size >= 3) rows += PadRow(R.string.appliance_navigation, PadRowStyle.D_PAD, keys = dpad)
            val nav = claim {
                ButtonNames.home(it) || ButtonNames.menu(it) || ButtonNames.back(it) || ButtonNames.guide(it)
            }
            if (nav.isNotEmpty()) rows += PadRow(R.string.appliance_navigation, PadRowStyle.CHIPS, keys = nav.take(24))
            val src = claim { ButtonNames.source(it) }
                        if (src.isNotEmpty()) rows += PadRow(R.string.appliance_input, PadRowStyle.CHIPS, keys = src.take(24), keySize = PadKeySize.LARGE)
            val vol = claim { ButtonNames.volUp(it) }
            val volDn = claim { ButtonNames.volDown(it) }
            val mute = claim { ButtonNames.mute(it) }
            if (vol.isNotEmpty() && volDn.isNotEmpty()) {
                rows += PadRow(R.string.appliance_volume, PadRowStyle.STEPPER, downKey = volDn.first(), upKey = vol.first())
            }
            if (mute.isNotEmpty()) rows += PadRow(R.string.appliance_more, PadRowStyle.CHIPS, keys = mute.take(24))
            val tools = claim { ButtonNames.projectorKey(it) }
            if (tools.isNotEmpty()) rows += PadRow(R.string.appliance_more, PadRowStyle.CHIPS, keys = tools.take(24))
        }

        PadFamily.LIGHTING -> {
            val colors = claim { ButtonNames.colorKey(it) }
            if (colors.isNotEmpty()) rows += PadRow(R.string.appliance_colors, PadRowStyle.CHIPS, keys = colors.take(24), keySize = PadKeySize.LARGE)
            val bright = claim { ButtonNames.brightnessKey(it) }
            if (bright.isNotEmpty()) rows += PadRow(R.string.appliance_brightness, PadRowStyle.CHIPS, keys = bright.take(24), keySize = PadKeySize.LARGE)
            val fx = claim { ButtonNames.effectKey(it) }
            if (fx.isNotEmpty()) rows += PadRow(R.string.appliance_effects, PadRowStyle.CHIPS, keys = fx.take(24), keySize = PadKeySize.LARGE)
        }

        PadFamily.CLEANING -> {
            val start = claim { ButtonNames.startStopKey(it) }
            if (start.isNotEmpty()) rows += PadRow(R.string.ac_power, PadRowStyle.CHIPS, keys = start.take(24), keySize = PadKeySize.LARGE)
            val clean = claim { ButtonNames.cleaningKey(it) }
            if (clean.isNotEmpty()) rows += PadRow(R.string.appliance_cleaning, PadRowStyle.CHIPS, keys = clean.take(24), keySize = PadKeySize.LARGE)
            val dock = claim { ButtonNames.dockKey(it) }
            if (dock.isNotEmpty()) rows += PadRow(R.string.appliance_more, PadRowStyle.CHIPS, keys = dock.take(24))
        }

        PadFamily.CLIMATE -> Unit
        PadFamily.TV -> Unit
    }

    // Keypad, then leftovers. A remote's odd keys must stay reachable — but
    // they arrive as several separate `claim` results that all used to render
    // as their own "More keys" row, so a projector showed the same heading
    // three times. Collect them into one row so the heading appears once.
    // A keypad only when the remote really is a numeric pad. Colour keys on a
    // light strip are named B1..B4/G1..G4, which `digit()` also accepts — that
    // is not a keypad, so require most of 0-9 to be present. Otherwise the
    // digit-ish labels stay unclaimed and fall through to the leftovers row.
    val numeric = available.mapNotNull { it.name.lastOrNull { c -> c.isDigit() } }.toSet()
    if (numeric.size >= 8) {
        val digits = claim { ButtonNames.digit(it) }
        rows += PadRow(R.string.appliance_keypad, PadRowStyle.KEYPAD, keys = digits.take(12))
    }
    val rest = claim { true }
    if (rest.isNotEmpty()) rows += PadRow(R.string.appliance_more, PadRowStyle.CHIPS, keys = rest.take(24))

    return rows
}

/**
 * Renders whatever [sectionsFor] produced. No family knowledge here, so a
 * new family costs a row order, not a screen.
 */
@Composable
fun DevicePadScreen(
    deviceName: String?,
    family: PadFamily,
    remoteId: Int,
    repo: IrCodeRepository?,
    transmitter: IrTransmitter,
    toast: ToastState,
    sendLog: SendLogModel,
    onBack: () -> Unit,
) {
    // Own codes only. No sibling borrow: an appliance's keys are per-remote.
    val available = remember(remoteId) {
        runCatching { repo?.buttons(remoteId) }.getOrNull().orEmpty().map {
            EffectiveButtons.Resolved(it.name, it.name, it.carrierHz, it.pattern, it.remoteId, 1, false)
        }
    }
    val rows = remember(available, family) { sectionsFor(family, available) }

    val view = LocalView.current
    var lastSent by remember(remoteId) { mutableStateOf<String?>(null) }

    fun transmit(key: EffectiveButtons.Resolved) {
        val result = transmitter.transmitButtonResult(key.carrierHz, key.pattern)
        sendLog.record(
            if (result is SendResult.Sent) {
                SentEntry(
                    deviceName ?: tr(R.string.pad_fallback), key.name,
                    key.carrierHz, key.pattern, System.currentTimeMillis(),
                )
            } else {
                SendLog.nothingSent(
                    deviceName ?: tr(R.string.pad_fallback), key.name,
                    sendFailureReason(result), System.currentTimeMillis(),
                )
            },
        )
        if (result is SendResult.Sent) {
            Feedback.send(view)
            lastSent = tr(R.string.ac_sent, key.name)
        } else {
            lastSent = tr(R.string.pad_not_sent)
            toast.show(
                if (!transmitter.hasIrEmitter()) tr(R.string.toast_no_ir_blaster)
                else tr(R.string.toast_couldnt_send),
            )
        }
    }

    Column(
        Modifier.fillMaxSize().applyTopInset().applyBottomInset().padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ActionIconView(
                ActionIcon.Back, 26.dp, MaterialTheme.colorScheme.onSurface, autoMirror = true,
                modifier = Modifier.pressable(onBack).padding(9.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                deviceName ?: tr(R.string.appliance_remote_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(44.dp))
        }

        Spacer(Modifier.height(16.dp))

        if (rows.isEmpty()) {
            Text(
                tr(R.string.appliance_no_usable),
                style = MaterialTheme.typography.bodyMedium, color = PaperFaint,
            )
            return@Column
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
        rows.forEach { row ->
            SectionHead(row.labelOverride ?: tr(row.labelRes))
            PadRowDivider()
            when (row.style) {
                PadRowStyle.STEPPER -> {
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 56.dp * FontScale.heightMultiplier(LocalDensity.current.fontScale)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val dn = row.downKey
                        val up = row.upKey
                        Text(
                            "−",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (dn != null) MaterialTheme.colorScheme.onSurface else PaperFaint.copy(alpha = 0.4f),
                            modifier = Modifier
                                .heightIn(min = 56.dp * FontScale.heightMultiplier(LocalDensity.current.fontScale))
                                .width(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                                .pressable(dn != null) { dn?.let(::transmit) }
                                .padding(horizontal = 8.dp),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "  ${row.readout ?: tr(row.labelRes)}  ",
                            style = MaterialTheme.typography.titleMedium,
                            color = Accent,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "+",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (up != null) MaterialTheme.colorScheme.onSurface else PaperFaint.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .heightIn(min = 56.dp * FontScale.heightMultiplier(LocalDensity.current.fontScale))
                                .width(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                                .pressable(up != null) { up?.let(::transmit) }
                                .padding(horizontal = 8.dp),
                        )
                    }
                }

                PadRowStyle.CHIPS -> KeyGrid(row.keys, row.keySize, { transmit(it) }, row.chipLabels)

                PadRowStyle.D_PAD -> DPad(row.keys) { transmit(it) }

                PadRowStyle.KEYPAD -> DigitGrid(row.keys) { transmit(it) }
            }
            PadRowDivider()
        }

        if (lastSent != null) {
            Text(
                lastSent.orEmpty(),
                style = MaterialTheme.typography.labelSmall, color = PaperFaint,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        }
    }
}

/**
 * A row of real keys, laid out like a physical remote's face.
 *
 * Mi Remote's whole readability comes from this: every control is a filled
 * rounded rectangle with a border, sized by importance, and the grid is
 * regular. Our earlier text-only chips gave a 47-key projector and a 4-key
 * fan the same visual grammar — a list of words, not a remote. Keys now
 * carry a surface, a stroke and a minimum touch height, so the eye reads
 * "control" before it reads the label.
 *
 * LARGE = half-width tiles for the pad's primary actions (mode, speed).
 * SMALL = thirds for supporting keys (timers, effects, sources).
 * SQUARE = one per cell, for keypads and colour swatches.
 */
@Composable
private fun KeyGrid(
    keys: List<EffectiveButtons.Resolved>,
    keySize: PadKeySize,
    onTap: (EffectiveButtons.Resolved) -> Unit,
    labels: List<String>? = null,
) {
    val perRow = when (keySize) {
        PadKeySize.LARGE -> 2
        PadKeySize.SMALL -> 3
        PadKeySize.SQUARE -> 3
    }
    val gap = 8.dp
    Column(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        keys.chunked(perRow).forEachIndexed { chunkIndex, chunk ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                chunk.forEach { key ->
                    // Absolute index into `keys` — not indexOf(key), which
                    // would mis-resolve when two remotes share identical codes.
                    val idx = chunkIndex * perRow + chunk.indexOf(key)
                    KeyTile(
                        label = labels?.getOrNull(idx) ?: key.name,
                        keySize = keySize,
                        modifier = Modifier.weight(1f),
                        onClick = { onTap(key) },
                    )
                }
                // Keep a short last row aligned to the grid instead of letting
                // two keys stretch across a three-column row.
                repeat(perRow - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** One physical-looking key: filled surface, hairline stroke, centred label. */
@Composable
private fun KeyTile(
    label: String,
    keySize: PadKeySize,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val h = when (keySize) {
        PadKeySize.LARGE -> 52.dp
        PadKeySize.SMALL -> 44.dp
        PadKeySize.SQUARE -> 56.dp
    }
    Box(
        modifier
            .height(h * FontScale.heightMultiplier(LocalDensity.current.fontScale))
            .clip(RoundedCornerShape(if (keySize == PadKeySize.SQUARE) 16.dp else 14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(if (keySize == PadKeySize.SQUARE) 16.dp else 14.dp))
            .pressable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = if (keySize == PadKeySize.LARGE) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * Cross layout. The five keys arrive as up/left/ok/right/down; each renders
 * in its own place and a missing one is simply absent, never faked.
 *
 * Mi Remote draws its d-pad as a round cluster of four arrow keys around a
 * centre OK, not as text on a line — the shape is what makes the cluster
 * read as one navigation unit rather than four separate links.
 */
@Composable
private fun DPad(keys: List<EffectiveButtons.Resolved>, onTap: (EffectiveButtons.Resolved) -> Unit) {
    val up = keys.getOrNull(0)
    val left = keys.getOrNull(1)
    val ok = keys.getOrNull(2)
    val right = keys.getOrNull(3)
    val down = keys.getOrNull(4)
    @Composable
    fun cell(key: EffectiveButtons.Resolved?, label: String, modifier: Modifier) {
        KeyTile(
            label = if (key != null) label else "·",
            keySize = PadKeySize.SQUARE,
            modifier = modifier,
            onClick = { key?.let(onTap) },
        )
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Column widths so the cross is symmetric: an arm is the same width as
        // the centre row, so the shape reads as a plus, not a ragged grid.
        Row(Modifier.fillMaxWidth(0.62f)) { cell(up, "▲", Modifier.weight(1f)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cell(left, "◀", Modifier.weight(1f))
            cell(ok, "OK", Modifier.weight(1f))
            cell(right, "▶", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(0.62f)) { cell(down, "▼", Modifier.weight(1f)) }
    }
}

/** Keypad: sorted 0-9, three per row, digits on square keys. */
@Composable
private fun DigitGrid(keys: List<EffectiveButtons.Resolved>, onTap: (EffectiveButtons.Resolved) -> Unit) {
    val sorted = keys.sortedBy { it.name.lastOrNull { c -> c.isDigit() } ?: ' ' }
    KeyGrid(sorted, PadKeySize.SQUARE, onTap)
}

@Composable
private fun PadRowDivider() {
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
}