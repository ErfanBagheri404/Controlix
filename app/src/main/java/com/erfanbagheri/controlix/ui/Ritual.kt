package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.theme.Confirm
import com.erfanbagheri.controlix.ui.theme.Ember
import com.erfanbagheri.controlix.ui.theme.Hairline
import com.erfanbagheri.controlix.ui.theme.Ink
import com.erfanbagheri.controlix.ui.theme.InkRaised
import com.erfanbagheri.controlix.ui.theme.Paper
import com.erfanbagheri.controlix.ui.theme.PaperDim

/**
 * The ritual — the signature screen. One huge power glyph that fires the
 * pulse on every send, one question, two oversized answers, a hairline
 * stepper. Codes cycle automatically; the user answers yes/no.
 *
 * Two passes like the Mi-Remote style:
 * 1. "Did it turn OFF?" — cycle power codes until yes.
 * 2. "Did it turn back ON?" — fires the same winning code once.
 *
 * Answering yes to both saves the device. Volume/channel confirm steps
 * are deferred: two confirmations already rule out false positives for
 * community data (246k power-labelled buttons include AV-knobs labeled
 * "power"; the on/off pair pins one code to one box).
 */
@Composable
fun RitualScreen(
    repo: IrCodeRepository,
    transmitter: IrTransmitter,
    brandId: Int,
    brandName: String,
    categoryName: String,
    onDone: (remoteId: Int, candidateCount: Int) -> Unit,
    onBack: () -> Unit,
) {
    val candidates = remember(brandId) { repo.powerCodes(brandId) }
    var index by remember { mutableIntStateOf(0) }
    var pulseKey by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(Phase.Off) }
    var fired by remember { mutableStateOf(false) }

    if (candidates.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text("Nothing to try", style = MaterialTheme.typography.headlineMedium, color = Paper)
            Spacer(Modifier.height(8.dp))
            Text(
                "No power codes for $brandName in $categoryName. The sweep tool still brute-forces every brand — try that instead.",
                style = MaterialTheme.typography.bodyLarge, color = PaperDim,
            )
            Spacer(Modifier.height(24.dp))
            BackLink(onBack)
        }
        return
    }

    val total = candidates.size
    val code = candidates[index.coerceIn(0, total - 1)]

    // Auto-send this candidate every time it changes.
    LaunchedEffect(index, phase) {
        if (phase == Phase.Off) {
            transmitter.transmitButton(code.carrierHz, code.pattern)
            pulseKey++
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        BackLink(onBack)
        Spacer(Modifier.height(8.dp))

        PulseGlow(pulseKey = pulseKey) {
            PowerGlyph(size = 120.dp, tint = Ember)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            if (phase == Phase.Off) "Did your $categoryName turn off?"
            else "Did it turn back on?",
            style = MaterialTheme.typography.displaySmall, color = Paper,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                phase == Phase.Off && total == 1 -> "$brandName · only one code"
                phase == Phase.Off -> "$brandName · code ${index + 1} of $total"
                else -> "$brandName · same code, sent again"
            },
            style = MaterialTheme.typography.labelMedium, color = PaperDim,
        )

        // Hairline stepper (only meaningful in the off-scan).
        if (phase == Phase.Off && total > 1) {
            Spacer(Modifier.height(16.dp))
            Stepper(index = index, total = total)
        }

        Spacer(modifier = Modifier.weight(1f))

        if (phase == Phase.Off) {
            ConfirmButton("Yes, it turned off") { phase = Phase.On; fired = true }
            Spacer(Modifier.height(12.dp))
            EmberOutlineButton(if (index < total - 1) "No — try another code" else "No — that's all of them") {
                if (index < total - 1) index++ else onBack()
            }
        } else {
            // On-phase: the winning code is re-fired once when entering.
            LaunchedEffect(phase) {
                transmitter.transmitButton(code.carrierHz, code.pattern)
                pulseKey++
            }
            ConfirmButton("Yes, it came back on") { onDone(code.remoteId, total) }
            Spacer(Modifier.height(12.dp))
            EmberOutlineButton("That was the wrong code") { phase = Phase.Off; index = (index + 1).coerceAtMost(total - 1) }
        }
        Spacer(Modifier.height(32.dp))
    }
}

private enum class Phase { Off, On }

@Composable
private fun Stepper(index: Int, total: Int) {
    // A quiet fractional bar: filled portion = codes tried so far.
    val frac = (index + 1).toFloat() / total.toFloat()
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Hairline)
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth(frac).height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Ember.copy(alpha = 0.7f))
        )
    }
}

@Composable
internal fun ConfirmButton(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Confirm.copy(alpha = 0.14f))
            .border(1.5.dp, Confirm, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✓  $label", style = MaterialTheme.typography.labelLarge, color = Confirm)
    }
}

@Composable
internal fun EmberOutlineButton(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, Ember, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Ember)
    }
}

@Composable
private fun BackLink(onBack: () -> Unit) {
    Text("‹ Back", color = Ember, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(8.dp))
}