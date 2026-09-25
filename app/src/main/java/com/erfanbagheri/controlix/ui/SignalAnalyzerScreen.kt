package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ir.SignalAnalysis
import com.erfanbagheri.controlix.ir.SignalInspector
import com.erfanbagheri.controlix.ir.patternToJson
import com.erfanbagheri.controlix.ir.selfTestPattern
import com.erfanbagheri.controlix.ui.theme.Accent
import com.erfanbagheri.controlix.ui.theme.PaperFaint
import java.util.Locale

/** One inspected code: the waveform, its stats, and the buttons to act on it. */
data class InspectedSignal(
    val buttonName: String,
    val carrierHz: Int,
    val pattern: IntArray,
    val protocol: String? = null,
) {
    // IntArray in a data class: identity equals by default, which is exactly
    // what a route/state holder wants (same entry = same object).
}

/**
 * Signal analyzer (issue #55). Draws the pattern as a real waveform —
 * emerald marks, dim spaces, time-proportional — plus the numbers a
 * contributed or misbehaving code needs to be diagnosed. Pure Compose
 * [Canvas]; no charting dependency.
 */
@Composable
fun SignalAnalyzerScreen(
    buttonName: String,
    carrierHz: Int,
    pattern: IntArray,
    protocol: String?,
    transmitter: IrTransmitter? = null,
    onBack: () -> Unit,
) {
    val analysis = remember(carrierHz, pattern, protocol) {
        SignalInspector.analyze(carrierHz, pattern, protocol)
    }
    val clipboard: ClipboardManager = LocalClipboardManager.current
    var note by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .applyTopInset()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        BackRow(onBack)
        Spacer(Modifier.height(12.dp))
        Text(
            buttonName.replace('_', ' ').replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${formatHz(analysis.carrierHz)} · ${formatMs(analysis.totalDurationUs)} · ${analysis.pulseCount} marks",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(18.dp))
        ProtocolBadge(analysis)
        analysis.decodedSummary?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionHead("Waveform")
        Spacer(Modifier.height(8.dp))
        Waveform(analysis)

        Spacer(Modifier.height(18.dp))
        SectionHead("Timings")
        Spacer(Modifier.height(4.dp))
        Text(
            "mark ${analysis.markMinUs}–${analysis.markMaxUs} us · " +
                "space ${analysis.spaceMinUs}–${analysis.spaceMaxUs} us",
            style = MaterialTheme.typography.labelSmall,
            color = PaperFaint,
        )
        Spacer(Modifier.height(8.dp))
        DurationRows(analysis.pattern, Modifier.weight(1f).fillMaxWidth())

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolButton("Copy JSON", ActionIcon.Macros, {
                clipboard.setText(AnnotatedString(patternToJson(analysis.pattern)))
                note = "Copied ${analysis.pattern.size} durations"
            }, Modifier.weight(1f))
            if (transmitter != null) {
                ToolButton("Test fire", ActionIcon.Sweep, {
                    note = when (val r = transmitter.transmitButtonResult(analysis.carrierHz, analysis.pattern)) {
                        is SendResult.Sent -> "Sent ${buttonName.replace('_', ' ')}"
                        SendResult.NoHardware -> "No IR emitter on this device"
                        is SendResult.Failed -> "Couldn't send: ${r.reason}"
                    }
                }, Modifier.weight(1f))
            }
        }
        note?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Time-proportional waveform. Width scales with the burst so a 34-mark NEC
 * frame and a 128-mark self-test both stay readable; the strip scrolls
 * horizontally when the natural width exceeds the screen.
 */
@Composable
private fun Waveform(analysis: SignalAnalysis, modifier: Modifier = Modifier) {
    val pattern = analysis.pattern
    if (pattern.isEmpty()) {
        Text(
            "This button has no timing data.",
            style = MaterialTheme.typography.bodyMedium,
            color = PaperFaint,
            modifier = modifier,
        )
        return
    }
    // Cap the scale so a long burst scrolls instead of drawing a hairline.
    val widthPx = (pattern.size * 7).coerceAtLeast(320)
    val markColor = Accent
    val spaceColor = MaterialTheme.colorScheme.outline
    val axisColor = PaperFaint.copy(alpha = 0.35f)
    Box(
        modifier
            .fillMaxWidth()
            .height(96.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .horizontalScroll(rememberScrollState()),
    ) {
        Canvas(Modifier.width(widthPx.dp).height(96.dp)) {
            val total = analysis.totalDurationUs.coerceAtLeast(1L)
            val top = 12f
            val baseline = size.height - 20f
            val usToPx = size.width / total.toFloat()
            var t = 0L
            pattern.forEachIndexed { i, raw ->
                val start = t * usToPx
                t += raw.coerceAtLeast(0).toLong()
                val end = t * usToPx
                if (i % 2 == 0) {
                    drawRect(
                        color = markColor,
                        topLeft = Offset(start, top),
                        size = Size((end - start).coerceAtLeast(1f), baseline - top),
                    )
                } else {
                    drawLine(
                        color = spaceColor,
                        start = Offset(start, baseline),
                        end = Offset(end, baseline),
                        strokeWidth = 3f,
                    )
                }
            }
            // Zero-time axis — spaces are gaps, not silence below the line.
            drawLine(
                color = axisColor,
                start = Offset(0f, baseline + 4f),
                end = Offset(size.width, baseline + 4f),
                strokeWidth = 2f,
            )
        }
    }
}

/** `0 leader, 1 gap, 2 mark, …` — the same array ConsumerIrManager takes. */
@Composable
private fun DurationRows(pattern: IntArray, modifier: Modifier = Modifier) {
    if (pattern.isEmpty()) {
        Text("No durations.", style = MaterialTheme.typography.bodyMedium, color = PaperFaint, modifier = modifier)
        return
    }
    LazyColumn(modifier) {
        items(pattern.size) { i ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (i % 2 == 0) "mark" else "space",
                    Modifier.width(64.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i % 2 == 0) Accent else PaperFaint,
                )
                Text("#$i", Modifier.width(48.dp), style = MaterialTheme.typography.labelSmall, color = PaperFaint)
                Text(
                    "${pattern[i]} us",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ProtocolBadge(analysis: SignalAnalysis) {
    val known = analysis.protocolName != null
    Text(
        analysis.protocolName ?: "Raw",
        style = MaterialTheme.typography.labelLarge,
        color = if (known) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .border(
                1.dp,
                if (known) Accent else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun ToolButton(label: String, icon: ActionIcon, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
            .pressable(onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIconView(icon, 18.dp, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

internal fun formatHz(hz: Int): String = "${group(hz)} Hz"

/** 67563 us -> "67.6 ms" (under a second), else "1.20 s". */
internal fun formatMs(us: Long): String {
    val ms = us.coerceAtLeast(0) / 1000.0
    return if (ms < 1000) String.format(Locale.US, "%.1f ms", ms)
    else String.format(Locale.US, "%.2f s", us / 1_000_000.0)
}

private fun group(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

/** The self-test burst, inspectable without a saved remote (drawer entry). */
fun selfTestSignal(): InspectedSignal =
    InspectedSignal("Self-test burst", 38000, selfTestPattern(), null)

/**
 * What the analyzer screen can be opened on: one saved device's buttons, or
 * the self-test burst. Built from the DB read the pad already does, so this
 * screen adds no new query.
 */
internal fun analyzerTargets(
    repo: IrCodeRepository?,
    devices: List<SavedDevice>,
    remoteId: Int?,
): List<Pair<String, InspectedSignal>> {
    val ids = if (remoteId != null) listOf(remoteId) else devices.map { it.remoteId }.filter { it >= 0 }
    val out = ArrayList<Pair<String, InspectedSignal>>()
    out += "Self-test" to selfTestSignal()
    ids.forEach { rid ->
        val buttons = runCatching { repo?.buttons(rid) }.getOrNull().orEmpty()
        val name = devices.firstOrNull { it.remoteId == rid }?.name
            ?: runCatching { repo?.remoteName(rid) }.getOrNull()
            ?: "Remote $rid"
        buttons.forEach { b ->
            out += "$name · ${b.name.replace('_', ' ')}" to
                InspectedSignal(b.name, b.carrierHz, b.pattern, b.protocol)
        }
    }
    return out
}

/** Pick what to inspect: the self-test burst, or any button of a saved remote. */
@Composable
fun AnalyzerPickerScreen(
    repo: IrCodeRepository?,
    devices: List<SavedDevice>,
    remoteId: Int?,
    onPick: (InspectedSignal) -> Unit,
    onBack: () -> Unit,
) {
    val targets = remember(repo, devices, remoteId) { analyzerTargets(repo, devices, remoteId) }
    Column(
        Modifier
            .fillMaxSize()
            .applyTopInset()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        BackRow(onBack)
        Spacer(Modifier.height(12.dp))
        Text("Signal analyzer", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Pick a button to see its pulse timings, carrier and protocol.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(targets.size) { i ->
                val (label, signal) = targets[i]
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Row(
                    Modifier.fillMaxWidth().pressable { onPick(signal) }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionIconView(ActionIcon.Waveform, 18.dp, MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        label,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
