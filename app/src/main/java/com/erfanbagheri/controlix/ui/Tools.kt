package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.feature.PowerOffSweep
import com.erfanbagheri.controlix.ir.IrTransmitter
import kotlinx.coroutines.launch

/**
 * Power-off sweep: every TV power code in the DB, one after another.
 * Counter in mono, brand readout, cancel always available.
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
    var running by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(40.dp))
        BackRow(onBack)
        Spacer(Modifier.height(20.dp))
        Text("Power-off sweep", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Fires every TV power code we know. If a TV in range shuts off, it was yours to control.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))

        val p = progress
        Text(
            when {
                running && p != null -> "${p.sent} / ${p.total}"
                done -> "Done"
                else -> "Ready"
            },
            style = MaterialTheme.typography.displaySmall,
        )
        if (running && p != null) {
            Text(
                p.currentBrand,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))

        if (running) {
            BigPressButton("Stop", ActionIcon.Cross) { sweep.cancel() }
        } else {
            BigPressButton(if (done) "Run again" else "Start sweep", ActionIcon.Sweep) {
                done = false
                running = true
                scope.launch {
                    sweep.run()
                    running = false
                    done = true
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * IR self-test: one burst, one readout. Visible via a phone camera.
 */
@Composable
fun SelfTestScreen(
    transmitter: IrTransmitter,
    onBack: () -> Unit,
) {
    var result by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(40.dp))
        BackRow(onBack)
        Spacer(Modifier.height(20.dp))
        Text("IR self-test", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Sends a burst that matches no device. Point any phone camera at the blaster: you should see a violet flicker.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
        BigPressButton("Fire test burst", ActionIcon.CameraTest) {
            result = if (transmitter.selfTest()) "Burst sent. Saw the flicker?" else "No IR emitter on this device"
        }
        Spacer(Modifier.height(16.dp))
        result?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}
