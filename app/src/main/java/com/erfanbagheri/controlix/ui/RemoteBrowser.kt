package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.IrCodeRepository

/**
 * Category -> Brand -> Remote -> Button pad navigation.
 * Each level is a simple grid/list; button pad transmits on tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteBrowser(
    repo: IrCodeRepository,
    transmitter: com.erfanbagheri.controlix.ir.IrTransmitter,
    onExit: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<IrCodeRepository.Category?>(null) }
    var selectedBrand by remember { mutableStateOf<IrCodeRepository.Brand?>(null) }
    var selectedRemoteId by remember { mutableStateOf<Int?>(null) }
    var remoteName by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val label = when {
                        remoteName != null -> remoteName!!
                        selectedBrand != null -> selectedBrand!!.name
                        selectedCategory != null -> selectedCategory!!.name
                        else -> "Controlix"
                    }
                    Text(label)
                },
                navigationIcon = {
                    // Back navigation: pop one level at a time
                    if (selectedCategory != null) {
                        TextButton(onClick = {
                            if (selectedRemoteId != null) {
                                selectedRemoteId = null; remoteName = null
                            } else if (selectedBrand != null) selectedBrand = null
                            else selectedCategory = null
                        }) { Text("Back") }
                    }
                },
                actions = {
                    TextButton(onClick = onExit) { Text("Exit") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                selectedRemoteId != null -> ButtonPad(
                    remoteId = selectedRemoteId!!,
                    repo = repo,
                    transmitter = transmitter,
                    onSent = { name -> toast = name }
                )
                selectedBrand != null -> RemoteList(
                    brand = selectedBrand!!,
                    repo = repo,
                    onPick = { id, name ->
                        selectedRemoteId = id; remoteName = name
                    }
                )
                selectedCategory != null -> BrandList(
                    category = selectedCategory!!,
                    repo = repo,
                    onPick = { selectedBrand = it }
                )
                else -> CategoryGrid(repo = repo, onPick = { selectedCategory = it })
            }
        }
    }

    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar("Sent: $it")
            toast = null
    }
    }
}

@Composable
fun CategoryGrid(repo: IrCodeRepository, onPick: (IrCodeRepository.Category) -> Unit) {
    val categories by produceState(initialValue = emptyList<IrCodeRepository.Category>()) {
        value = repo.categories()
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories, key = { it.id }) { cat ->
            Card(modifier = Modifier.clickable { onPick(cat) }) {
                Text(
                    cat.name,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun BrandList(
    category: IrCodeRepository.Category,
    repo: IrCodeRepository,
    onPick: (IrCodeRepository.Brand) -> Unit
) {
    val brands by produceState(initialValue = emptyList<IrCodeRepository.Brand>(), category.slug) {
        value = repo.brands(category.slug)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(brands, key = { it.id }) { brand ->
            Card(modifier = Modifier.clickable { onPick(brand) }) {
                Column(Modifier.padding(12.dp)) {
                    Text(brand.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${brand.remoteCount} remotes",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun RemoteList(
    brand: IrCodeRepository.Brand,
    repo: IrCodeRepository,
    onPick: (Int, String) -> Unit
) {
    val remotes by produceState(initialValue = emptyList<IrCodeRepository.Remote>(), brand.id) {
        value = repo.remotes(brand.id)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(remotes, key = { it.id }) { r ->
            Card(modifier = Modifier.clickable { onPick(r.id, r.modelName ?: r.fileName) }) {
                Text(
                    r.modelName ?: r.fileName,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Button pad. Renders a grid of buttons for the selected remote; each tap
 * transmits the stored pattern. Volume/channel-style buttons get a wider
 * cell to hint importance.
 */
@Composable
fun ButtonPad(
    remoteId: Int,
    repo: IrCodeRepository,
    transmitter: com.erfanbagheri.controlix.ir.IrTransmitter,
    onSent: (String) -> Unit
) {
    val buttons by produceState(
        initialValue = emptyList<IrCodeRepository.Button>(),
        remoteId
    ) {
        value = repo.buttons(remoteId)
    }
    if (buttons.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No usable buttons for this remote")
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(buttons, key = { it.name }) { btn ->
            OutlinedButton(
                onClick = {
                    val ok = transmitter.transmitButton(btn.carrierHz, btn.pattern)
                    onSent(if (ok) btn.name else "${btn.name} (failed)")
                },
                modifier = Modifier.height(48.dp)
            ) {
                Text(prettyButtonName(btn.name), maxLines = 1)
            }
        }
    }
}

/** "Vol_up" -> "Vol +" style pretty printing for common button names. */
fun prettyButtonName(raw: String): String = when (raw.lowercase().replace(' ', '_')) {
    "power", "on/off", "onoff", "power_on", "power_off", "pwr" -> "Power"
    "vol_up", "vol+", "volume_up", "volup" -> "Vol +"
    "vol_dn", "vol-", "volume_down", "voldown", "vol_dwn" -> "Vol −"
    "ch_up", "ch+", "channel_up", "chan_up" -> "CH +"
    "ch_dn", "ch-", "ch_down", "channel_down", "chan_down" -> "CH −"
    "mute" -> "Mute"
    "menu" -> "Menu"
    "ok", "enter", "select" -> "OK"
    "up" -> "▲"
    "down" -> "▼"
    "left" -> "◀"
    "right" -> "▶"
    "back", "exit", "return" -> "Back"
    "home" -> "Home"
    else -> raw.replace('_', ' ').trim()
}
