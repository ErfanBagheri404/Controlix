package com.erfanbagheri.controlix.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.BorrowedCodeMemory
import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.data.SiblingButtonPicker
import com.erfanbagheri.controlix.ui.theme.Accent
import com.erfanbagheri.controlix.ui.theme.Gold
import com.erfanbagheri.controlix.tr
import com.erfanbagheri.controlix.R

/** Android persistence boundary around the pure [BorrowedCodeMemory]. */
class BorrowedCodeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("borrowed_codes", Context.MODE_PRIVATE)

    var memory: BorrowedCodeMemory
        get() = BorrowedCodeMemory.parse(prefs.getString("decisions", "").orEmpty())
        set(value) {
            prefs.edit().putString("decisions", value.serialize()).apply()
        }
}

/** Current borrowed candidate, rendered when the user presses its key. */
internal data class PendingBorrow(
    val resolved: EffectiveButtons.Resolved,
    val candidate: SiblingButtonPicker.Picked,
)

/** Adapt the resolved row back to the picker's identity for memory decisions. */
internal fun EffectiveButtons.Resolved.asPicked() = SiblingButtonPicker.Picked(
    remoteId = remoteId,
    name = name,
    pattern = pattern,
    votes = votes,
    carrierHz = carrierHz,
)

@Composable
internal fun rememberBorrowedCodeMemory(): Pair<BorrowedCodeMemory, (BorrowedCodeMemory) -> Unit> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember(context) { BorrowedCodeStore(context) }
    var memory by remember(context) { mutableStateOf(store.memory) }
    return memory to { updated ->
        memory = updated
        store.memory = updated
    }
}

/** "Borrowed" provenance for a key: source sibling + number of remotes in agreement. */
internal fun borrowedSourceLabel(resolved: EffectiveButtons.Resolved, remoteName: String?): String {
    val source = remoteName?.takeIf { it.isNotBlank() }
        ?: tr(R.string.borrowed_sibling_fallback, resolved.remoteId)
    val agree = if (resolved.votes == 1) tr(R.string.borrowed_agree_one) else tr(R.string.borrowed_agree_many)
    return tr(R.string.borrowed_source_votes, source, resolved.votes, agree)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BorrowedCodeConfirmSheet(
    pending: PendingBorrow,
    source: String,
    onKeep: () -> Unit,
    onReject: () -> Unit,
) {
    val keyLabel = pending.resolved.key.replace('_', ' ').replaceFirstChar { it.uppercase() }
    ModalBottomSheet(
        onDismissRequest = onReject,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Text(tr(R.string.borrowed_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                tr(
                    R.string.borrowed_sheet, keyLabel, source, pending.resolved.votes,
                    if (pending.resolved.votes == 1) tr(R.string.borrowed_sibling_one)
                    else tr(R.string.borrowed_sibling_many),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                tr(R.string.borrowed_try),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceButton(tr(R.string.borrowed_try_next), Modifier.weight(1f)) { onReject() }
                ChoiceButton(tr(R.string.borrowed_keep_send), Modifier.weight(1f), primary = true) { onKeep() }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChoiceButton(label: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    val color = if (primary) Accent else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .border(1.dp, color, RoundedCornerShape(18.dp))
            .pressable(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = color)
    }
}
