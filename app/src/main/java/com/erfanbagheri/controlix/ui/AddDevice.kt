package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ui.theme.Ember
import com.erfanbagheri.controlix.ui.theme.Hairline
import com.erfanbagheri.controlix.ui.theme.InkRaised
import com.erfanbagheri.controlix.ui.theme.Paper
import com.erfanbagheri.controlix.ui.theme.PaperDim

/**
 * One question per screen, full bleed — per docs/DESIGN.md.
 * Step 1: category (image tiles, 2-col). Step 2: brand (search + list).
 */

/** Slugs shown first — the ones people actually own. The rest follow. */
private val FEATURED = listOf("tvs", "acs", "fans", "projectors", "soundbars",
    "audio_and_video_receivers", "streaming_devices", "monitors", "consoles")

/** Real 46-category slugs → line glyph. Anything unmapped gets [Chip]. */
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
    else -> CategoryGlyph.Chip // misc, bidet, lifts, whiteboards…
}

@Composable
fun AddDeviceScreen(
    repo: IrCodeRepository,
    onPick: (catSlug: String, brandId: Int, brandName: String, catName: String) -> Unit,
    onBack: () -> Unit,
) {
    var chosen: IrCodeRepository.Category? by remember { mutableStateOf(null) }
    if (chosen == null) {
        CategoryGrid(
            categories = remember { repo.categories() },
            onPick = { chosen = it },
            onBack = onBack,
        )
    } else {
        val cat = chosen!!
        BrandList(
            title = cat.name,
            brands = remember(cat.slug) { repo.brands(cat.slug) },
            onPick = { onPick(cat.slug, it.id, it.name, cat.name) },
            onBack = { chosen = null },
        )
    }
}

@Composable
private fun Shell(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ Back", color = Ember, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(8.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.displaySmall, color = Paper)
        Spacer(Modifier.height(20.dp))
        content()
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CategoryGrid(
    categories: List<IrCodeRepository.Category>,
    onPick: (IrCodeRepository.Category) -> Unit,
    onBack: () -> Unit,
) {
    val ordered = remember(categories) {
        val by = categories.associateBy { it.slug }
        FEATURED.mapNotNull { by[it] } + categories.filterNot { FEATURED.contains(it.slug) }
    }
    Shell(title = "What are you adding?", onBack = onBack) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(ordered, key = { it.id }) { cat ->
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(InkRaised)
                        .border(1.dp, Hairline, RoundedCornerShape(20.dp))
                        .clickable { onPick(cat) }
                        .padding(18.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    CategoryIcon(glyphFor(cat.slug), size = 44.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(cat.name, style = MaterialTheme.typography.titleMedium, color = Paper, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun BrandList(
    title: String,
    brands: List<IrCodeRepository.Brand>,
    onPick: (IrCodeRepository.Brand) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, brands) {
        if (query.isBlank()) brands
        else brands.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    // De-dupe display names the iodn merge produced ("Samsung" vs "SAMSUNG").
    val deduped = remember(shown) {
        val seen = HashSet<String>()
        shown.filter { seen.add(it.name.lowercase()) }
    }
    Shell(title = "Who made it?", onBack = onBack) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search $title brands") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Paper, unfocusedTextColor = Paper,
                focusedBorderColor = Ember, unfocusedBorderColor = Hairline,
                cursorColor = Ember,
            ),
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(deduped, key = { it.id }) { b ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(InkRaised)
                        .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                        .clickable { onPick(b) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(b.name, style = MaterialTheme.typography.titleMedium, color = Paper,
                        modifier = Modifier.weight(1f))
                    Text("›", style = MaterialTheme.typography.headlineSmall, color = PaperDim)
                }
            }
        }
    }
}