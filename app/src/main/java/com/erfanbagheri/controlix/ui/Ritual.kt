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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter

/**
 * Brand setup — candidates are user-selectable in any order. Power codes keep
 * the existing lock rule: follow-up keys use the accepted power candidate's
 * own DB buttons; a failed follow-up falls back to the power list.
 */
enum class TestKind(val title: String, val question: String, val actionLabel: String, val icon: ActionIcon) {
    Power("Power", "Did it turn off?", "Press power", ActionIcon.Power),
    VolUp("Volume up", "Did the volume go up?", "Press volume up", ActionIcon.VolUp),
    VolDown("Volume down", "And back down?", "Press volume down", ActionIcon.VolDown),
    Mute("Mute", "Did it mute?", "Press mute", ActionIcon.Mute),
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
            onMissingCode = { onMissingCode("Power") },
        )
        return
    }

    // Status list survives rotation / process death via the saveable.
    var session by rememberSaveable(stateSaver = setupSessionSaver) {
        mutableStateOf(SetupTestSession(candidates.size).fire("power#0"))
    }
    var lockedRemoteId by rememberSaveable { mutableStateOf(-1) }
    var emitTrigger by remember { mutableStateOf<Any?>(null) }
    val pressMessage =
        if (transmitter.hasIrEmitter()) "Press the button below to send the command."
        else "This device has no IR blaster. Setup cannot test this remote."
    var transmission by remember { mutableStateOf(SetupTransmission(false, pressMessage)) }

    val followUpTests = remember(lockedRemoteId, brandId) {
        if (lockedRemoteId < 0) emptyList() else resolveFollowUps(repo, lockedRemoteId, brandId)
    }
    val powerKeys = remember(candidates) { candidates.indices.map { "power#$it" } }
    val followUpKeys = followUpTests.map { it.first.name }
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

    /** Tap: select the row and fire it immediately. */
    fun fire(key: String) {
        session = session.fire(key)
        send(key)
    }

    /** Selection change without a send — disarm Yes until the user fires. */
    fun select(key: String) {
        session = session.fire(key)
        transmission = SetupTransmission(false, pressMessage)
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
                select(session.nextUntested(names) ?: names.first())
                return
            }
            val id = lockedRemoteId
            if (id >= 0 && followUpKeys.all { session.statusFor(it) == SetupTestStatus.Accepted }) {
                onDone(id, candidates.size)
            } else {
                session.nextUntested(followUpKeys)?.let { select(it) }
            }
            return
        }
        // No — mark tried, then advance exactly like the old linear ladder.
        session = session.reject()
        if (key.startsWith("power#")) {
            val next = session.nextUntested(powerKeys)
            when {
                next != null -> select(next)
                session.acceptedPower != null && followUpKeys.isNotEmpty() -> {
                    val f = session.nextUntested(followUpKeys)
                    if (f != null) select(f)
                }
                session.acceptedPower == null -> onBack()
            }
        } else {
            // A failed follow-up falls back to the power list (same as today).
            lockedRemoteId = -1
            session = session.unlock()
            val next = session.nextUntested(powerKeys)
            if (next != null) select(next) else onBack()
        }
    }

    fun skipToVolume() {
        if (session.acceptedPower == null || followUpKeys.isEmpty()) return
        select(session.nextUntested(followUpKeys) ?: followUpKeys.first())
    }

    val currentTest = when {
        selectedKey == null || selectedKey.startsWith("power#") -> TestKind.Power
        else -> followUpTests.firstOrNull { it.first.name == selectedKey }?.first ?: TestKind.Power
    }
    val canSkip = session.acceptedPower != null && followUpKeys.isNotEmpty() &&
        (selectedKey == null || selectedKey.startsWith("power#"))
    val testedCount = powerKeys.count { session.statusFor(it) != SetupTestStatus.Untested } +
        followUpKeys.count { session.statusFor(it) != SetupTestStatus.Untested }
    val totalCount = powerKeys.size + followUpKeys.size

    Column(Modifier.fillMaxSize().applyTopInset().navigationBarsPadding()) {
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
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
                Spacer(Modifier.width(12.dp))
                Text(
                    "$testedCount / $totalCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                transmission.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(powerKeys, key = { it }) { key ->
                    val index = key.substringAfter('#').toInt()
                    CandidateRow(
                        title = "Power code ${index + 1}",
                        detail = candidates[index].buttonName,
                        status = session.statusFor(key),
                        selected = key == selectedKey,
                        onClick = { fire(key) },
                    )
                    RowHairline()
                }
                if (followUpKeys.isNotEmpty()) {
                    item(key = "followups-head") {
                        Spacer(Modifier.height(16.dp))
                        Text("Follow-up tests", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Codes from the accepted power remote.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    items(followUpKeys, key = { it }) { key ->
                        val pair = followUpTests.first { it.first.name == key }
                        CandidateRow(
                            title = pair.first.title,
                            detail = pair.second.name,
                            status = session.statusFor(key),
                            selected = key == selectedKey,
                            onClick = { fire(key) },
                        )
                        RowHairline()
                    }
                }
                item(key = "report") {
                    Spacer(Modifier.height(12.dp))
                    // Flat link, not a chip: the ritual stays the funnel, this is the escape hatch.
                    Text(
                        "Missing this button? Report it",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.pressable { onMissingCode(currentTest.title) }.padding(vertical = 8.dp),
                    )
                }
                item(key = "tail") { Spacer(Modifier.height(16.dp)) }
            }
        }

        // Bottom block: skip shortcut, the real press, the two answers.
        Column(Modifier.padding(horizontal = 24.dp)) {
            if (canSkip) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Row(
                        Modifier.pressable(::skipToVolume).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "Skip to volume test",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        ActionIconView(ActionIcon.ChevronRight, 16.dp, MaterialTheme.colorScheme.primary)
                    }
                }
                RowHairline()
                Spacer(Modifier.height(12.dp))
            }
            BigPressButton(currentTest.actionLabel, currentTest.icon) {
                selectedKey?.let { send(it) }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth().padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnswerChip(ActionIcon.Cross, "No", feedback = Feedback::deny) { answer(false) }
                AnswerChip(
                    ActionIcon.Check,
                    "Yes",
                    enabled = transmission.canConfirm,
                    feedback = Feedback::confirm,
                ) { answer(true) }
            }
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
        Text("Nothing to try", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "No power codes for $brandName in $categoryName. The power-off sweep still brute-forces every brand — try that instead.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Missing a code? Report it",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.pressable(onMissingCode).padding(vertical = 12.dp),
        )
        Spacer(Modifier.height(8.dp))
        BackRow(onBack)
    }
}

@Composable
private fun CandidateRow(
    title: String,
    detail: String,
    status: SetupTestStatus,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                else Color.Transparent
            )
            .pressable(onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(12.dp))
        when (status) {
            SetupTestStatus.Untested ->
                Text("—", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SetupTestStatus.Tried ->
                ActionIconView(ActionIcon.Cross, 16.dp, MaterialTheme.colorScheme.onSurfaceVariant)
            SetupTestStatus.Accepted ->
                ActionIconView(ActionIcon.Check, 16.dp, MaterialTheme.colorScheme.primary)
        }
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
        ActionIconView(ActionIcon.Back, 20.dp, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("Back", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
