package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.layout.Arrangement
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

    val modeKeys = claim { ButtonNames.modeKey(it) }
    if (modeKeys.isNotEmpty()) rows += PadRow(R.string.appliance_mode, PadRowStyle.CHIPS, keys = modeKeys.take(24))

    val speedKeys = claim {
        ButtonNames.speedLevel(it) || ButtonNames.speedUp(it) || ButtonNames.speedDown(it)
    }
    if (speedKeys.isNotEmpty()) rows += PadRow(R.string.appliance_speed, PadRowStyle.CHIPS, keys = speedKeys.take(24))

    val oscKeys = claim { ButtonNames.oscillate(it) }
    if (oscKeys.isNotEmpty()) rows += PadRow(R.string.appliance_oscillate, PadRowStyle.CHIPS, keys = oscKeys.take(24))

    val timerKeys = claim { ButtonNames.timerKey(it) }
    if (timerKeys.isNotEmpty()) rows += PadRow(R.string.appliance_timer, PadRowStyle.CHIPS, keys = timerKeys.take(24))

    val lightKeys = claim { ButtonNames.lightKey(it) }
    if (lightKeys.isNotEmpty()) rows += PadRow(R.string.appliance_light, PadRowStyle.CHIPS, keys = lightKeys.take(24))

    // Family-specific middle sections.
    when (family) {
        PadFamily.AIR_TREATMENT -> {
            val mist = claim { ButtonNames.humidityKey(it) }
            if (mist.isNotEmpty()) rows += PadRow(R.string.appliance_humidity, PadRowStyle.CHIPS, keys = mist.take(24))
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
            if (src.isNotEmpty()) rows += PadRow(R.string.appliance_input, PadRowStyle.CHIPS, keys = src.take(24))
            val sound = claim { ButtonNames.audioModeKey(it) }
            if (sound.isNotEmpty()) rows += PadRow(R.string.appliance_sound_modes, PadRowStyle.CHIPS, keys = sound.take(24))
            val tone = claim { ButtonNames.bassTrebleKey(it) }
            if (tone.isNotEmpty()) rows += PadRow(R.string.appliance_more, PadRowStyle.CHIPS, keys = tone.take(24))
            val play = claim { ButtonNames.transportKey(it) }
            if (play.isNotEmpty()) rows += PadRow(R.string.appliance_playback, PadRowStyle.CHIPS, keys = play.take(24))
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
            if (nav.isNotEmpty()) rows += PadRow(R.string.appliance_more, PadRowStyle.CHIPS, keys = nav.take(24))
            val src = claim { ButtonNames.source(it) }
            if (src.isNotEmpty()) rows += PadRow(R.string.appliance_input, PadRowStyle.CHIPS, keys = src.take(24))
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
            if (colors.isNotEmpty()) rows += PadRow(R.string.appliance_colors, PadRowStyle.CHIPS, keys = colors.take(24))
            val bright = claim { ButtonNames.brightnessKey(it) }
            if (bright.isNotEmpty()) rows += PadRow(R.string.appliance_brightness, PadRowStyle.CHIPS, keys = bright.take(24))
            val fx = claim { ButtonNames.effectKey(it) }
            if (fx.isNotEmpty()) rows += PadRow(R.string.appliance_effects, PadRowStyle.CHIPS, keys = fx.take(24))
        }

        PadFamily.CLEANING -> {
            val start = claim { ButtonNames.startStopKey(it) }
            if (start.isNotEmpty()) rows += PadRow(R.string.ac_power, PadRowStyle.CHIPS, keys = start.take(24))
            val clean = claim { ButtonNames.cleaningKey(it) }
            if (clean.isNotEmpty()) rows += PadRow(R.string.appliance_cleaning, PadRowStyle.CHIPS, keys = clean.take(24))
            val dock = claim { ButtonNames.dockKey(it) }
            if (dock.isNotEmpty()) rows += PadRow(R.string.appliance_more, PadRowStyle.CHIPS, keys = dock.take(24))
        }

        PadFamily.CLIMATE -> Unit
        PadFamily.TV -> Unit
    }

    // Keypad, then leftovers. A remote's odd keys must stay reachable.
    // A keypad only when the remote really is a numeric pad. Colour keys on a
    // light strip are named B1..B4/G1..G4, which `digit()` also accepts — that
    // is not a keypad, so require most of 0-9 to be present. Otherwise the
    // digit-ish labels stay unclaimed and fall through to the leftovers row.
    val numeric = available.mapNotNull { it.name.lastOrNull { c -> c.isDigit() } }.toSet()
    if (numeric.size >= 8) {
        val digits = claim { ButtonNames.digit(it) }
        rows += PadRow(R.string.appliance_more, PadRowStyle.KEYPAD, keys = digits.take(12))
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
            SectionHead(tr(row.labelRes))
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
                            modifier = Modifier.pressable(dn != null) { dn?.let(::transmit) },
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
                            modifier = Modifier.pressable(up != null) { up?.let(::transmit) },
                        )
                    }
                }

                PadRowStyle.CHIPS -> ChipWrap(row.keys) { transmit(it) }

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

/** Chips wrap so a 12-key colour or source row never scrolls off-screen. */
@Composable
private fun ChipWrap(keys: List<EffectiveButtons.Resolved>, onTap: (EffectiveButtons.Resolved) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        keys.chunked(4).forEach { chunk ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                chunk.forEach { key ->
                    Text(
                        key.name,
                        style = MaterialTheme.typography.titleSmall, color = Accent,
                        modifier = Modifier.pressable { onTap(key) },
                    )
                }
            }
        }
    }
}

/**
 * Cross layout. The five keys arrive as up/left/ok/right/down; each renders
 * in its own place and a missing one is simply absent, never faked.
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
        Text(
            if (key != null) key.name else label,
            style = MaterialTheme.typography.titleSmall,
            color = if (key != null) Accent else PaperFaint.copy(alpha = 0.35f),
            textAlign = TextAlign.Center,
            modifier = modifier.pressable(key != null) { key?.let(onTap) }.padding(vertical = 8.dp),
        )
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            cell(up, "▲", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            cell(left, "◀", Modifier.weight(1f))
            cell(ok, "OK", Modifier.weight(1f))
            cell(right, "▶", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            cell(down, "▼", Modifier.weight(1f))
        }
    }
}

/** Keypad: sorted 0-9, three per row, digits drawn as plain text. */
@Composable
private fun DigitGrid(keys: List<EffectiveButtons.Resolved>, onTap: (EffectiveButtons.Resolved) -> Unit) {
    val sorted = keys.sortedBy { it.name.lastOrNull { c -> c.isDigit() } ?: ' ' }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        sorted.chunked(3).forEach { chunk ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                chunk.forEach { key ->
                    val d = key.name.lastOrNull { c -> c.isDigit() }?.toString() ?: key.name
                    Text(
                        d,
                        style = MaterialTheme.typography.titleMedium, color = Accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .pressable { onTap(key) }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PadRowDivider() {
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
}