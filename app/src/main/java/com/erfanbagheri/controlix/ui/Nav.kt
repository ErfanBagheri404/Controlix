package com.erfanbagheri.controlix.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlixNav(ir: IrTransmitter, repo: IrCodeRepository?, coldStart: Boolean = true) {
    val model = rememberDeviceModel(repo)
    val macroModel = rememberMacroModel()
    val favoritesModel = rememberFavoritesModel()
    val ctx = LocalContext.current
    val copiedModel = rememberCopiedButtonModel()
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
        val json = BackupCodec.encode(model.devices, macroModel.macros)
        runCatching {
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: error("Backup stream unavailable")
            output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }.onSuccess { toast.show("Backup exported") }
            .onFailure { toast.show("Couldn't write the backup file") }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (json == null) {
            toast.show("Couldn't read that file")
            return@rememberLauncherForActivityResult
        }
        when (val result = BackupCodec.decode(json)) {
            is BackupDecodeResult.Error -> toast.show(result.message)
            is BackupDecodeResult.Success -> {
                val backup = result.backup
                toast.show(
                    "Replace with ${backup.devices.size} devices, ${backup.macros.size} macros?",
                    actionLabel = "Import",
                ) {
                    model.replaceAll(backup.devices)
                    macroModel.save(backup.macros)
                    toast.show("Backup restored")
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
                        DbChangelog.format(dbCounts.first, dbCounts.second, it.manifest.remoteCount, it.manifest.buttonCount)
                    },
                    dbState = when (val s = refreshState) {
                        RefreshState.Idle -> null
                        is RefreshState.Checked -> "Update available"
                        is RefreshState.Downloading -> "Downloading…"
                        is RefreshState.Verified -> "Verifying…"
                        is RefreshState.Applying -> "Applying…"
                        is RefreshState.Done -> "Database updated — restart to load it"
                        is RefreshState.RolledBack -> "Rolled back: ${s.reason} — old database kept"
                        is RefreshState.Failed -> s.reason
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
                        UpdateCheckState.Checking -> "Checking…"
                        is UpdateCheckState.UpToDate -> "Up to date (${s.currentVersion})"
                        is UpdateCheckState.UpdateAvailable -> "${s.release.tagName} available"
                        is UpdateCheckState.Failed -> s.reason
                    },
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
                            // controlix://remote/{id}/{name}/{brand}/{catSlug}
                            val m = Regex("""controlix://remote/(\d+)/([^/]*)/([^/]*)/([^/]*)""").find(text)
                            if (m != null && repo != null) {
                                val (id, name, brand, slug) = m.destructured
                                val rid = id.toIntOrNull() ?: -1
                                if (repo.buttons(rid).isNotEmpty()) {
                                    val decodedName = java.net.URLDecoder.decode(name, "UTF-8").ifBlank { brand }
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
                            toast.show("${r.brandName} added")
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
                        // The tile fires this remote's power code.
                        LaunchedEffect(r.remoteId) { TileStore(ctx).rememberRemoteId(r.remoteId) }
                        // AC remotes are stateful: their pad is a separate climate
                        // layout. Every other category keeps the normal TV pad.
                        if (saved?.isAc() == true) {
                            AcPadScreen(
                                deviceName = saved.name,
                                remoteId = r.remoteId,
                                repo = repo,
                                transmitter = ir,
                                toast = toast,
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
                                onDone = { route = Route.Home; toast.show("Remote updated") },
                                onBack = { route = Route.Home },
                            )
                        }
                    }

                    is Route.Share -> {
                        val dev = model.devices.firstOrNull { it.remoteId == r.remoteId }
                        if (dev != null) {
                            ShareScreen(
                                device = dev,
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
            "The code database didn't load. Reinstall the app — the bundled DB ships inside the APK.",
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
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.width(300.dp),
    ) {
        Column(Modifier.fillMaxSize().applyTopInset().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(16.dp))
            Text("Menu", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(32.dp))

            // AC-category remotes jump straight to the climate pad.
            if (acDevices.isNotEmpty()) {
                SectionHead("Air conditioning")
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

            SectionHead("Tools")
            DrawerRow(ActionIcon.Add, "Build remote", onBuild)
            DrawerRow(ActionIcon.Macros, "Macros", onMacros)
            DrawerRow(ActionIcon.Sweep, "TV-B-Gone", onSweep)
            DrawerRow(ActionIcon.CameraTest, "IR self-test", onSelfTest)
            DrawerRow(ActionIcon.Waveform, "Signal analyzer", onAnalyzer)
            DrawerRow(ActionIcon.Database, "Update code database", onUpdateDb)
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
            DrawerRow(ActionIcon.Gauge, "Database health", onDbHealth)

            Spacer(Modifier.height(32.dp))
            SectionHead("Settings")
            DrawerToggle(
                "Resume last remote",
                effectiveResumeEnabled(ResumeState.explicit, hasDevices),
                ResumeState::setEnabled,
            )
            DrawerRow(ActionIcon.Down, "Check for app update", onCheckUpdate)
            if (updateStateLabel != null) {
                Text(
                    updateStateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(32.dp))
            SectionHead("Automation")
            val automationCtx = LocalContext.current
            DrawerToggle(
                "Allow external broadcasts",
                AutomationState.enabled,
            ) { AutomationState.setEnabled(automationCtx, it) }
            if (AutomationState.enabled) {
                Text(
                    "Tasker / Home Assistant can fire codes:\n" +
                        "adb shell am broadcast -a ${ExternalCommand.ACTION} \\\n" +
                        "  --es remote_name \"TV\" --es button power",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(32.dp))
            SectionHead("Transmitter")
            TransmitterPickerRow()

            Spacer(Modifier.height(32.dp))
            SectionHead("Backup")
            DrawerRow(ActionIcon.Share, "Export backup", onExport)
            DrawerRow(ActionIcon.Down, "Import backup", onImport)

            SectionHead("Contribute")
            DrawerRow(ActionIcon.Info, "Missing a code?", onMissingCode)

            Spacer(Modifier.height(32.dp))
            SectionHead("Appearance")
            DrawerToggle("Dark mode", ThemeState.isDark, ThemeState::toggleDark)
            DrawerToggle("Animations", Feedback.animationsOn, Feedback::setAnimations)
            OrientationChoice()

            Spacer(Modifier.height(24.dp))
            SectionHead("Feedback")
            DrawerToggle("Haptics", Feedback.hapticsOn, Feedback::setHaptics)

            Spacer(Modifier.height(32.dp))
            SectionHead("Rocker repeat")
            DrawerToggle("Hold VOL/CH to repeat", Feedback.rockerRepeatOn, Feedback::setRockerRepeat)
            if (Feedback.rockerRepeatOn) {
                RepeatSettingRow(
                    label = "Hold delay",
                    valueMs = Feedback.rockerRepeatHoldMs,
                    steps = RepeatSettings.HOLD_STEPS,
                    slowMs = RepeatSettings.MAX_HOLD_MS,
                    fastMs = RepeatSettings.MIN_HOLD_MS,
                    onChange = Feedback::setRockerRepeatHold,
                )
                RepeatSettingRow(
                    label = "Repeat interval",
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
            "$valueMs ms · ${RepeatSettings.paceLabel(valueMs, slowMs, fastMs)}",
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
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Screen-orientation choice. Flat rows, hairline separators, no stock toggle:
 * three labelled options cycle on tap; the current one is the accent.
 */
@Composable
private fun OrientationChoice() {
    val view = LocalView.current
    val activity = view.context as? Activity
    Text(
        "Orientation",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, top = 18.dp, bottom = 4.dp),
    )
    Orientation.Mode.entries.forEach { option ->
        val selected = OrientationState.mode == option
        Row(
            Modifier
                .fillMaxWidth()
                .pressable {
                    Feedback.tap(view)
                    activity?.requestedOrientation = OrientationState.set(option)
                }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                Orientation.label(option),
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) com.erfanbagheri.controlix.ui.theme.Accent
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                ActionIconView(ActionIcon.Check, 20.dp, com.erfanbagheri.controlix.ui.theme.Accent)
            }
        }
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
            "Transmitter",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            choice.display,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        ActionIconView(ActionIcon.ChevRight, 16.dp, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
