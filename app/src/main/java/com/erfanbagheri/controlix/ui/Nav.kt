package com.erfanbagheri.controlix.ui

import com.erfanbagheri.controlix.data.FontScale
import androidx.compose.ui.platform.LocalDensity
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.DbChangelog
import com.erfanbagheri.controlix.data.DbRefresh
import com.erfanbagheri.controlix.data.AppUpdateCheck
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.feature.AutomationState
import com.erfanbagheri.controlix.feature.ExternalCommand
import com.erfanbagheri.controlix.data.ReleaseInfo
import com.erfanbagheri.controlix.data.SemVer
import com.erfanbagheri.controlix.data.UpdateCheckState
import com.erfanbagheri.controlix.data.WhatsNewStore
import com.erfanbagheri.controlix.data.RemoteShareCodec
import com.erfanbagheri.controlix.feature.sceneFromMacro
import com.erfanbagheri.controlix.data.RefreshState
import com.erfanbagheri.controlix.data.RepeatSettings
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ir.TransmitterChoice
import com.erfanbagheri.controlix.ir.TransmitterSelection
import com.erfanbagheri.controlix.ir.display
import com.erfanbagheri.controlix.quicksettings.TileStore
import com.erfanbagheri.controlix.ui.theme.ThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import com.erfanbagheri.controlix.tr
import com.erfanbagheri.controlix.isFa
import com.erfanbagheri.controlix.setLanguage
import com.erfanbagheri.controlix.R
import com.erfanbagheri.controlix.data.Copy

/** Every screen. Sealed route list, no nav library — app is 4 levels deep max. */
private sealed interface Route {
    data class MissingCode(
        val brand: String = "",
        val category: String = "",
        val remote: String = "",
        val button: String = "",
    ) : Route

