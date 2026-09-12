package com.erfanbagheri.controlix

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.feature.PowerOffSweep
import com.erfanbagheri.controlix.ui.RemoteBrowser
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ControlixApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlixApp() {
    val context = LocalContext.current
    val ir = remember { IrTransmitter(context) }
    var status by remember { mutableStateOf(ir.describe()) }
    var browsing by remember { mutableStateOf(false) }

    // Open the bundled code DB once; if assets/controlix.db is missing the
    // browse button is disabled instead of crashing the app.
    val repo = remember {
        runCatching { IrCodeRepository(context) }
            .onFailure { it.printStackTrace() }
            .getOrNull()
    }

    val scope = rememberCoroutineScope()
    val sweep = remember(repo) { repo?.let { PowerOffSweep(it, ir) } }
    val sweepProgress by sweep?.progress?.collectAsState() ?: remember { mutableStateOf(null) }
    var sweepJob by remember { mutableStateOf<Job?>(null) }
    val sweeping = sweepJob != null

    if (browsing && repo != null) {
        RemoteBrowser(repo = repo, transmitter = ir, onExit = { browsing = false })
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Controlix") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("IR hardware", style = MaterialTheme.typography.titleMedium)
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(
                onClick = { browsing = true },
                enabled = repo != null
            ) {
                Text("Browse devices (${repo?.buttonCount() ?: 0} codes)")
            }
            if (sweep != null) {
                Button(
                    onClick = {
                        if (sweeping) {
                            sweep.cancel()
                        } else {
                            sweepJob = scope.launch {
                                sweep.run()
                                sweepJob = null
                            }
                        }
                    },
                    enabled = ir.hasIrEmitter()
                ) {
                    Text(if (sweeping) "Stop power-off sweep" else "Power-off sweep (all TVs)")
                }
                sweepProgress?.let { p ->
                    if (p.total > 0) {
                        LinearProgressIndicator(
                            progress = { p.sent.toFloat() / p.total.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Sending ${p.sent} / ${p.total} — ${p.currentBrand}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    val ok = ir.selfTest()
                    status = ir.describe() + if (ok) "\nLast self-test: burst sent" else "\nLast self-test: failed or no IR"
                    Toast.makeText(context, if (ok) "IR burst sent" else "No IR hardware", Toast.LENGTH_SHORT).show()
                },
                enabled = ir.hasIrEmitter()
            ) {
                Text("IR self-test")
            }
            if (repo == null) {
                Text(
                    "Code database not bundled in this build.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
