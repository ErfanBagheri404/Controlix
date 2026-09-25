package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.AcMode
import com.erfanbagheri.controlix.data.AcOutcome
import com.erfanbagheri.controlix.data.AcSelection
import com.erfanbagheri.controlix.data.ButtonNames
import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.theme.Accent
import com.erfanbagheri.controlix.ui.theme.PaperFaint

/**
 * Flat AC pad. Rows only, hairline separators, no cards: the remote is
 * stateful, so every row is rendered straight from the codes this remote
 * actually carries.
 *
 * Honest rules:
 *  - temperature values come from the remote's own (mode, temp) states
 *  - a value/mode the remote does not carry is visibly disabled and says so
 *  - nothing is ever synthesized: an unresolvable tap transmits nothing
 *
 * ponytail: AC codes are never borrowed from sibling remotes — a sibling's
 * state machine is not this remote's. Upgrade path: per-model calibration.
 */
@Composable
fun AcPadScreen(
    deviceName: String?,
    remoteId: Int,
    repo: IrCodeRepository?,
    transmitter: IrTransmitter,
    toast: ToastState,
    onBack: () -> Unit,
) {
    // Own codes only. No sibling borrow: stateful AC patterns are per-remote.
    val available = remember(remoteId) {
        runCatching { repo?.buttons(remoteId) }.getOrNull().orEmpty().map {
            EffectiveButtons.Resolved(it.name, it.name, it.carrierHz, it.pattern, it.remoteId, 1, false)
        }
    }
    val modes = remember(available) { AcSelection.supportedModes(available) }
    var mode by remember(remoteId, modes) {
        mutableStateOf(AcSelection.presetModes().firstOrNull { it in modes } ?: AcMode.COOL)
    }
    val temps = remember(available, mode) { AcSelection.supportedTemps(available, mode) }
    var target by remember(remoteId, mode, temps) {
        mutableStateOf(temps.firstOrNull() ?: AcSelection.presetTemps().first())
    }
    val fanKeys = remember(available) {
        available.filter { ButtonNames.acFanSpeed(it.name) }
            .distinctBy { it.name }.take(6)
    }
    val swingKey = remember(available) { available.firstOrNull { ButtonNames.acSwing(it.name) } }
    val powerOnKey = remember(available) {
        available.firstOrNull { ButtonNames.power(it.name) && !ButtonNames.powerOff(it.name) }
    }

    val view = LocalView.current
    var lastSent by remember(remoteId) { mutableStateOf<String?>(null) }

    fun transmit(button: EffectiveButtons.Resolved, label: String) {
        if (transmitter.transmitButton(button.carrierHz, button.pattern)) {
            Feedback.send(view)
            lastSent = "Sent: ${button.name}"
        } else {
            lastSent = "Not sent"
            toast.show(
                if (!transmitter.hasIrEmitter()) "This device has no IR blaster."
                else "Couldn't send. Try again.",
            )
        }
    }

    fun send(selection: AcSelection) {
        when (val out = selection.resolve(available)) {
            is AcOutcome.Resolved -> transmit(out.button, selection.describe())
            is AcOutcome.NotSupported -> toast.show(explain(selection, out))
        }
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
            Text(deviceName ?: "AC remote", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(44.dp))
        }

        Spacer(Modifier.height(16.dp))

        if (available.isEmpty()) {
            Text(
                "This remote has no usable AC codes in the database.",
                style = MaterialTheme.typography.bodyMedium, color = PaperFaint,
            )
            return@Column
        }

        // ── Temperature: only values this remote stores for this mode ────
        SectionHead("Target")
        Hairline()
        Row(
            Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            FlatKey("−", enabled = target > (temps.firstOrNull() ?: target)) {
                val idx = temps.indexOf(target)
                val next = temps.getOrNull(idx - 1) ?: return@FlatKey
                target = next
                send(AcSelection(next, mode, true))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "$target°C",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            FlatKey("+", enabled = target < (temps.lastOrNull() ?: target)) {
                val idx = temps.indexOf(target)
                val next = temps.getOrNull(idx + 1) ?: return@FlatKey
                target = next
                send(AcSelection(next, mode, true))
            }
        }
        Hairline()
        if (temps.isEmpty()) {
            Text(
                "No stored temperatures for ${mode.label}. The pad cannot set one — pick another mode.",
                style = MaterialTheme.typography.labelSmall, color = PaperFaint,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        } else {
            TempRow(temps, target) { picked ->
                target = picked
                send(AcSelection(picked, mode, true))
            }
        }

        // ── Mode ─────────────────────────────────────────────────────────
        SectionHead("Mode")
        Hairline()
        ModeRow(AcSelection.presetModes(), modes, mode) { picked ->
            val pickedTemps = AcSelection.supportedTemps(available, picked)
            if (pickedTemps.isNotEmpty()) target = pickedTemps.first()
            mode = picked
            send(AcSelection(target, picked, true))
        }
        Hairline()
        val missingModes = AcSelection.presetModes().filterNot { it in modes }
        if (missingModes.isNotEmpty()) {
            Text(
                "Disabled: ${missingModes.joinToString { it.label }} — this remote carries no such code.",
                style = MaterialTheme.typography.labelSmall, color = PaperFaint,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }

        // ── Power ────────────────────────────────────────────────────────
        SectionHead("Power")
        Hairline()
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            FlatKey("On", enabled = powerOnKey != null) {
                powerOnKey?.let { transmit(it, "power") }
                    ?: toast.show(explainPower(available, on = true))
            }
            Spacer(Modifier.width(24.dp))
            FlatKey("Off", enabled = available.any { ButtonNames.powerOff(it.name) }) {
                send(AcSelection(target, mode, false))
            }
        }
        Hairline()
        Text(
            if (powerOnKey == null) "On is disabled: this remote has no separate power-on key."
            else "Off uses this remote's own off key.",
            style = MaterialTheme.typography.labelSmall, color = PaperFaint,
            modifier = Modifier.padding(vertical = 10.dp),
        )

        // ── Fan speed: one row, the real keys it carries ─────────────────
        if (fanKeys.isNotEmpty()) {
            SectionHead("Fan")
            Hairline()
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                fanKeys.forEach { key ->
                    Text(
                        key.name,
                        style = MaterialTheme.typography.titleSmall, color = Accent,
                        modifier = Modifier.pressable { transmit(key, key.name) },
                    )
                }
            }
            Hairline()
        }

        // ── Swing ────────────────────────────────────────────────────────
        if (swingKey != null) {
            SectionHead("Swing")
            Hairline()
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                FlatKey(swingKey.name) { transmit(swingKey, "swing") }
            }
            Hairline()
        }

        if (lastSent != null) {
            Text(
                lastSent.orEmpty(), style = MaterialTheme.typography.labelSmall, color = PaperFaint,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** "This remote has no 24°C Cool code. It carries: Cool_23, Heat_30." */
private fun explain(selection: AcSelection, out: AcOutcome.NotSupported): String {
    val have = out.buttonsItDoesHave.take(4).joinToString(", ")
    return "This remote has no ${selection.describe()} code." +
        if (have.isEmpty()) " Nothing usable to send." else " It carries: $have."
}

private fun explainPower(available: List<EffectiveButtons.Resolved>, on: Boolean): String {
    val have = available.map { it.name }.distinct().take(4).joinToString(", ")
    val what = if (on) "power-on" else "power-off"
    return "This remote has no $what key. It carries: $have."
}

@Composable
private fun TempRow(temps: List<Int>, target: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        temps.forEach { t ->
            Text(
                "$t",
                style = MaterialTheme.typography.bodyMedium,
                color = if (t == target) Accent else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.pressable { onPick(t) },
            )
        }
    }
}

@Composable
private fun ModeRow(
    presets: List<AcMode>,
    supported: Set<AcMode>,
    selected: AcMode,
    onPick: (AcMode) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        presets.forEach { m ->
            val on = m in supported
            Text(
                m.label,
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    !on -> PaperFaint
                    m == selected -> Accent
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.pressable(on) { onPick(m) },
            )
        }
    }
}

/** Flat row key: no background, no stock control, dims when disabled. */
@Composable
private fun FlatKey(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = if (enabled) MaterialTheme.colorScheme.onSurface else PaperFaint.copy(alpha = 0.5f),
        modifier = Modifier.pressable(enabled) { onClick() },
    )
}

@Composable
private fun Hairline() {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline,
    )
}
