package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.ButtonNames
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter

/**
 * Brand setup — the user presses the REAL button; the only question is
 * "is it working?". Power codes cycle first; once one is confirmed, the
 * locked remote's actual volume/mute buttons are tested the same way.
 * A failed vol/mute answer falls back to the next power candidate.
 *
 * State machine: testStep < 0 is the power phase; >= 0 indexes followUpTests.
 * All sends happen in LaunchedEffect keyed on the state.
 */
enum class TestKind(val title: String, val question: String, val actionLabel: String, val icon: ActionIcon) {
    Power("Power", "Did it turn off?", "Press power", ActionIcon.Power),
    VolUp("Volume up", "Did the volume go up?", "Press volume up", ActionIcon.VolUp),
    VolDown("Volume down", "And back down?", "Press volume down", ActionIcon.VolDown),
    Mute("Mute", "Did it mute?", "Press mute", ActionIcon.Mute),
}

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
    if (candidates.isEmpty()) {
        EmptyRitual(brandName, categoryName, onBack)
        return
    }

    var powerIndex by remember { mutableIntStateOf(0) }
    var lockedRemoteId by remember { mutableStateOf<Int?>(null) }
    var testStep by remember { mutableIntStateOf(-1) }
    var emitTrigger by remember { mutableStateOf<Any?>(null) }

    val followUpTests = remember(lockedRemoteId) {
        val id = lockedRemoteId ?: return@remember emptyList()
        val btns = runCatching { repo.buttons(id) }.getOrNull() ?: return@remember emptyList()
        listOf(
            TestKind.VolUp to btns.firstOrNull { ButtonNames.volUp(it.name) },
            TestKind.VolDown to btns.firstOrNull { ButtonNames.volDown(it.name) },
            TestKind.Mute to btns.firstOrNull { ButtonNames.mute(it.name) },
        ).mapNotNull { (kind, btn) -> if (btn != null) kind to btn else null }
    }

    LaunchedEffect(powerIndex, testStep, lockedRemoteId) {
        if (testStep < 0) {
            val code = candidates.getOrNull(powerIndex) ?: return@LaunchedEffect
            transmitter.transmitButton(code.carrierHz, code.pattern)
            emitTrigger = Any()
        } else {
            val id = lockedRemoteId ?: return@LaunchedEffect
            val pair = followUpTests.getOrNull(testStep)
            if (pair == null) onDone(id, candidates.size)
            else {
                transmitter.transmitButton(pair.second.carrierHz, pair.second.pattern)
                emitTrigger = Any()
            }
        }
    }

    val current: Pair<TestKind, () -> Unit> = if (testStep < 0) {
        val code = candidates[powerIndex.coerceAtMost(candidates.lastIndex)]
        TestKind.Power to { transmitter.transmitButton(code.carrierHz, code.pattern); emitTrigger = Any() }
    } else {
        followUpTests.getOrNull(testStep)
            ?.let { (kind, btn) -> kind to { transmitter.transmitButton(btn.carrierHz, btn.pattern); emitTrigger = Any() } }
            ?: (TestKind.Power to {})
    }
    val (test, fire) = current
    val totalTests = if (testStep < 0) 1 else 1 + followUpTests.size
    val position = if (testStep < 0) 1 else testStep + 2

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(40.dp))
            BackRow(onBack)
            Spacer(Modifier.height(20.dp))

            Text(
                if (testStep < 0) "$brandName · code ${powerIndex + 1} of ${candidates.size}"
                else "$brandName · ${test.title}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(36.dp))

            // The emitter icon + pulse — the signature moment.
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                EmitPulse(
                    triggerKey = emitTrigger,
                    modifier = Modifier.matchParentSize(),
                )
                ActionIconView(test.icon, 80.dp, MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(28.dp))
            Text(test.question, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(12.dp))
            Text(
                "It sends automatically — press the button again if the moment passed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            // Counter in mono — tabular figures keep it from jumping.
            Text(
                "$position / $totalTests",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            )
        }

        // Bottom block: the real button press + answers.
        Column(Modifier.padding(horizontal = 24.dp)) {
            BigPressButton(test.actionLabel, test.icon) { fire() }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth().padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnswerChip(ActionIcon.Cross, "No", feedback = Feedback::deny) {
                    if (testStep < 0) {
                        if (powerIndex < candidates.lastIndex) powerIndex++ else onBack()
                    } else {
                        testStep = -1
                        lockedRemoteId = null
                        if (powerIndex < candidates.lastIndex) powerIndex++ else onBack()
                    }
                }
                AnswerChip(ActionIcon.Check, "Yes", feedback = Feedback::confirm) {
                    if (testStep < 0) {
                        val code = candidates.getOrNull(powerIndex) ?: return@AnswerChip
                        lockedRemoteId = code.remoteId
                        testStep = 0
                    } else {
                        val id = lockedRemoteId ?: return@AnswerChip
                        if (testStep < followUpTests.lastIndex) testStep++
                        else onDone(id, candidates.size)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRitual(brandName: String, categoryName: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Nothing to try", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "No power codes for $brandName in $categoryName. The power-off sweep still brute-forces every brand — try that instead.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        BackRow(onBack)
    }
}

@Composable
private fun AnswerChip(
    icon: ActionIcon,
    label: String,
    feedback: (android.view.View?) -> Unit = Feedback::tap,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .pressable(feedback = feedback, onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ActionIconView(icon, 22.dp, MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** The primary action in the ritual and tool screens. Filled ember outline. */
@Composable
fun BigPressButton(label: String, icon: ActionIcon, onPress: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp))
            .pressable(onPress),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIconView(icon, 30.dp, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** Back chip — same press contract, left-aligned. */
@Composable
fun BackRow(onBack: () -> Unit) {
    Row(Modifier.pressable(onBack).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        ActionIconView(ActionIcon.Back, 20.dp, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("Back", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
