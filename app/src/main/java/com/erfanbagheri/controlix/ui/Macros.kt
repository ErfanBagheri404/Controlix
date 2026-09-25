package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.feature.Macro
import com.erfanbagheri.controlix.feature.MacroPlayer
import com.erfanbagheri.controlix.feature.MacroStep
import com.erfanbagheri.controlix.ir.IrTransmitter
import kotlinx.coroutines.launch

/**
 * Macro list screen: named button sequences (TV on → AVR input → lights off).
 * Steps are (remoteId, buttonName) so macros survive DB updates.
 * Create flow: pick one of the saved devices, then tick buttons into the sequence.
 */
@Composable
fun MacrosScreen(
    repo: IrCodeRepository,
    transmitter: IrTransmitter,
    devices: List<SavedDevice>,
    model: MacroModel,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val player = remember(repo) { MacroPlayer(repo, transmitter) }
    var creating by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<Int?>(null) }
    var playResult by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().applyTopInset().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(14.dp))
        BackRow(onBack)
        Spacer(Modifier.height(8.dp))
        Text("Macros", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Named sequences of buttons. One tap runs the whole chain.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        if (creating) {
            MacroBuilder(
                devices = devices,
                repo = repo,
                onDone = { name, steps ->
                    model.add(Macro((model.macros.maxOfOrNull { it.id } ?: 0) + 1, name, steps))
                    creating = false
                },
                onCancel = { creating = false },
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(model.macros, key = { it.id }) { macro ->
                    MacroRow(
                        macro = macro,
                        playing = playing == macro.id,
                        onPlay = {
                            if (playing != null) return@MacroRow
                            playing = macro.id
                            playResult = null
                            scope.launch {
                                val sent = player.play(macro)
                                playResult = if (sent == macro.steps.size) "All ${sent} sent" else "$sent of ${macro.steps.size} sent"
                                playing = null
                            }
                        },
                        onDelete = { model.remove(macro.id) },
                    )
                }
            }

            playResult?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            if (devices.isEmpty()) {
                Text(
                    "Add a device first — macros replay buttons from your remotes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                BigPressButton("New macro", ActionIcon.Add) { creating = true }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun MacroRow(
    macro: Macro,
    playing: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(macro.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${macro.steps.size} steps",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (playing) {
            Text(
                "Sending…",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Row(
                Modifier.pressable(onPlay).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionIconView(ActionIcon.Sweep, 20.dp, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("Run", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(4.dp))
            Row(
                Modifier.combinedPressable(onClick = {}, onLongClick = onDelete).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionIconView(ActionIcon.Cross, 18.dp, MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/**
 * Inline builder: name is derived from the chosen steps count unless the user
 * types one; steps are appended in order from any saved device.
 */
@Composable
private fun MacroBuilder(
    devices: List<SavedDevice>,
    repo: IrCodeRepository,
    onDone: (String, List<MacroStep>) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var pickedRemote by remember { mutableStateOf(devices.first().remoteId) }
    var steps by remember { mutableStateOf<List<MacroStep>>(emptyList()) }
    val context = LocalContext.current
    // Custom remotes (negative ids) expose recipe references, not DB rows.
    val recipe = remember(pickedRemote) {
        if (pickedRemote >= 0) null else BuilderStore(context).load(pickedRemote)
    }
    // Each choice pairs the button shown with the step that fires it: for a
    // custom remote that is the original (remoteId, sourceName) reference.
    val choices = remember(pickedRemote, recipe) {
        if (recipe != null) {
            recipe.buttons.mapNotNull { e ->
                runCatching { repo.buttonByName(e.remoteId, e.sourceName) }.getOrNull()
                    ?.let { b -> b.copy(remoteId = e.remoteId) to MacroStep(e.remoteId, e.sourceName) }
            }
        } else {
            runCatching { repo.buttons(pickedRemote) }.getOrElse { emptyList() }
                .map { it to MacroStep(pickedRemote, it.name) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Name your macro, then tick buttons below in order.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        // device picker
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            devices.forEach { dev ->
                val selected = dev.remoteId == pickedRemote
                Row(
                    Modifier
                        .pressable { pickedRemote = dev.remoteId }
                        .border(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        dev.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // step list
        if (steps.isNotEmpty()) {
            Text(
                steps.mapIndexed { i, s -> "${i + 1}. ${s.buttonName}" }.joinToString("  →  "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(Modifier.weight(1f)) {
            items(choices.take(60), key = { "b${it.first.remoteId}/${it.first.name}" }) { choice ->
                val btn = choice.first
                val step = choice.second
                Row(
                    Modifier.fillMaxWidth().pressable { steps = steps + step }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionIconView(ActionIcon.Add, 16.dp, MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(12.dp))
                    Text(btn.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (steps.any { it.remoteId == step.remoteId && it.buttonName == step.buttonName }) {
                        Text(
                            "✓",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BigPressButton(if (name.isBlank()) "Save (${steps.size})" else "Save", ActionIcon.Sweep) {
                if (steps.isEmpty()) return@BigPressButton
                onDone(name.ifBlank { "Macro ${steps.size} steps" }, steps)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.pressable(onCancel).padding(vertical = 8.dp)) {
            Text("Cancel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(28.dp))
    }
}
