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
import com.erfanbagheri.controlix.ui.theme.InkRaised
import com.erfanbagheri.controlix.ui.theme.PaperFaint
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
    deviceName: String?,
    onRename: (String) -> Unit,
    onBack: () -> Unit,
) {
    val buttons = remember(remoteId) { repo.buttons(remoteId) }
    var lastSent by remember { mutableStateOf<String?>(null) }
    var emitTrigger by remember { mutableStateOf<Any?>(null) }
    var renaming by remember { mutableStateOf(false) }
    val padView = androidx.compose.ui.platform.LocalView.current

    fun fire(b: IrCodeRepository.Button?) {
        if (b != null && transmitter.transmitButton(b.carrierHz, b.pattern)) {
            lastSent = b.name
            emitTrigger = Any()
            Feedback.send(padView)
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
        // Top chrome: back on the left, device name center, last-sent mono right.
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackRow(onBack)
            Spacer(Modifier.weight(1f))
            if (renaming) {
                var text by remember { mutableStateOf(deviceName ?: "") }
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(24) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.width(200.dp),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { if (text.isNotBlank()) onRename(text.trim()); renaming = false },
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        deviceName ?: "Remote",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    lastSent?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                MaterialIcon(
                    androidx.compose.material.icons.Icons.Filled.Edit,
                    20.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .pressable { renaming = true }
                        .padding(4.dp),
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
                            .bgTile(14.dp, MaterialTheme.colorScheme.surfaceVariant)
                            .pressable(onClick = { fire(b) })
                            .padding(horizontal = 10.dp, vertical = 14.dp),
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** The power crown: filled accent circle when powered, dim ink when not. */
@Composable
private fun PowerCrown(
    power: IrCodeRepository.Button?,
    onFire: () -> Unit,
) {
    val has = power != null
    val bg = if (has) MaterialTheme.colorScheme.primary else InkRaised
    val fg = if (has) Color(0xFF003312) else PaperFaint
    Box(
        Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(bg)
            .pressable(enabled = has) { onFire() },
        contentAlignment = Alignment.Center,
    ) {
        ActionIconView(ActionIcon.Power, 32.dp, fg)
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

/** Round D-pad key: filled tonal circle, OK key slightly larger. */
@Composable
private fun RoundKey(icon: ActionIcon, enabled: Boolean, size: androidx.compose.ui.unit.Dp = 50.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) MaterialTheme.colorScheme.surfaceVariant else InkRaised)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ActionIconView(
            icon, size * 0.55f,
            if (enabled) MaterialTheme.colorScheme.onSurface else PaperFaint,
        )
    }
}

/**
 * Rail key (Vol/Ch): filled surface key, icon + short label stacked.
 * Disabled keys dim but stay readable.
 */
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
            .bgTile(16.dp, MaterialTheme.colorScheme.surfaceVariant)
            .clip(RoundedCornerShape(16.dp))
            .pressable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ActionIconView(
            icon, 26.dp,
            if (enabled) MaterialTheme.colorScheme.onSurface else PaperFaint,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else PaperFaint,
        )
    }
}
