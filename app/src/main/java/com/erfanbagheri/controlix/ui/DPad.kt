package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.erfanbagheri.controlix.data.FontScale
import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.ui.theme.PaperFaint

/** Cross layout with a circular center. Tiles = bg-tiled, radius 16dp. */
@Composable
fun DPad(keys: List<EffectiveButtons.Resolved>, onTap: (EffectiveButtons.Resolved) -> Unit) {
    val up = keys.getOrNull(0)
    val left = keys.getOrNull(1)
    val ok = keys.getOrNull(2)
    val right = keys.getOrNull(3)
    val down = keys.getOrNull(4)
    @Composable
    fun cell(key: EffectiveButtons.Resolved?, enabled: Boolean, modifier: Modifier) {
        val h = 56.dp * FontScale.heightMultiplier(LocalDensity.current.fontScale)
        Box(
            modifier = modifier
                .height(h)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (enabled && key != null) Modifier.pressable { onTap(key) } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                key?.name ?: "·",
                style = MaterialTheme.typography.titleMedium,
                color = if (key != null) MaterialTheme.colorScheme.onSurface else PaperFaint.copy(alpha = 0.4f),
            )
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(0.62f)) { cell(up, true, Modifier.weight(1f)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cell(left, true, Modifier.weight(1f))
            cell(ok, ok != null, Modifier.weight(1f))
            cell(right, true, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(0.62f)) { cell(down, true, Modifier.weight(1f)) }
    }
}
