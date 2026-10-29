package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.R
import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.tr

/**
 * Overflow keys of an appliance remote that do not fit the main pad, listed
 * in a bottom sheet. Reuses the pad's own [KeyGrid] so the tiles look like
 * the rest of the product instead of a second key language.
 *
 * ponytail: a plain scrollable Column, not a LazyVerticalGrid — appliance
 * overflow key counts are small (tens at most). Upgrade path: swap in
 * LazyVerticalGrid if a remote ever ships hundreds of extra keys.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraKeysSheet(
    keys: List<EffectiveButtons.Resolved>,
    onKey: (EffectiveButtons.Resolved) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            SectionHead(tr(R.string.appliance_extra_keys))
            Spacer(Modifier.height(4.dp))
            Text(
                tr(R.string.appliance_extra_keys_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            KeyGrid(keys, PadKeySize.SMALL, onKey)
        }
    }
}