    data object Home : Route
    data object AddDevice : Route
    data object Scan : Route
    data class Ritual(val brandId: Int, val brandName: String, val catSlug: String, val catName: String) : Route
    data class Pad(val remoteId: Int) : Route
    data class Edit(val remoteId: Int) : Route
    data class Share(val remoteId: Int) : Route
    data object Sweep : Route
    data object SelfTest : Route
    data object Macros : Route
    data object Builder : Route
    data object DbHealth : Route
    /** Button picker for the analyzer; null remote = every saved device. */
    data class Analyzer(val remoteId: Int? = null) : Route
    data class SignalAnalyzer(
        val buttonName: String,
        val carrierHz: Int,
        val pattern: IntArray,
        val protocol: String?,
    ) : Route
    data object RecentSends : Route
    data object TileTarget : Route
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlixNav(ir: IrTransmitter, repo: IrCodeRepository?, coldStart: Boolean = true) {
    val model = rememberDeviceModel(repo)
    val macroModel = rememberMacroModel()
    // The index re-keys stored favourites and lets the pad pin keys, so the
    // model is built once the DB is open (issue #71).
    val remoteIndex = remember(repo) { runCatching { repo?.remoteIndex() }.getOrNull() }
    val favoritesModel = rememberFavoritesModel(index = remoteIndex)
    val ctx = LocalContext.current
    val copiedModel = rememberCopiedButtonModel()
    // Issue #68: one shared history — pads append, Recent sends reads it.
    val sendLogModel = rememberSendLogModel()
    // Cold start only: a cold launch restores the last-used pad; Activity
    // recreation (rotation, savedInstanceState != null) lands on Home —
    // route is plain remember, not saveable. Pad back still returns
    // to Home — no trap.
    var route: Route by remember {
        mutableStateOf(
            resumeRestoreTarget(
                enabled = effectiveResumeEnabled(ResumeState.explicit, model.devices.isNotEmpty()),
                lastRemoteId = ResumeState.lastRemoteId,
                enabledDeviceIds = model.devices.filter { it.enabled }.map { it.remoteId }.toSet(),
            )?.takeIf { coldStart }?.let { Route.Pad(it) } ?: Route.Home,
        )
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val toast = rememberToastState()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = BackupCodec.encode(model.devices, macroModel.macros, BackupStoreIo.capture(context))
        runCatching {
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: error("Backup stream unavailable")
            output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }.onSuccess { toast.show(tr(R.string.backup_exported)) }
            .onFailure { toast.show(tr(R.string.backup_couldnt_write)) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (json == null) {
            toast.show(tr(R.string.backup_couldnt_read))
            return@rememberLauncherForActivityResult
        }
        when (val result = BackupCodec.decode(json)) {
            is BackupDecodeResult.Error -> toast.show(Copy.backupError(result.message))
            is BackupDecodeResult.Success -> {
                val backup = result.backup
                // Issue #83: a restore that drops a store must say which one,
                // rather than reporting success over a partial state.
                val lost = backup.unreadableStores
                val lostNames = lost.map { Copy.backupStore(it) }
                val label = when {
                    lost.isEmpty() -> tr(
                        R.string.backup_replace_confirm,
                        backup.devices.size, backup.macros.size,
                    )
                    lost.size == 1 -> tr(R.string.backup_lost_one, lostNames[0])
                    else -> tr(R.string.backup_lost_many, lost.size, lostNames.joinToString())
                }
                toast.show(label, actionLabel = tr(R.string.backup_import)) {
                    model.replaceAll(backup.devices)
                    macroModel.save(backup.macros)
                    val unreadable = BackupStoreIo.restore(context, backup.stores)
                    val stillLost = (lost + unreadable).distinct()
                    toast.show(
                        if (stillLost.isEmpty()) tr(R.string.backup_restored)
                        else tr(
                            R.string.backup_read_failed_partial,
                            stillLost.map { Copy.backupStore(it) }.joinToString(),
                        ),
                    )
                }
            }
        }
    }


    // Database refresh (issue #12): pure plan in data/, thin IO shell here.
    var refreshState by remember { mutableStateOf<RefreshState>(RefreshState.Idle) }
    val dbCounts = remember(repo) { repo?.counts() ?: (0 to 0) }
    val onUpdateDb = {
        if (refreshState == RefreshState.Idle || refreshState is RefreshState.Failed ||
            refreshState is RefreshState.RolledBack || refreshState is RefreshState.Done
        ) {
            scope.launch(Dispatchers.IO) {
                DbRefresh.run(context.getDatabasePath("bundled_codes.db")) { refreshState = it }
            }
        }
    }
    fun openPad(remoteId: Int) {
        ResumeState.recordLastRemote(remoteId)
        route = Route.Pad(remoteId)
    }

    // App update check (issue #51). Fires once per launch off the main thread
    // and only writes into the drawer's status line; a manual tap on the row
    // re-runs it. Never blocks first paint and never opens a dialog by itself.
    var updateState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }
    var whatsNewRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
    var whatsNewVersion by remember { mutableStateOf<String?>(null) }

    fun installedVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "unknown"

    suspend fun runUpdateCheck() {
        updateState = UpdateCheckState.Checking
        val installed = installedVersion()
        val release = withContext(Dispatchers.IO) { AppUpdateCheck.fetchLatest() }
        updateState = AppUpdateCheck.evaluate(installed, release)
        // "What's new" (issue #52) rides the same single fetch, no second request.
        // Only fire when the version we are RUNNING is the release itself, so the
        // notes always describe the build the user actually installed.
        val running = SemVer.parse(installed)
        val released = release?.let { SemVer.parse(it.tagName) }
        if (release != null && running != null && running == released &&
            WhatsNewStore.shouldShow(installed)
        ) {
            whatsNewVersion = installed
            whatsNewRelease = release
        }
    }

    LaunchedEffect(Unit) { runUpdateCheck() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (repo == null) {
            MissingDb()
            return@Box
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                MenuDrawer(
                    onBuild = { scope.launch { drawerState.close() }; route = Route.Builder },
                    acDevices = model.devices.filter { it.isAc() },
                    onOpenAc = { dev -> scope.launch { drawerState.close() }; route = Route.Pad(dev.remoteId) },
                    hasDevices = model.devices.isNotEmpty(),
                    onMacros = { scope.launch { drawerState.close() }; route = Route.Macros },
                    onSweep = { scope.launch { drawerState.close() }; route = Route.Sweep },
                    onSelfTest = { scope.launch { drawerState.close() }; route = Route.SelfTest },
                    onAnalyzer = { scope.launch { drawerState.close() }; route = Route.Analyzer() },
                    onUpdateDb = onUpdateDb,
                    dbChangelog = (refreshState as? RefreshState.Checked)?.let {
                        Copy.changelog(dbCounts.first, dbCounts.second, it.manifest.remoteCount, it.manifest.buttonCount)
                    },
                    dbState = when (val s = refreshState) {
                        RefreshState.Idle -> null
                        is RefreshState.RolledBack -> tr(R.string.db_rolled_back, Copy.rollbackReason(s.reason))
                        else -> Copy.refreshState(s)
                    },
                    onExport = {
                        scope.launch { drawerState.close() }
                        val stamp = LocalDate.now().toString().replace("-", "")
                        exportLauncher.launch("controlix-backup-$stamp.json")
                    },
                    onImport = {
                        scope.launch { drawerState.close() }
                        importLauncher.launch(
                            arrayOf("application/json", "application/octet-stream", "text/plain"),
                        )
                    },
                    onMissingCode = { scope.launch { drawerState.close() }; route = Route.MissingCode() },
                    onDbHealth = { scope.launch { drawerState.close() }; route = Route.DbHealth },
                    onCheckUpdate = { scope.launch { runUpdateCheck() } },
                    updateStateLabel = when (val s = updateState) {
                        UpdateCheckState.Idle -> null
                        UpdateCheckState.Checking -> tr(R.string.update_checking)
                        is UpdateCheckState.UpToDate -> tr(R.string.update_up_to_date, s.currentVersion)
                        is UpdateCheckState.UpdateAvailable -> tr(R.string.update_available_version, s.release.tagName)
                        is UpdateCheckState.Failed -> Copy.updateFailure(s.reason)
                    },
                    onRecentSends = { scope.launch { drawerState.close() }; route = Route.RecentSends },
                    onTileTarget = { scope.launch { drawerState.close() }; route = Route.TileTarget },
                )
            },
        ) {
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    if (!Feedback.animationsOn) {
                        (androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None).using(null)
                    } else {
                    val forward = targetState != Route.Home && initialState == Route.Home
                    val dir = if (forward) 1 else -1
                    val specIn = slideInHorizontally(tween(Motion.Route, easing = Motion.EaseOut)) { it / 5 * dir } +
                        fadeIn(tween(Motion.Route))
                    val specOut = slideOutHorizontally(tween(Motion.RouteExit, easing = Motion.EaseInOut)) { -it / 7 * dir } +
                        fadeOut(tween(120))
                    (specIn togetherWith specOut).using(null)
                    }
                },
                label = "route",
            ) { r ->
                when (r) {
                    is Route.Home -> HomeScreen(
                        model = model,
                        repo = repo,
                        transmitter = ir,
                        favoritesModel = favoritesModel,
                        onOpenDevice = { openPad(it.remoteId) },
                        onAddDevice = { route = Route.AddDevice },
                        onToggleDrawer = { Feedback.tap(view); scope.launch { drawerState.open() } },
                        onEdit = { route = Route.Edit(it.remoteId) },
                        onShare = { route = Route.Share(it.remoteId) },
                        toast = toast,
                    )

                    is Route.AddDevice -> AddDeviceScreen(
                        repo = repo,
                        onPick = { brandId, brandName, catSlug, catName ->
                            route = Route.Ritual(brandId, brandName, catSlug, catName)
                        },
                        onScan = { route = Route.Scan },
                        onBack = { route = Route.Home },
                    )

                    is Route.Scan -> ScanScreen(
                        onResult = { text ->
                            // New format (issue #56): the QR carries the buttons
                            // themselves, so no shared database is needed. The
                            // remote is stored on this phone with a negative id.
                            if (RemoteShareCodec.isCompact(text)) {
                                val shared = SharedRemoteImport.fromPayload(text)
                                if (shared != null) {
                                    val saved = SharedRemoteStore(context).save(shared)
                                    toast.show(tr(R.string.backup_imported, saved.name, saved.buttons.size))
                                    route = Route.Pad(saved.remoteId)
                                } else {
                                    toast.show(tr(R.string.menu_qr_not_controlix))
                                    route = Route.AddDevice
                                }
                                return@ScanScreen
                            }
                            // Legacy: controlix://remote/{id}/{name}/{brand}/{catSlug}
                            val m = Regex("""controlix://remote/(\d+)/([^/]*)/([^/]*)/([^/]*)""").find(text)
                            if (m != null && repo != null) {
                                val (id, name, brand, slug) = m.destructured
                                val rid = id.toIntOrNull() ?: -1
                                if (repo.buttons(rid).isNotEmpty()) {
                                    val decodedName = legacyShareName(name, brand)
                                    model.saveById(
                                        remoteId = rid,
                                        name = decodedName,
                                        brand = java.net.URLDecoder.decode(brand, "UTF-8"),
                                        categorySlug = slug,
                                        buttonCount = repo.buttons(rid).size,
                                        matched = false, // raw remote-id pick — no model matched
                                    )
                                    openPad(rid)
                                } else route = Route.AddDevice
                            } else route = Route.AddDevice
                        },
                        onError = { route = Route.AddDevice },
                        onBack = { route = Route.AddDevice },
                    )

                    is Route.Ritual -> RitualScreen(
                        repo = repo,
                        transmitter = ir,
                        brandId = r.brandId,
                        brandName = r.brandName,
                        categoryName = r.catName,
                        categorySlug = r.catSlug,
                        onMissingCode = { button ->
                            route = Route.MissingCode(
                                brand = r.brandName,
                                category = r.catSlug,
                                remote = "",
                                button = button,
                            )
                        },
                        onDone = { remoteId, _ ->
                            model.saveById(
                                remoteId = remoteId,
                                name = r.brandName,
                                brand = r.brandName,
                                categorySlug = r.catSlug,
                                buttonCount = repo.buttons(remoteId).size,
                            )
                            openPad(remoteId)
                            toast.show(tr(R.string.menu_brand_added, r.brandName))
                        },
                        onBack = { route = Route.Home },
                    )

                    is Route.MissingCode -> MissingCodeScreen(
                        initialBrand = r.brand,
                        initialCategory = r.category,
                        initialRemote = r.remote,
                        initialButton = r.button,
                        onBack = { route = Route.Home },
                    )

                    is Route.Pad -> {
                        // System back from a cold-start-restored pad returns Home, never traps.
                        BackHandler { route = Route.Home }
                        val saved = model.devices.firstOrNull { it.remoteId == r.remoteId }
                        // Issue #78: the tile fires a *pinned* target, not
                        // whichever pad was opened last. Opening a pad no longer
                        // writes the tile's slot; the picker is its only writer.
                        // AC remotes are stateful: their pad is a separate climate
                        // layout. Every other category keeps the normal TV pad.
                        if (saved?.isAc() == true) {
                            AcPadScreen(
                                deviceName = saved.name,
                                remoteId = r.remoteId,
                                repo = repo,
                                transmitter = ir,
                                toast = toast,
                                sendLog = sendLogModel,
                                onBack = { route = Route.Home },
                            )
                        } else PadScreen(
                            deviceName = saved?.name,
                            onRename = { newName -> saved?.let { model.rename(it.key, newName) } },
                            remoteId = r.remoteId,
                            repo = repo,
                            transmitter = ir,
                            devices = model.devices,
                            model = model,
                            favoritesModel = favoritesModel,
                            copied = copiedModel,
                            toast = toast,
                            sendLog = sendLogModel,
                            onSwitchDevice = { openPad(it.remoteId) },
                            onEdit = { route = Route.Edit(it.remoteId) },
                            onShare = { route = Route.Share(it.remoteId) },
                            onInspectSignals = { route = Route.Analyzer(it) },
                            onBack = { route = Route.Home },
                        )
                    }

                    is Route.Edit -> {
                        val dev = model.devices.firstOrNull { it.remoteId == r.remoteId }
                        if (dev != null) {
                            EditDeviceScreen(
                                device = dev,
                                model = model,
                                onDone = { route = Route.Home; toast.show(tr(R.string.menu_remote_updated)) },
                                onBack = { route = Route.Home },
                            )
                        }
                    }

                    is Route.Share -> {
                        val dev = model.devices.firstOrNull { it.remoteId == r.remoteId }
                        if (dev != null) {
                            ShareScreen(
                                device = dev,
                                repo = repo,
                                onBack = { route = Route.Home },
                            )
                        }
                    }

                    is Route.Sweep -> SweepScreen(repo, ir) { route = Route.Home }
                    is Route.SelfTest -> SelfTestScreen(ir) { route = Route.Home }
                    is Route.Builder -> BuilderScreen(
                        repo = repo,
                        model = model,
                        toast = toast,
                        onSaved = { route = Route.Pad(it) },
                        onBack = { route = Route.Home },
                    )
                    is Route.Macros -> MacrosScreen(
                        repo = repo,
                        transmitter = ir,
                        devices = model.devices,
                        model = macroModel,
                        onBack = { route = Route.Home },
                    )
                    is Route.DbHealth -> DbHealthScreen(
                        repo = repo,
                        onBack = { route = Route.Home },
                    )
                    is Route.Analyzer -> AnalyzerPickerScreen(
                        repo = repo,
                        devices = model.devices,
                        remoteId = r.remoteId,
                        onPick = { s -> route = Route.SignalAnalyzer(s.buttonName, s.carrierHz, s.pattern, s.protocol) },
                        onBack = { route = Route.Home },
                    )
                    is Route.SignalAnalyzer -> {
                        BackHandler { route = Route.Home }
                        SignalAnalyzerScreen(
                            buttonName = r.buttonName,
                            carrierHz = r.carrierHz,
                            pattern = r.pattern,
                            protocol = r.protocol,
                            transmitter = ir,
                            onBack = { route = Route.Home },
                        )
                    }
                    is Route.RecentSends -> RecentSendsScreen(
                        model = sendLogModel,
                        transmitter = ir,
                        toast = toast,
                        onBack = { route = Route.Home },
                    )
                    is Route.TileTarget -> TileTargetScreen(
                        devices = model.devices,
                        repo = repo,
                        toast = toast,
                        onBack = { route = Route.Home },
                    )
                }
            }
        }

        ToastHost(toast, Modifier.align(Alignment.BottomCenter).applyBottomInset())
        LaunchedEffect(route) { if (route is Route.Home) model.reload() }
        // Scenes mirror the macro store — one projection, no second editor.
        LaunchedEffect(macroModel.macros) {
            // Steps resolve by stable key, so the projection needs the index.
            favoritesModel.replaceScenes(
                macroModel.macros.map { sceneFromMacro(it, it.id, runCatching { repo?.remoteIndex() }.getOrNull()) },
            )
        }
        // What's-new modal (issue #52). Hosted here, not in a route, so it floats
        // over whatever screen the user is on when the release is detected.
        val whatsNew = whatsNewRelease
        val whatsNewAt = whatsNewVersion
        if (whatsNew != null && whatsNewAt != null) {
            WhatsNewDialog(
                version = whatsNewAt,
                notes = whatsNew.body.orEmpty(),
                onDismiss = {
                    WhatsNewStore.markSeen(whatsNewAt)
                    whatsNewRelease = null
                    whatsNewVersion = null
                },
            )
        }
    }
}

