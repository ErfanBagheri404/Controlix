package com.erfanbagheri.controlix.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.ButtonNames
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter

/**
 * The pad — a hardware remote flattened onto glass. Layout mirrors the
 * physical body: power crown, vol/ch rails, D-pad diamond, extras grid.
 * Hairline tiles (flat, no elevation) are the pad's button caps.
 */
@Composable
fun PadScreen(
    repo: IrCodeRepository,
    transmitter: IrTransmitter,
    remoteId: Int,
    onBack: () -> Unit,
) {
    val buttons = remember(remoteId) { repo.buttons(remoteId) }
    var lastSent by remember { mutableStateOf<String?>(null) }
    var emitTrigger by remember { mutableStateOf<Any?>(null) }

    fun fire(b: IrCodeRepository.Button?) {
        if (b != null && transmitter.transmitButton(b.carrierHz, b.pattern)) {
            lastSent = b.name
            emitTrigger = Any()
        }
    }

    val power = buttons.firstOrNull { ButtonNames.power(it.name) }
    val volUp = buttons.firstOrNull { ButtonNames.volUp(it.name) }
    val volDn = buttons.firstOrNull { ButtonNames.volDown(it.name) }
    val chUp = buttons.firstOrNull { ButtonNames.chUp(it.name) }
    val chDn = buttons.firstOrNull { ButtonNames.chDown(it.name) }
    val mute = buttons.firstOrNull { ButtonNames.mute(it.name) }
    val up = buttons.firstOrNull { it.name.equals("up", true) || it.name.contains("arrow_up", true) }
    val down = buttons.firstOrNull { it.name.equals("down", true) || it.name.contains("arrow_down", true) }
    val left = buttons.firstOrNull { it.name.equals("left", true) || it.name.contains("arrow_left", true) }
    val right = buttons.firstOrNull { it.name.equals("right", true) || it.name.contains("arrow_right", true) }
    val ok = buttons.firstOrNull { it.name.equals("ok", true) || it.name.equals("select", true) || it.name.contains("enter", true) }

    val used = setOf(power, volUp, volDn, chUp, chDn, mute, up, down, left, right, ok).filterNotNull().toSet()
    val rest = buttons.filterNot { it in used }

    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(40.dp))
        // Top chrome: back on the left, last-sent readout in mono on the right.
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackRow(onBack)
            Spacer(Modifier.weight(1f))
            lastSent?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // Power crown + pulse field.
        Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
            EmitPulse(triggerKey = emitTrigger, modifier = androidx.compose.ui.Modifier.matchParentSize())
            PowerCrown(power) { fire(power) }
        }
        Spacer(Modifier.height(12.dp))

        // Vol | D-pad | Ch.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PadKey("Vol", ActionIcon.VolUp, volUp != null) { fire(volUp) }
                PadKey("Mute", ActionIcon.Mute, mute != null) { fire(mute) }
                PadKey("Vol", ActionIcon.VolDown, volDn != null) { fire(volDn) }
            }
            DPad(up, down, left, right, ok) { fire(it) }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PadKey("Ch", ActionIcon.ChUp, chUp != null) { fire(chUp) }
                // Spacer block — channels often lack a mute counterpart; keep symmetry.
                Spacer(Modifier.height(64.dp))
                PadKey("Ch", ActionIcon.ChDown, chDn != null) { fire(chDn) }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Everything else, in a tile grid — real button names from the DB.
        if (rest.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(rest, key = { it.name }) { b ->
                    Text(
                        b.name.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .hairlineTile(14.dp)
                            .pressable(onClick = { fire(b) })
                            .padding(horizontal = 10.dp, vertical = 14.dp),
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** The power crown: 76dp ember-ringed circle, the pad's hero. */
@Composable
private fun PowerCrown(
    power: IrCodeRepository.Button?,
    onFire: () -> Unit,
) {
    val has = power != null
    Box(
        Modifier
            .size(76.dp)
            .border(
                1.5.dp,
                if (has) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                CircleShape,
            )
            .clip(CircleShape)
            .pressable(enabled = has) { onFire() },
        contentAlignment = Alignment.Center,
    ) {
        ActionIconView(
            ActionIcon.Power,
            32.dp,
            if (has) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun DPad(
    up: IrCodeRepository.Button?, down: IrCodeRepository.Button?,
    left: IrCodeRepository.Button?, right: IrCodeRepository.Button?,
    ok: IrCodeRepository.Button?, fire: (IrCodeRepository.Button?) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundKey(ActionIcon.Up, up != null) { fire(up) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundKey(ActionIcon.Left, left != null) { fire(left) }
            RoundKey(ActionIcon.Ok, ok != null, size = 58.dp) { fire(ok) }
            RoundKey(ActionIcon.Right, right != null) { fire(right) }
        }
        RoundKey(ActionIcon.Down, down != null) { fire(down) }
    }
}

@Composable
private fun RoundKey(icon: ActionIcon, enabled: Boolean, size: androidx.compose.ui.unit.Dp = 50.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .hairlineTile(size / 2)
            .clip(CircleShape)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ActionIconView(
            icon, size * 0.55f,
            if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
    }
}

/** Pad button cap: hairline tile with icon + short label. */
@Composable
private fun PadKey(
    label: String,
    icon: ActionIcon,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .hairlineTile(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .pressable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ActionIconView(
            icon, 26.dp,
            if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
        )
    }
}
