package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.BuilderRecipe
import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ui.theme.Accent
import com.erfanbagheri.controlix.ui.theme.PaperFaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Custom remote builder. Search the bundled DB for a source remote, tap
 * its buttons into an ordered recipe, reorder/rename/remove rows, save as
 * a new custom remote (negative id) that shows up on Home and opens as a
 * normal pad. Only functions the fixed pad layout can fire are pickable —
 * every other DB code would be a button no pad key ever sends.
 */
@Composable
fun BuilderScreen(
    repo: IrCodeRepository,
    model: DeviceModel,
    toast: ToastState,
    onSaved: (remoteId: Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { BuilderStore(context) }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var recipe by remember { mutableStateOf(BuilderRecipe.EMPTY) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<IrCodeRepository.Remote, String>>>(emptyList()) }
    var sourceRemote by remember { mutableStateOf<IrCodeRepository.Remote?>(null) }
    var sourceButtons by remember { mutableStateOf<List<IrCodeRepository.Button>>(emptyList()) }
    var editingIndex by remember { mutableStateOf(-1) }

    // Debounced DB search — a keystroke must not query SQLite on the frame.
    LaunchedEffect(query) {
        delay(250)
        results = withContext(Dispatchers.IO) {
            runCatching { repo.searchModels(query) }.getOrNull().orEmpty()
        }
    }

    // Buttons of the picked source remote that map onto a pad key,
    // one candidate per key (duplicates collapse; first in DB order wins).
    fun openSource(remote: IrCodeRepository.Remote) {
        sourceRemote = remote
        sourceButtons = emptyList()
        scope.launch {
            sourceButtons = withContext(Dispatchers.IO) {
                runCatching { repo.buttons(remote.id) }.getOrNull().orEmpty()
                    .mapNotNull { b ->
                        EffectiveButtons.CHECKS.firstOrNull { it.second(b.name) }?.let { (key, _) -> key to b }
                    }
                    .distinctBy { it.first }
                    .map { it.second }
            }
        }
    }

    fun save() {
        val label = name.trim()
        if (label.isEmpty()) { toast.show("Name the remote first."); return }
        if (recipe.buttons.isEmpty()) { toast.show("Pick at least one button."); return }
        // Validation queries SQLite — off the frame, then persist.
        scope.launch {
            val validated = withContext(Dispatchers.IO) {
                recipe.validate { rid, btn ->
                    runCatching { repo.buttonByName(rid, btn) != null }.getOrDefault(false)
                }
            }
            if (validated.buttons.isEmpty()) {
                toast.show("Those buttons are gone from the database.")
                return@launch
            }
            val id = store.nextId()
            store.save(id, validated)
            model.save(SavedDevice(id, label, "Custom", "custom", validated.buttons.size))
            toast.show("$label saved")
            onSaved(id)
        }
    }

    Column(
        Modifier.fillMaxSize().applyTopInset().padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackRow(onBack)
            Spacer(Modifier.weight(1f))
            ActionIconView(
                ActionIcon.Check, 26.dp, Accent,
                modifier = Modifier.pressable(::save).padding(6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Build remote", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("Remote name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "Buttons (${recipe.buttons.size})",
            style = MaterialTheme.typography.labelLarge, color = PaperFaint,
        )
        Spacer(Modifier.height(4.dp))
        if (recipe.buttons.isEmpty()) {
            Text(
                "Search below, pick a remote, tap its buttons.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            recipe.buttons.forEachIndexed { i, entry ->
                ChosenRow(
                    index = i,
                    entry = entry,
                    editing = editingIndex == i,
                    onRename = { recipe = recipe.renameAt(i, it) },
                    onDoneEdit = { editingIndex = -1 },
                    onEdit = { editingIndex = i },
                    onUp = { recipe = recipe.move(i, i - 1) },
                    onDown = { recipe = recipe.move(i, i + 1) },
                    onRemove = { recipe = recipe.removeAt(i) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        val source = sourceRemote
        if (source == null) {
            Text(
                "Source remote",
                style = MaterialTheme.typography.labelLarge, color = PaperFaint,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search remotes") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (results.isEmpty()) {
                Text(
                    if (query.isBlank()) "Type to search the database." else "No remotes match.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            results.forEach { (remote, display) ->
                Row(
                    Modifier.fillMaxWidth().pressable { openSource(remote) }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        display,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    ActionIconView(ActionIcon.ChevRight, 18.dp, MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Hairline()
            }
        } else {
            Row(
                Modifier.fillMaxWidth().pressable { sourceRemote = null }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionIconView(ActionIcon.Back, 18.dp, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "All remotes",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Hairline()
            if (sourceButtons.isEmpty()) {
                Text(
                    "This remote has no pad-mappable buttons.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            sourceButtons.forEach { b ->
                val added = recipe.buttons.any { it.remoteId == b.remoteId && it.sourceName == b.name }
                Row(
                    Modifier.fillMaxWidth().pressable {
                        if (!added) recipe = recipe.add(b.remoteId, b.name)
                    }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        b.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (added) {
                        ActionIconView(ActionIcon.Check, 18.dp, Accent)
                    } else {
                        ActionIconView(ActionIcon.Add, 18.dp, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Hairline()
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/** One chosen row: label, provenance, reorder / rename / remove controls. */
@Composable
private fun ChosenRow(
    index: Int,
    entry: BuilderRecipe.Entry,
    editing: Boolean,
    onRename: (String) -> Unit,
    onDoneEdit: () -> Unit,
    onEdit: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${index + 1}",
            style = MaterialTheme.typography.labelSmall, color = PaperFaint,
            modifier = Modifier.width(24.dp),
        )
        if (editing) {
            OutlinedTextField(
                value = entry.label,
                onValueChange = onRename,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            )
            ActionIconView(
                ActionIcon.Check, 20.dp, Accent,
                modifier = Modifier.pressable(onDoneEdit).padding(8.dp),
            )
        } else {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${entry.sourceName} · remote ${entry.remoteId}",
                    style = MaterialTheme.typography.labelSmall, color = PaperFaint,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            val dim = MaterialTheme.colorScheme.onSurfaceVariant
            ActionIconView(
                ActionIcon.ChevronUp, 18.dp, dim,
                modifier = Modifier.pressable(index > 0, onUp).padding(8.dp),
            )
            ActionIconView(
                ActionIcon.ChevronDown, 18.dp, dim,
                modifier = Modifier.pressable { onDown() }.padding(8.dp),
            )
            ActionIconView(
                ActionIcon.Edit, 18.dp, dim,
                modifier = Modifier.pressable(onEdit).padding(8.dp),
            )
            ActionIconView(
                ActionIcon.Trash, 18.dp, MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.pressable(onRemove).padding(8.dp),
            )
        }
    }
    Hairline()
}

/** Flat hairline separator — no cards, no rounded boxes. */
@Composable
private fun Hairline() {
    Box(
        Modifier.fillMaxWidth().height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    )
}
