package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.R
import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.data.FontScale
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.data.SendLog
import com.erfanbagheri.controlix.data.SentEntry
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.tr
import com.erfanbagheri.controlix.ui.theme.Accent
import com.erfanbagheri.controlix.ui.theme.PaperFaint
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/**
 * Renders a [PadRow] list built by [sectionsFor].
 *
 * Composed to read like the TV pad (ui/Pad.kt), which is this app's own
 * reference: same header pill with the emit pulse, same filled rounded key
 * tiles with no stroke, same hairlines between sections, and the same
 * pressable feel. What it does NOT copy is the TV layout — a fan has no
 * d-pad and no channel rocker, so the body is still one row per real control.
 *
 * Key sizes: LARGE = two per line (primary actions), SMALL = three per line
 * (supporting keys), SQUARE = one per cell (keypad, colour swatches).
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
    var lastSent by remember(remoteId) { mutableStateOf<String?>(null) }
    var emitKey by remember(remoteId) { mutableStateOf<Any?>(null) }
    var overflowKeys by remember(remoteId) { mutableStateOf<List<EffectiveButtons.Resolved>?>(null) }
    val view = LocalView.current

    // Own codes only. No sibling borrow: an appliance's keys are per-remote.
    val available = remember(remoteId) {
        runCatching { repo?.buttons(remoteId) }.getOrNull().orEmpty().map {
            EffectiveButtons.Resolved(it.name, it.name, it.carrierHz, it.pattern, it.remoteId, 1, false)
        }
    }
    val rows = remember(available, family) { sectionsFor(family, available) }

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
            emitKey = Any()
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
            Row(
                Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    EmitPulse(emitKey, Modifier.size(20.dp))
                    Canvas(Modifier.size(8.dp)) { drawCircle(Accent) }
                }
                Text(
                    deviceName ?: tr(R.string.appliance_remote_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(44.dp))
        }

        Spacer(Modifier.height(20.dp))

        if (rows.isEmpty()) {
            Text(
                tr(R.string.appliance_no_usable),
                style = MaterialTheme.typography.bodyMedium, color = PaperFaint,
            )
            return@Column
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            rows.forEach { row ->
                val label = row.labelOverride ?: tr(row.labelRes)
                // A stepper carries its own label between the two keys, the
                // way a rocker is silk-screened on a remote. Giving it a
                // heading too would print the same word twice and turn six
                // soundbar rockers into twelve stacked blocks.
                if (row.style == PadRowStyle.STEPPER || row.style == PadRowStyle.OVERFLOW) {
                    // A stepper carries its own label between the two keys, the
                    // way a rocker is silk-screened on a remote. Giving it a
                    // heading too would print the same word twice and turn six
                    // soundbar rockers into twelve stacked blocks.
                    if (row.style == PadRowStyle.STEPPER) {
                        StepperRow(
                            row, label,
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            ::transmit,
                        )
                        PadRowDivider()
                    } else {
                        OverflowTile(row.keys.size) { overflowKeys = row.keys }
                        PadRowDivider()
                    }
                } else {
                    SectionHead(label)
                    PadRowDivider()
                    when (row.style) {
                        PadRowStyle.CHIPS -> KeyGrid(row.keys, row.keySize, ::transmit, row.chipLabels)
                        PadRowStyle.D_PAD -> DPad(row.keys, ::transmit)
                        PadRowStyle.KEYPAD -> DigitGrid(row.keys, ::transmit)
                        // Both handled above, alongside STEPPER: an untitled
                        // rows share the "no heading, just the control" branch.
                        PadRowStyle.STEPPER, PadRowStyle.OVERFLOW -> Unit
                    }
                    PadRowDivider()
                }
            }
            if (lastSent != null) {
                Text(
                    lastSent.orEmpty(),
                    style = MaterialTheme.typography.labelSmall, color = PaperFaint,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // Sheet lives outside the scrolling column: a ModalBottomSheet inside a
    // verticalScroll parent measures against the scrolled content, not the
    // window, and lands wherever the user stopped scrolling.
    overflowKeys?.let { keys ->
        ExtraKeysSheet(
            keys = keys,
            onKey = ::transmit,
            onDismiss = { overflowKeys = null },
        )
    }
}
