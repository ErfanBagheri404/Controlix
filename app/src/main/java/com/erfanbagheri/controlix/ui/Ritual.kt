package com.erfanbagheri.controlix.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.tr
import com.erfanbagheri.controlix.R

/**
 * Brand setup — candidates are user-selectable in any order. Power codes keep
 * the existing lock rule: follow-up keys use the accepted power candidate's
 * own DB buttons; a failed follow-up falls back to the power list.
 */
enum class TestKind(val title: String, val question: String, val actionLabel: String, val icon: ActionIcon) {
    Power("Power", "Did it turn off?", "Press power", ActionIcon.Power),
    VolUp("Volume up", "Did the volume go up?", "Press volume up", ActionIcon.VolUp),
    VolDown("Volume down", "And back down?", "Press volume down", ActionIcon.VolDown),
    Mute("Mute", "Did it mute?", "Press mute", ActionIcon.Mute);

    /** Localized copy; the enum literals above are the English contract. */
    fun display(kind: TestKind): Triple<String, String, String> = when (kind) {
        TestKind.Power -> Triple(
            tr(R.string.widget_key_power), tr(R.string.ritual_q_power), tr(R.string.ritual_action_power),
        )
        TestKind.VolUp -> Triple(
            tr(R.string.widget_key_volume_up), tr(R.string.ritual_q_up), tr(R.string.ritual_action_vol_up),
        )
        TestKind.VolDown -> Triple(
            tr(R.string.widget_key_volume_down), tr(R.string.ritual_q_down), tr(R.string.ritual_action_vol_down),
        )
        TestKind.Mute -> Triple(
            tr(R.string.widget_key_mute), tr(R.string.ritual_q_mute), tr(R.string.ritual_action_mute),
        )
    }
}

private val setupSessionSaver = listSaver<SetupTestSession, String>(
    save = { it.toSaveable() },
    restore = { SetupTestSession.fromSaveable(it) },
)

/** Locked remote's follow-up buttons — plain fn so answer handlers resolve
 *  them without waiting for recomposition. */
private fun resolveFollowUps(
    repo: IrCodeRepository,
    remoteId: Int,
    brandId: Int,
): List<Pair<TestKind, EffectiveButtons.Resolved>> {
    val own = runCatching { repo.buttons(remoteId) }.getOrNull().orEmpty()
    val sameModel = runCatching { repo.sameModelButtons(remoteId) }.getOrNull().orEmpty()
    val brand = runCatching { repo.brandButtons(brandId) }.getOrNull().orEmpty()
    val effective = EffectiveButtons.resolve(remoteId, own, sameModel + brand)
    return listOf(
        TestKind.VolUp to effective.firstOrNull { it.key == "volume_up" },
        TestKind.VolDown to effective.firstOrNull { it.key == "volume_down" },
        TestKind.Mute to effective.firstOrNull { it.key == "mute" },
    ).mapNotNull { (kind, button) -> if (button != null) kind to button else null }
}

