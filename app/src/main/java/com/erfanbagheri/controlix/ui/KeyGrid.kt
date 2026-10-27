package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.data.FontScale

/**
 * A grid of real keys.
 *
 * Drawn exactly like the TV pad's [KeyTile] in ui/Pad.kt: a filled
 * surfaceVariant tile at 20dp radius, no stroke, titleMedium label, minimum
 * height that grows with the system font scale. That is the app's own
 * remote language — appliance pads borrow it instead of inventing a second
 * one, so a fan and a TV feel like the same product.
 *
 * LARGE = two per line (primary actions), SMALL = three per line
 * (supporting keys), SQUARE = three per line at full height (keypads).
 */
@Composable
fun KeyGrid(
    keys: List<EffectiveButtons.Resolved>,
    keySize: PadKeySize,
    onTap: (EffectiveButtons.Resolved) -> Unit,
    labels: List<String>? = null,
) {
    // A row holding one or two keys should not leave two thirds of the width
    // empty with a stray pill floating at the edge — which is exactly what a
    // fireplace (Power, Timer) looked like. One key fills the row; two split
    // it. Only three or more fall back to the tile size's own column count.
    val perRow = when {
        keys.size == 1 -> 1
        keys.size == 2 -> 2
        keySize == PadKeySize.LARGE -> 2
        else -> 3
    }
    val gap = 10.dp
    Column(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        // A projector lists "SOURCE VIDEO" and "INPUT VIDEO" — two codes, one
        // visible label. Two identical tiles are a coin flip for the user, so
        // the first wins and the twin is dropped. Pair each key with its label
        // first so a dropped twin cannot shift the rest of the labels.
        // ponytail: drops the duplicate code, not the label. Upgrade path: a
        // long-press "all keys" sheet if a dropped code ever matters.
        val items = keys.mapIndexed { i, k -> k to (labels?.getOrNull(i) ?: k.name) }
            .distinctBy { prettyKey(it.second) }
        items.chunked(perRow).forEach { chunk ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                chunk.forEach { (key, label) ->
                    KeyTile(
                        label = label,
                        keySize = keySize,
                        modifier = Modifier.weight(1f),
                        onClick = { onTap(key) },
                    )
                }
                // Keep a short last row aligned to the grid instead of letting
                // two keys stretch across a three-column row.
                repeat(perRow - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The DB writes key labels as `Subwf_Down`, `AVDelay_`, `VZTVRem`. Shown raw
 * they read as source code. Underscores become spaces and the handful of
 * abbreviations every soundbar remote uses get their full word back.
 *
 * ponytail: a fixed word map, not a translation layer — the ceiling is
 * English-only appliance keys, which is what the DB carries. Upgrade path:
 * per-brand label maps once a second brand needs different words.
 */
private val KEY_WORDS = mapOf(
    "subwf" to "Subwoofer", "surr" to "Surround", "bal" to "Balance",
    "vol" to "Volume", "avdelay" to "A/V Delay", "ch" to "Channel",
    "temp" to "Temperature", "src" to "Source", "eq" to "EQ",
    // A bare "Mode" key does not name a mode, it steps to the next one.
    // Printed under a "Mode" heading the label would read "Mode / Mode".
    "mode" to "Cycle", "humididier" to "Humidifier",
)

fun prettyKey(raw: String): String {
    val words = raw.replace('_', ' ').trim().split(' ').filter { it.isNotBlank() }
    // "Source HDMI In" and "INPUT HDMI" are the same socket named twice — one
    // key is the remote's menu entry, the other a direct input. Two tiles read
    // the same in the hand, so the redundant word goes and the socket name
    // stays: the row is already headed "Input".
    val socket = words.size > 1 && (words[0].equals("Source", true) || words[0].equals("Input", true))
    // The same socket is spelled "SOURCE INPUT A" on one key and "INPUT A" on
    // another, so every leading source/input word goes. What is left is often
    // a bare letter ("A", "B", "D4") that means nothing on a tile — the row is
    // headed "Input", so a short token gets that word back.
    var drop = 0
    while (drop < words.size - 1 &&
        (words[drop].equals("Source", true) || words[drop].equals("Input", true))
    ) drop++
    val core = if (socket) words.drop(drop) else words
    val kept = if (socket && core.size == 1 && core[0].length <= 3) listOf("Input") + core else core
    val out = kept.joinToString(" ") { w ->
        KEY_WORDS[w.lowercase()] ?: w.replaceFirstChar { it.uppercase() }
    }.replace(Regex("\\s+"), " ").trim().ifBlank { raw }
    // A duration like "5m" is a number followed by a Latin unit. In an RTL
    // paragraph the bidi algorithm flips that run and the tile reads "m5" —
    // the fan's whole mode grid came out backwards in Farsi. An LTR isolate
    // keeps the run intact whatever the paragraph direction.
    // ponytail: only digit-led labels need it. Upgrade path: full bidi
    // formatting per key once a label mixes scripts mid-word.
    return if (out.firstOrNull()?.isDigit() == true) "\u2066$out\u2069" else out
}

/** One key: filled tile, 20dp radius, no border — the TV pad's own look. */
@Composable
private fun KeyTile(
    label: String,
    keySize: PadKeySize,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val min = when (keySize) {
        PadKeySize.LARGE -> 58.dp
        PadKeySize.SMALL -> 50.dp
        PadKeySize.SQUARE -> 62.dp
    }
    Box(modifier.heightIn(min = min * FontScale.heightMultiplier(LocalDensity.current.fontScale))) {
        Box(
            Modifier.fillMaxSize().bgTile(20.dp, MaterialTheme.colorScheme.surfaceVariant)
                .pressable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                prettyKey(label),
                style = MaterialTheme.typography.titleMedium.copy(
                    textDirection =
                        if (prettyKey(label).firstOrNull()?.isDigit() == true) TextDirection.Ltr
                        else TextDirection.Content,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** Keypad: sorted 0-9, three per row, digits on square keys. */
@Composable
fun DigitGrid(keys: List<EffectiveButtons.Resolved>, onTap: (EffectiveButtons.Resolved) -> Unit) {
    val sorted = keys.sortedBy { it.name.lastOrNull { c -> c.isDigit() } ?: ' ' }
    KeyGrid(sorted, PadKeySize.SQUARE, onTap)
}

@Composable
fun PadRowDivider() {
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
}
