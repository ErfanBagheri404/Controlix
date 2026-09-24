package com.erfanbagheri.controlix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.feature.sceneFromMacro
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.theme.ThemeState
import kotlinx.coroutines.launch

/** Every screen. Sealed route list, no nav library — app is 4 levels deep max. */
private sealed interface Route {
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
    data object DbHealth : Route
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlixNav(ir: IrTransmitter, repo: IrCodeRepository?, coldStart: Boolean = true) {
    val model = rememberDeviceModel(repo)
    val macroModel = rememberMacroModel()
    val favoritesModel = rememberFavoritesModel()
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

    fun openPad(remoteId: Int) {
        ResumeState.recordLastRemote(remoteId)
        route = Route.Pad(remoteId)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (repo == null) {
            MissingDb()
            return@Box
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                MenuDrawer(
                    hasDevices = model.devices.isNotEmpty(),
                    onMacros = { scope.launch { drawerState.close() }; route = Route.Macros },
                    onSweep = { scope.launch { drawerState.close() }; route = Route.Sweep },
                    onSelfTest = { scope.launch { drawerState.close() }; route = Route.SelfTest },
                    onDbHealth = { scope.launch { drawerState.close() }; route = Route.DbHealth },
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

                    is Route.Pad -> {
                        // System back from a cold-start-restored pad returns Home, never traps.
                        BackHandler { route = Route.Home }
                        val saved = model.devices.firstOrNull { it.remoteId == r.remoteId }
                        PadScreen(
                            deviceName = saved?.name,
                            onRename = { newName -> saved?.let { model.rename(it.key, newName) } },
                            remoteId = r.remoteId,
                            repo = repo,
                            transmitter = ir,
                            devices = model.devices,
                            model = model,
                            favoritesModel = favoritesModel,
                            toast = toast,
                            onSwitchDevice = { openPad(it.remoteId) },
                            onEdit = { route = Route.Edit(it.remoteId) },
                            onShare = { route = Route.Share(it.remoteId) },
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
                }
            }
        }

        ToastHost(toast, Modifier.align(Alignment.BottomCenter).applyBottomInset())
        LaunchedEffect(route) { if (route is Route.Home) model.reload() }
        // Scenes mirror the macro store — one projection, no second editor.
        LaunchedEffect(macroModel.macros) {
            favoritesModel.replaceScenes(macroModel.macros.map { sceneFromMacro(it, it.id) })
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
    hasDevices: Boolean,
    onMacros: () -> Unit,
    onSweep: () -> Unit,
    onSelfTest: () -> Unit,
    onDbHealth: () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.width(300.dp),
    ) {
        Column(Modifier.fillMaxSize().applyTopInset().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(16.dp))
            Text("Menu", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(32.dp))

            SectionHead("Tools")
            DrawerRow(ActionIcon.Macros, "Macros", onMacros)
            DrawerRow(ActionIcon.Sweep, "Power-off sweep", onSweep)
            DrawerRow(ActionIcon.CameraTest, "IR self-test", onSelfTest)
            DrawerRow(ActionIcon.Gauge, "Database health", onDbHealth)

            Spacer(Modifier.height(32.dp))
            SectionHead("Settings")
            DrawerToggle(
                "Resume last remote",
                effectiveResumeEnabled(ResumeState.explicit, hasDevices),
                ResumeState::setEnabled,
            )

            Spacer(Modifier.height(32.dp))
            SectionHead("Appearance")
            DrawerToggle("Dark mode", ThemeState.isDark, ThemeState::toggleDark)
            DrawerToggle("Animations", Feedback.animationsOn, Feedback::setAnimations)

            Spacer(Modifier.height(24.dp))
            SectionHead("Feedback")
            DrawerToggle("Haptics", Feedback.hapticsOn, Feedback::setHaptics)

            Spacer(Modifier.height(32.dp))
            SectionHead("Rocker repeat")
            DrawerToggle("Hold VOL/CH to repeat", Feedback.rockerRepeatOn, Feedback::setRockerRepeat)
            if (Feedback.rockerRepeatOn) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(120 to "Fast", 180 to "Standard", 300 to "Slow").forEach { (ms, label) ->
                        DrawerChoice(label, Feedback.rockerRepeatIntervalMs == ms) {
                            Feedback.setRockerRepeatInterval(ms)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) com.erfanbagheri.controlix.ui.theme.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .border(1.dp, if (selected) com.erfanbagheri.controlix.ui.theme.Accent else MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .pressable(onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
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