@Composable
fun RitualScreen(
    repo: IrCodeRepository,
    transmitter: IrTransmitter,
    brandId: Int,
    brandName: String,
    categoryName: String,
    categorySlug: String,
    onDone: (remoteId: Int, candidateCount: Int) -> Unit,
    onBack: () -> Unit,
    onMissingCode: (buttonName: String) -> Unit,
) {
    val candidates = remember(brandId) { repo.powerCodes(brandId) }
    if (candidates.isEmpty()) {
        EmptyRitual(
            brandName, categoryName, onBack,
            onMissingCode = { onMissingCode(TestKind.Power.title) },
        )
        return
    }

    // Status list survives rotation / process death via the saveable.
    var session by rememberSaveable(stateSaver = setupSessionSaver) {
        mutableStateOf(SetupTestSession(candidates.size))
    }
    var lockedRemoteId by rememberSaveable { mutableStateOf(-1) }
    var emitTrigger by remember { mutableStateOf<Any?>(null) }
    val pressMessage =
        if (transmitter.hasIrEmitter()) tr(R.string.setup_press)
        else tr(R.string.setup_no_ir)
    var transmission by remember { mutableStateOf(SetupTransmission(false, pressMessage)) }

    val followUpTests = remember(lockedRemoteId, brandId) {
        if (lockedRemoteId < 0) emptyList() else resolveFollowUps(repo, lockedRemoteId, brandId)
    }
    val followUpKeys = followUpTests.map { it.first.name }
    val powerKeys = remember(candidates) { candidates.indices.map { "power#$it" } }
    // The row under test. Power candidates walk the list; follow-ups walk the locked set.
    val selectedKey = session.selected

    fun send(key: String) {
        val result = when {
            key.startsWith("power#") -> {
                val code = candidates.getOrNull(key.substringAfter('#').toIntOrNull() ?: -1) ?: return
                transmitter.transmitButtonResult(code.carrierHz, code.pattern)
            }
            else -> {
                val button = followUpTests.firstOrNull { it.first.name == key }?.second ?: return
                transmitter.transmitButtonResult(button.carrierHz, button.pattern)
            }
        }
        transmission = SetupTransmission.afterSend(result)
        emitTrigger = if (result is SendResult.Sent) Any() else null
    }

    /** Fire a row immediately — the whole screen is one press-test loop. */
    fun fire(key: String) {
        session = session.fire(key)
        send(key)
    }

    /** Auto-advance: fire the next untried power code, or bail to the brand picker. */
    fun advancePower() {
        val next = session.nextUntested(powerKeys)
        if (next != null) fire(next) else onBack()
    }

    fun answer(accepted: Boolean) {
        val key = session.selected ?: return
        if (accepted) {
            if (!transmission.canConfirm) return
            session = session.accept()
            if (key.startsWith("power#")) {
                val index = key.substringAfter('#').toIntOrNull() ?: return
                val id = candidates[index].remoteId
                lockedRemoteId = id
                val follow = resolveFollowUps(repo, id, brandId)
                if (follow.isEmpty()) {
                    onDone(id, candidates.size)
                    return
                }
                val names = follow.map { it.first.name }
                fire(session.nextUntested(names) ?: names.first())
                return
            }
            val id = lockedRemoteId
            if (id >= 0 && followUpKeys.all { session.statusFor(it) == SetupTestStatus.Accepted }) {
                onDone(id, candidates.size)
            } else {
                session.nextUntested(followUpKeys)?.let { fire(it) }
            }
            return
        }
        // No — mark tried and try the next one without asking again.
        session = session.reject()
        if (key.startsWith("power#")) {
            advancePower()
        } else {
            // A failed follow-up falls back to the power list.
            lockedRemoteId = -1
            session = session.unlock()
            advancePower()
        }
    }

    // Fire the first power code once: the user watches the TV instead of
    // picking a code from a list they can't judge. The flag survives process
    // death so a config change doesn't replay the ritual from code 1.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            fire("power#0")
        }
    }

    val currentTest = when {
        selectedKey == null || selectedKey.startsWith("power#") -> TestKind.Power
        else -> followUpTests.firstOrNull { it.first.name == selectedKey }?.first ?: TestKind.Power
    }
    val (testTitle, _, testAction) = currentTest.display(currentTest)
    // Which attempt is on screen: 1-based power index, or position in the
    // follow-up walk — shown as "3/10" between the two answers.
    val currentPowerIndex = selectedKey?.takeIf { it.startsWith("power#") }
        ?.substringAfter('#')?.toIntOrNull()
    val currentTotal = if (selectedKey?.startsWith("power#") == true || selectedKey == null) {
        candidates.size
    } else followUpKeys.size.coerceAtLeast(1)
    val currentPosition = currentPowerIndex?.plus(1)
        ?: followUpKeys.indexOf(selectedKey).plus(1).coerceAtLeast(1)
    val attemptProgress = tr(R.string.ritual_progress, currentPosition, currentTotal)
    val exhaustNote = selectedKey == null && session.acceptedPower == null &&
        session.statusFor("power#0") != SetupTestStatus.Untested

    Column(Modifier.fillMaxSize().applyTopInset().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(14.dp))
        BackRow(onBack)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                brandName,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            SetupTransmission.display(transmission),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        // The code under test. Tried codes are gone, not listed — the
        // user only ever judges the code in front of them.
        if (!exhaustNote) {
            Text(
                testTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            RowHairline()
        }
        Text(
            tr(R.string.ritual_missing_button),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.pressable { onMissingCode(currentTest.title) }.padding(vertical = 8.dp),
        )

        // Press button centered in the space between the header and the answers.
        Box(
            Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            BigPressButton(testAction, currentTest.icon) {
                selectedKey?.let { send(it) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
                AnswerChip(ActionIcon.Cross, tr(R.string.ritual_no), feedback = Feedback::deny) { answer(false) }
                Text(
                    attemptProgress,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                AnswerChip(
                    ActionIcon.Check,
                    tr(R.string.ritual_yes),
                    enabled = transmission.canConfirm,
                    feedback = Feedback::confirm,
                ) { answer(true) }
        }
    }
}

@Composable
private fun RowHairline() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun EmptyRitual(
    brandName: String,
    categoryName: String,
    onBack: () -> Unit,
    onMissingCode: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(tr(R.string.ritual_nothing), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            tr(R.string.ritual_no_power, brandName, categoryName),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            tr(R.string.ritual_missing_code),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.pressable(onMissingCode).padding(vertical = 12.dp),
        )
        Spacer(Modifier.height(8.dp))
        BackRow(onBack)
    }
}

@Composable
private fun AnswerChip(
    icon: ActionIcon,
    label: String,
    enabled: Boolean = true,
    feedback: (android.view.View?) -> Unit = Feedback::tap,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .then(if (enabled) Modifier.pressable(feedback = feedback, onClick = onClick) else Modifier)
            .semantics { if (!enabled) disabled() }
            .alpha(if (enabled) 1f else 0.38f)
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
        ActionIconView(ActionIcon.Back, 20.dp, MaterialTheme.colorScheme.primary, autoMirror = true)
        Spacer(Modifier.width(8.dp))
        Text(tr(R.string.ritual_back), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
