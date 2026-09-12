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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.feature.PowerOffSweep
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.theme.Confirm
import com.erfanbagheri.controlix.ui.theme.Ember
import com.erfanbagheri.controlix.ui.theme.Hairline
import com.erfanbagheri.controlix.ui.theme.InkRaised
import com.erfanbagheri.controlix.ui.theme.Paper
import com.erfanbagheri.controlix.ui.theme.PaperDim
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Power-off sweep: every TV power code in the DB, one after another.
 * The pulsing rings keep it visual; the brand readout keeps it informative.
 */
@Composable
fun SweepScreen(
    repo: IrCodeRepository,
    transmitter: IrTransmitter,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sweep = remember(repo) { PowerOffSweep(repo, transmitter) }
    val progress by sweep.progress.collectAsState()
    var job by remember { mutableStateOf<Job?>(null) }
    var sentTotal by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("‹ Devices", color = Ember, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { job?.let { sweep.cancel() }; onBack() }.padding(8.dp))
        Spacer(Modifier.height(8.dp))
        Text("Power-off sweep", style = MaterialTheme.typography.displaySmall, color = Paper)
        Spacer(Modifier.height(6.dp))
        Text(
            "Fires every TV power code we know — about seven minutes, offline. If a TV in range shuts off, it was yours to control.",
            style = MaterialTheme.typography.bodyLarge, color = PaperDim,
        )
        Spacer(Modifier.height(24.dp))

        val p = progress
        PulseGlow(pulseKey = sentTotal, size = 200.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (job == null && !finished) "Ready"
                    else if (finished) "Done"
                    else "${p?.sent ?: 0}",
                    style = MaterialTheme.typography.displaySmall, color = Paper,
                )
                Text(
                    if (p != null && job != null) "of ${p.total} · ${p.currentBrand}" else "tap start",
                    style = MaterialTheme.typography.labelMedium, color = PaperDim,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        if (job == null) {
            ConfirmButton(if (finished || sentTotal > 0) "Sweep again" else "Start the sweep") {
                finished = false
                job = scope.launch {
                    sweep.run()
                    sentTotal++
                    finished = true
                    job = null
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(InkRaised)
                    .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                    .clickable {
                        sweep.cancel()
                        finished = true
                        job = null
                    }
                    .padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("Stop", style = MaterialTheme.typography.labelLarge, color = Paper)
            }
        }
        Spacer(Modifier.height(24.dp))
        if (p != null && p.total > 0 && job != null) {
            val frac = p.sent.toFloat() / p.total.toFloat()
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
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Totally rewritten self-test: one button, one readout, one hint.
 * Safe burst, visible via a phone camera (pale violet flicker).
 */
@Composable
fun SelfTestScreen(
    transmitter: IrTransmitter,
    onBack: () -> Unit,
) {
    var result by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("‹ Devices", color = Ember, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(8.dp))
        Spacer(Modifier.height(8.dp))
        Text("IR self-test", style = MaterialTheme.typography.displaySmall, color = Paper)
        Spacer(Modifier.height(6.dp))
        Text(
            "Sends a burst that matches no device — safe. Point any phone camera at the blaster: you should see a violet flicker.",
            style = MaterialTheme.typography.bodyLarge, color = PaperDim,
        )
        Spacer(Modifier.height(24.dp))
        PulseGlow(pulseKey = if (result == null) 0 else 1, size = 200.dp) {
            PowerGlyph(size = 72.dp, tint = Ember)
        }
        Spacer(Modifier.height(24.dp))
        ConfirmButton("Fire the test burst") {
            val ok = transmitter.selfTest()
            result = if (ok) "Burst sent. Saw the flicker?" else "Could not access the blaster"
        }
        Spacer(Modifier.height(12.dp))
        result?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = if (it.startsWith("Burst")) Confirm else PaperDim,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}