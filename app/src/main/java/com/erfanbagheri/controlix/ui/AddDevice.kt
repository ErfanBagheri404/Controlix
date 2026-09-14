package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.IrCodeRepository

/**
 * Setup flow. Step 1: category rows with hand-drawn glyphs. Step 2: brand
 * list (A-Z, deduped case variants). The ritual takes over after — no model
 * numbers anywhere.
 */

private val FEATURED = listOf(
    "tvs", "acs", "fans", "projectors", "soundbars",
    "audio_and_video_receivers", "streaming_devices", "monitors", "consoles",
)

fun glyphFor(slug: String): CategoryGlyph = when (slug) {
    "tvs", "tv_tuner", "universal_tv_remotes" -> CategoryGlyph.TV
    "acs" -> CategoryGlyph.AC
    "fans" -> CategoryGlyph.Fan
    "projectors" -> CategoryGlyph.Projector
    "soundbars" -> CategoryGlyph.Soundbar
    "audio_and_video_receivers", "head_units", "car_multimedia" -> CategoryGlyph.AVR
    "cable_boxes", "dvb-t", "converters", "multimedia" -> CategoryGlyph.SetTop
    "speakers" -> CategoryGlyph.Speaker
    "cd_players", "dvd_players", "blu-ray", "laserdisc", "minidisc" -> CategoryGlyph.Disc
    "cameras", "cctv" -> CategoryGlyph.Camera
    "heaters" -> CategoryGlyph.Heater
    "fireplaces" -> CategoryGlyph.Fireplace
    "vacuum_cleaners", "dust_collectors", "window_cleaners" -> CategoryGlyph.Vacuum
    "monitors", "computers", "touchscreen_displays" -> CategoryGlyph.Monitor
    "consoles", "toys" -> CategoryGlyph.Console
    "streaming_devices", "kvm", "digital_signs" -> CategoryGlyph.Streaming
    "humidifiers" -> CategoryGlyph.Humidifier
    "air_purifiers" -> CategoryGlyph.Purifier
    "clocks", "picture_frames" -> CategoryGlyph.Clock
    "vcr" -> CategoryGlyph.VCR
    "iodn_irblaster" -> CategoryGlyph.Rays
    else -> CategoryGlyph.Chip
}

@Composable
fun AddDeviceScreen(
    repo: IrCodeRepository,
    onPick: (brandId: Int, brandName: String, catSlug: String, catName: String) -> Unit,
    onBack: () -> Unit,
) {
    var chosen: IrCodeRepository.Category? by remember { mutableStateOf(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(40.dp))
        BackRow(onBack)
        Spacer(Modifier.height(20.dp))
        val cat = chosen
        if (cat == null) {
            Text("What are you adding?", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            CategoryGrid(
                categories = remember { repo.categories() },
                onPick = { chosen = it },
            )
        } else {
            Text("${cat.name}. Who made it?", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            BrandList(
                brands = remember(cat.slug) { repo.brands(cat.slug) },
                onBack = { chosen = null },
            ) { onPick(it.id, it.name, cat.slug, cat.name) }
        }
    }
}

@Composable
private fun CategoryGrid(
    categories: List<IrCodeRepository.Category>,
    onPick: (IrCodeRepository.Category) -> Unit,
) {
    val ordered = remember(categories) {
        val by = categories.associateBy { it.slug }
        FEATURED.mapNotNull { by[it] } + categories.filterNot { FEATURED.contains(it.slug) }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(ordered, key = { it.id }) { cat ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .hairlineTile(14.dp)
                    .pressable(onClick = { onPick(cat) })
                    .padding(horizontal = 10.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CategoryIcon(glyphFor(cat.slug), 26.dp, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(
                    cat.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    lineHeight = MaterialTheme.typography.labelMedium.lineHeight * 2,
                )
            }
        }
    }
}

@Composable
private fun BrandList(
    brands: List<IrCodeRepository.Brand>,
    onBack: () -> Unit,
    onPick: (IrCodeRepository.Brand) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Case-dedupe ("Samsung" vs "SAMSUNG" from the merge) — one row per brand.
    val all = remember(brands) {
        val seen = HashSet<String>()
        brands.filter { seen.add(it.name.lowercase()) }
    }
    val shown = remember(query, all) {
        if (query.isBlank()) all else all.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Search brands") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    )
    Spacer(Modifier.height(8.dp))
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(shown, key = { it.id }) { b ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .hairlineTile(14.dp)
                    .pressable(onClick = { onPick(b) })
                    .padding(horizontal = 14.dp, vertical = 16.dp),
            ) {
                Text(
                    b.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${b.remoteCount} ${if (b.remoteCount == 1) "remote" else "remotes"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
