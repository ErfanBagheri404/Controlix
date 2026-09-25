package com.erfanbagheri.controlix.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.feature.HoldConfirm
import com.erfanbagheri.controlix.feature.HoldEvent
import com.erfanbagheri.controlix.feature.HoldState
import com.erfanbagheri.controlix.feature.PowerOffSweep
import com.erfanbagheri.controlix.feature.preflightMessage
import com.erfanbagheri.controlix.ir.IrTransmitter
import kotlinx.coroutines.delay
import java.util.Locale
import com.erfanbagheri.controlix.ui.SendResult

private const val HOLD_MS = 800L

/**
 * TV-B-Gone: hold-to-confirm power-off sweep.
 *
 * Safety contract: nothing transmits before HoldConfirm reaches Armed.
 * Start is posted from the Armed state only (the machine ignores it
 * anywhere else), the sweep coroutine re-checks Running before it runs,
 * and any release/cancel while in flight calls PowerOffSweep.cancel().
 * Pre-flight shows the exact queue size BEFORE the first transmit.
 */
@Composable
fun SweepScreen(
    repo: IrCodeRepository,
    transmitter: IrTransmitter,
    onBack: () -> Unit,
) {
    val sweep = remember(repo) { PowerOffSweep(repo, transmitter) }
    val progress by sweep.progress.collectAsState()
    val gate = remember { HoldConfirm(HOLD_MS) }
    var holdState by remember { mutableStateOf<HoldState>(HoldState.Idle) }
    var runToken by remember { mutableIntStateOf(0) }
    var runInFlight by remember { mutableStateOf(false) }
    var startedAt by remember { mutableStateOf(0L) }
    var pressedAt by remember { mutableStateOf<Long?>(null) }
    val view = LocalView.current

    fun post(e: HoldEvent): HoldState = gate.post(e).also { holdState = it }

    fun startRun() {
        if (post(HoldEvent.Start) !is HoldState.Running) return
        runInFlight = true
        runToken++
    }

    // Hold clock: 30 Hz ticks while the finger is down. Crossing the
    // threshold arms AND starts inside the same tick — no frame exists
    // where Armed sits unhandled.
    LaunchedEffect(pressedAt) {
        val t0 = pressedAt ?: return@LaunchedEffect
        while (true) {
            delay(33)
            val s = post(HoldEvent.Tick(SystemClock.elapsedRealtime() - t0))
            if (s is HoldState.Armed) {
                Feedback.confirm(view)
                startRun()
            }
        }
    }

    // The sweep. Guard + synchronous runInFlight flag mean a release that
    // lands before this coroutine starts still wins the race (no transmit).
    LaunchedEffect(runToken) {
        if (runToken == 0) return@LaunchedEffect
        if (holdState !is HoldState.Running) {
            runInFlight = false
            return@LaunchedEffect
        }
        startedAt = SystemClock.elapsedRealtime()
        try {
            sweep.run()
            post(HoldEvent.Finished)
        } finally {
            runInFlight = false
        }
    }

    val total = remember(sweep) { sweep.totalDevices }
    val skipped = remember(repo) { repo.tvRemotesWithoutPowerCount() }
    val s = holdState
    val p = progress
    val running = s is HoldState.Running

    Column(Modifier.fillMaxSize().applyTopInset().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(14.dp))
        BackRow(onBack)
        Spacer(Modifier.height(8.dp))
        Text("TV-B-Gone", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Fires every TV power code we know. Press and hold to arm; release or Cancel to stop.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        SectionHead("Pre-flight")
        Text(preflightMessage(total), style = MaterialTheme.typography.titleLarge)
        if (skipped > 0) {
            Text(
                "$skipped remotes skipped — no power button",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text(
            statusLine(s, p, running, startedAt),
            style = MaterialTheme.typography.titleLarge,
            color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Live per-device rows — brand + typed SendResult, hairline-separated.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val recent = p?.recent.orEmpty()
            if (recent.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "No transmissions yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            recent.forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        d.brand,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        sendResultLabel(d.result),
                        style = MaterialTheme.typography.labelMedium,
                        color = when (d.result) {
                            SendResult.Sent -> MaterialTheme.colorScheme.primary
                            SendResult.NoHardware -> MaterialTheme.colorScheme.onSurfaceVariant
                            is SendResult.Failed -> MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HoldConfirmRow(
            label = when (s) {
                is HoldState.Holding -> "Keep holding…"
                HoldState.Armed, HoldState.Running -> "Transmitting — release to stop"
                else -> "Hold to power off"
            },
            fraction = when (s) {
                is HoldState.Holding -> s.progress
                HoldState.Armed, HoldState.Running -> 1f
                else -> 0f
            },
            onPress = {
                Feedback.tap(view)
                pressedAt = SystemClock.elapsedRealtime()
                post(HoldEvent.Press)
            },
            onRelease = {
                val wasActive = s is HoldState.Holding || s is HoldState.Armed || running
                pressedAt = null
                if (runInFlight) sweep.cancel()
                post(HoldEvent.Release)
                if (wasActive) Feedback.deny(view)
            },
        )

        if (running) {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth()
                    .pressable {
                        if (runInFlight) sweep.cancel()
                        post(HoldEvent.Cancel)
                        Feedback.deny(view)
                    }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionIconView(ActionIcon.Cross, 20.dp, MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Cancel sweep",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** The hold target: square corners, hairline border, progress fills left→right. */
@Composable
private fun HoldConfirmRow(
    label: String,
    fraction: Float,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onPress()
                    try {
                        tryAwaitRelease()
                    } finally {
                        onRelease()
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionIconView(ActionIcon.Sweep, 26.dp, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun statusLine(
    s: HoldState,
    p: PowerOffSweep.Progress?,
    running: Boolean,
    startedAt: Long,
): String = when {
    running && p != null -> {
        val clock = mmss(SystemClock.elapsedRealtime() - startedAt)
        val head = if (p.currentBrand.isEmpty()) "" else "${p.currentBrand} "
        "$head${p.sent}/${p.total} · $clock"
    }
    s is HoldState.Done -> "Done"
    s is HoldState.Cancelled && p != null -> "Stopped · ${p.sent}/${p.total}"
    s is HoldState.Cancelled -> "Cancelled — nothing sent"
    else -> "Ready"
}

private fun mmss(ms: Long): String {
    val t = ms.coerceAtLeast(0) / 1000
    return String.format(Locale.US, "%02d:%02d", t / 60, t % 60)
}

private fun sendResultLabel(r: SendResult): String = when (r) {
    SendResult.Sent -> "Sent"
    SendResult.NoHardware -> "No IR emitter"
    is SendResult.Failed -> "Failed"
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