/**
 * Hamburger menu: tools + feedback toggle. Flat rows.
 */

/** The DB failed to load — factory guidance. */
@Composable
private fun MissingDb() {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            tr(R.string.db_not_loaded),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
@Composable
private fun MenuDrawer(
    onBuild: () -> Unit,
    acDevices: List<SavedDevice>,
    onOpenAc: (SavedDevice) -> Unit,
    hasDevices: Boolean,
    onMacros: () -> Unit,
    onSweep: () -> Unit,
    onSelfTest: () -> Unit,
    onAnalyzer: () -> Unit,
    onUpdateDb: () -> Unit,
    dbChangelog: String?,
    dbState: String?,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onMissingCode: () -> Unit,
    onDbHealth: () -> Unit,
    onCheckUpdate: () -> Unit,
    updateStateLabel: String?,
    onRecentSends: () -> Unit,
    onTileTarget: () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.width(300.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .applyTopInset()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(tr(R.string.menu), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(32.dp))

            // AC-category remotes jump straight to the climate pad.
            if (acDevices.isNotEmpty()) {
                SectionHead(tr(R.string.menu_air_conditioning))
                acDevices.forEach { dev ->
                    Row(
                        Modifier.fillMaxWidth().pressable { onOpenAc(dev) }.padding(vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LucideIcon(
                            LucideCategoryIcons.forCategory(dev.categorySlug), 22.dp,
                            MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            dev.name, style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
            }

            SectionHead(tr(R.string.menu_tools))
            DrawerRow(ActionIcon.Add, tr(R.string.menu_build_remote), onBuild)
            DrawerRow(ActionIcon.CameraTest, tr(R.string.menu_ir_self_test), onSelfTest)
            DrawerRow(ActionIcon.Macros, tr(R.string.menu_macros), onMacros)
            DrawerRow(ActionIcon.History, tr(R.string.menu_recent_sends), onRecentSends)
            DrawerRow(ActionIcon.Waveform, tr(R.string.menu_signal_analyzer), onAnalyzer)
            DrawerRow(ActionIcon.Sweep, "TV-B-Gone", onSweep)

            Spacer(Modifier.height(32.dp))
            SectionHead(tr(R.string.menu_database))
            DrawerRow(ActionIcon.Gauge, tr(R.string.menu_database_health), onDbHealth)
            DrawerRow(ActionIcon.Info, tr(R.string.menu_missing_code), onMissingCode)
            DrawerRow(ActionIcon.Database, tr(R.string.menu_update_code_database), onUpdateDb)
            if (dbChangelog != null) {
                Text(
                    dbChangelog,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (dbState != null) {
                Text(
                    dbState,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(32.dp))
            SectionHead(tr(R.string.menu_automation))
            val automationCtx = LocalContext.current
            DrawerToggle(
                tr(R.string.menu_allow_external_broadcasts),
                AutomationState.enabled,
            ) { AutomationState.setEnabled(automationCtx, it) }
            if (AutomationState.enabled) {
                val clipboard = LocalClipboardManager.current
                if (AutomationState.token.isEmpty()) {
                    DrawerRow(ActionIcon.Info, tr(R.string.menu_set_broadcast_token)) {
                        AutomationState.setToken(automationCtx, AutomationState.newToken())
                        clipboard.setText(AnnotatedString(AutomationState.token))
                    }
                    Text(
                        tr(R.string.menu_token_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    DrawerRow(ActionIcon.Info, tr(R.string.menu_rotate_token)) {
                        AutomationState.setToken(automationCtx, AutomationState.newToken())
                        clipboard.setText(AnnotatedString(AutomationState.token))
                    }
                    Text(
                        tr(R.string.menu_token_prefix, AutomationState.token) + "\n" +
                            "adb shell am broadcast -a ${ExternalCommand.ACTION} \\\n" +
                            "  --es remote_name \"TV\" --es button power --es token ${AutomationState.token}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            SectionHead(tr(R.string.menu_transmitter))
            TransmitterPickerRow()

            Spacer(Modifier.height(32.dp))
            SectionHead(tr(R.string.menu_backup))
            DrawerRow(ActionIcon.Share, tr(R.string.menu_export_backup), onExport)
            DrawerRow(ActionIcon.Down, tr(R.string.menu_import_backup), onImport)

            Spacer(Modifier.height(32.dp))
            SectionHead(tr(R.string.menu_appearance))
            DrawerToggle(tr(R.string.menu_dark_mode), ThemeState.isDark, ThemeState::toggleDark)
            DrawerToggle(tr(R.string.menu_animations), Feedback.animationsOn, Feedback::setAnimations)
            LanguageRow()

            Spacer(Modifier.height(32.dp))
            SectionHead(tr(R.string.menu_settings))
            DrawerToggle(
                tr(R.string.menu_resume_last_remote),
                effectiveResumeEnabled(ResumeState.explicit, hasDevices),
                ResumeState::setEnabled,
            )
            DrawerRow(ActionIcon.QrScan, tr(R.string.menu_tile_target), onTileTarget)
            DrawerRow(ActionIcon.Down, tr(R.string.menu_check_for_app_update), onCheckUpdate)
            if (updateStateLabel != null) {
                Text(
                    updateStateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionHead(tr(R.string.menu_feedback))
            DrawerToggle(tr(R.string.menu_haptics), Feedback.hapticsOn, Feedback::setHaptics)

            Spacer(Modifier.height(32.dp))
            SectionHead(tr(R.string.menu_rocker_repeat))
            DrawerToggle(tr(R.string.menu_hold_vol_ch_to_repeat), Feedback.rockerRepeatOn, Feedback::setRockerRepeat)
            if (Feedback.rockerRepeatOn) {
                RepeatSettingRow(
                    label = tr(R.string.menu_hold_delay),
                    valueMs = Feedback.rockerRepeatHoldMs,
                    steps = RepeatSettings.HOLD_STEPS,
                    slowMs = RepeatSettings.MAX_HOLD_MS,
                    fastMs = RepeatSettings.MIN_HOLD_MS,
                    onChange = Feedback::setRockerRepeatHold,
                )
                RepeatSettingRow(
                    label = tr(R.string.menu_repeat_interval),
                    valueMs = Feedback.rockerRepeatIntervalMs,
                    steps = RepeatSettings.INTERVAL_STEPS,
                    slowMs = RepeatSettings.MAX_INTERVAL_MS,
                    fastMs = RepeatSettings.MIN_INTERVAL_MS,
                    onChange = Feedback::setRockerRepeatInterval,
                )
            }
        }
    }
}

@Composable
private fun RepeatSettingRow(
    label: String,
    valueMs: Int,
    steps: List<Int>,
    slowMs: Int,
    fastMs: Int,
    onChange: (Int) -> Unit,
) {
    val view = LocalView.current
    Row(
        Modifier
            .fillMaxWidth()
            .pressable {
                Feedback.tap(view)
                onChange(RepeatSettings.nextStep(valueMs, steps))
            }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            tr(R.string.menu_pace, valueMs, Copy.pace(RepeatSettings.paceLabel(valueMs, slowMs, fastMs))),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        ActionIconView(ActionIcon.ChevRight, 16.dp, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DrawerRow(icon: ActionIcon, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick).padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIconView(icon, 22.dp, MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(16.dp))
        // Issue #69: a drawer row is text — let a large scale wrap it rather
        // than clip against the sheet edge.
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (FontScale.allowWrap(LocalDensity.current.fontScale)) 2 else 1,
            overflow = TextOverflow.Ellipsis)
    }
}

/** In-app language: cycles English ↔ فارسی, recreates the activity so RTL/type re-derive. */
@Composable
private fun LanguageRow() {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().pressable {
            setLanguage(if (isFa()) "en" else "fa")
            (ctx as? Activity)?.recreate()
        }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tr(R.string.menu_language),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (isFa()) tr(R.string.lang_farsi) else tr(R.string.lang_english),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        ActionIconView(ActionIcon.ChevRight, 16.dp, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DrawerToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val toggleView = LocalView.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { Feedback.tap(toggleView); onChange(it) },
            colors = SwitchDefaults.colors(
                // Dark thumb on dim accent track — thumb stays visible when on.
                checkedThumbColor = com.erfanbagheri.controlix.ui.theme.Ink,
                checkedTrackColor = com.erfanbagheri.controlix.ui.theme.Accent,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

/**
 * Transmitter picker: flat row, tap cycles Internal > USB dongle > BLE
 * blaster, persisted in SharedPreferences (TransmitterSelection).
 */
@Composable
private fun TransmitterPickerRow() {
    val choice = TransmitterSelection.choice
    Row(
        Modifier
            .fillMaxWidth()
            .pressable {
                val entries = TransmitterChoice.entries
                TransmitterSelection.select(entries[(choice.ordinal + 1) % entries.size])
            }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tr(R.string.menu_transmitter),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            Copy.transmitter(choice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        ActionIconView(ActionIcon.ChevRight, 16.dp, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
