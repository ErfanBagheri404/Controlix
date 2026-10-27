package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.data.FontScale
import com.erfanbagheri.controlix.ui.theme.Accent
import com.erfanbagheri.controlix.ui.theme.PaperFaint

/** A stepper row: − | label | + with bg-tiled, rounded tiles. */
@Composable
fun StepperRow(
    row: PadRow,
    label: String,
    modifier: Modifier = Modifier,
    transmit: (EffectiveButtons.Resolved) -> Unit,
) {
    val dn = row.downKey
    val up = row.upKey
    Row(
        modifier.heightIn(min = 56.dp * FontScale.heightMultiplier(LocalDensity.current.fontScale)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StepKey("−", dn != null, Modifier.weight(1f)) { dn?.let(transmit) }
        Text(
            label.ifBlank { "·" },
            style = MaterialTheme.typography.titleSmall,
            color = Accent,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.4f),
        )
        StepKey("+", up != null, Modifier.weight(1f)) { up?.let(transmit) }
    }
}

/** One tile of a stepper: filled surface, corner radius 14dp, titleSmall. */
@Composable
fun StepKey(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(56.dp * FontScale.heightMultiplier(LocalDensity.current.fontScale))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (enabled) Modifier.pressable(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else PaperFaint.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
        )
    }
}
