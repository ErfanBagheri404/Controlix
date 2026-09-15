package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ui.theme.PaperFaint

/**
 * Setup flow. Step 1: category tiles (2 columns, Material vectors).
 * Step 2: brand tiles under a search field. The ritual takes over after —
 * no model numbers anywhere.
 */

private val FEATURED = listOf(
    "tvs", "acs", "fans", "projectors", "soundbars",
    "audio_and_video_receivers", "streaming_devices", "monitors", "consoles",
)

@Composable
fun AddDeviceScreen(
    repo: IrCodeRepository,
    onPick: (brandId: Int, brandName: String, catSlug: String, catName: String) -> Unit,
    onScan: () -> Unit,
    onBack: () -> Unit,
) {
    var chosen: IrCodeRepository.Category? by remember { mutableStateOf(null) }
    Column(Modifier.fillMaxSize().applyTopInset().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BackRow(onBack)
            ActionIconView(
                ActionIcon.QrScan,
                22.dp,
                MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.pressable(onScan).padding(10.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        val cat = chosen
        if (cat == null) {
            Text("What are you adding?", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(16.dp))
            CategoryGrid(
                categories = remember { repo.categories() },
                onPick = { chosen = it },
            )
        } else {
            Text("${cat.name}. Who made it?", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            BrandGrid(
                brands = remember(cat.slug) { repo.brands(cat.slug) },
                onPick = { onPick(it.id, it.name, cat.slug, cat.name) },
            )
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
        columns = GridCells.Fixed(2),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(ordered, key = { it.id }) { cat ->
            // Square tiles: identical height every row, no drift.
            Column(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
                    .bgTile(16.dp)
                    .pressable(onClick = { onPick(cat) })
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                MaterialIcon(
                    CategoryIcons.forCategory(cat.slug),
                    40.dp,
                    MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    cat.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BrandGrid(
    brands: List<IrCodeRepository.Brand>,
    onPick: (IrCodeRepository.Brand) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Case-dedupe ("Samsung" vs "SAMSUNG" from the merge) — one tile per brand.
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
            // Fixed height per tile — brand names wrap/ellipsize, never resize.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .bgTile(14.dp)
                    .pressable(onClick = { onPick(b) })
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Column {
                    Text(
                        b.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${b.remoteCount} ${if (b.remoteCount == 1) "remote" else "remotes"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = PaperFaint,
                    )
                }
            }
        }
    }
}
