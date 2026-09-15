package com.erfanbagheri.controlix.ui

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
}

@Composable
fun ControlixNav(ir: IrTransmitter, repo: IrCodeRepository?) {
    val model = rememberDeviceModel()
    val macroModel = rememberMacroModel()
    var route: Route by remember { mutableStateOf(Route.Home) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (repo == null) {
            MissingDb()
            return@Box
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                MenuDrawer(
                    onMacros = { scope.launch { drawerState.close() }; route = Route.Macros },
                    onSweep = { scope.launch { drawerState.close() }; route = Route.Sweep },
                    onSelfTest = { scope.launch { drawerState.close() }; route = Route.SelfTest },
                )
            },
        ) {
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    val forward = targetState != Route.Home && initialState == Route.Home
                    val dir = if (forward) 1 else -1
                    val specIn = slideInHorizontally(tween(Motion.Route, easing = Motion.EaseOut)) { it / 5 * dir } +
                        fadeIn(tween(Motion.Route))
                    val specOut = slideOutHorizontally(tween(Motion.RouteExit, easing = Motion.EaseInOut)) { -it / 7 * dir } +
                        fadeOut(tween(120))
                    specIn togetherWith specOut
                },
                label = "route",
            ) { r ->
                when (r) {
                    is Route.Home -> HomeScreen(
                        model = model,
                        repo = repo,
                        transmitter = ir,
                        onOpenDevice = { route = Route.Pad(it.remoteId) },
                        onAddDevice = { route = Route.AddDevice },
                        onToggleDrawer = { Feedback.tap(view); scope.launch { drawerState.open() } },
                        onEdit = { route = Route.Edit(it.remoteId) },
                        onShare = { route = Route.Share(it.remoteId) },
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
                                    model.save(
                                        SavedDevice(
                                            remoteId = rid,
                                            name = java.net.URLDecoder.decode(name, "UTF-8").ifBlank { brand },
                                            brand = java.net.URLDecoder.decode(brand, "UTF-8"),
                                            categorySlug = slug,
                                            buttonCount = repo.buttons(rid).size,
                                        )
                                    )
                                    route = Route.Pad(rid)
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
                            model.save(
                                SavedDevice(
                                    remoteId = remoteId,
                                    name = r.brandName,
                                    brand = r.brandName,
                                    categorySlug = r.catSlug,
                                    buttonCount = repo.buttons(remoteId).size,
                                )
                            )
                            route = Route.Pad(remoteId)
                        },
                        onBack = { route = Route.Home },
                    )

                    is Route.Pad -> {
                        val saved = model.devices.firstOrNull { it.remoteId == r.remoteId }
                        PadScreen(
                            deviceName = saved?.name,
                            onRename = { model.rename(r.remoteId, it) },
                            remoteId = r.remoteId,
                            repo = repo,
                            transmitter = ir,
                            devices = model.devices,
                            onSwitchDevice = { route = Route.Pad(it.remoteId) },
                            onBack = { route = Route.Home },
                        )
                    }

                    is Route.Edit -> {
                        val dev = model.devices.firstOrNull { it.remoteId == r.remoteId }
                        if (dev != null) {
                            EditDeviceScreen(
                                device = dev,
                                model = model,
                                onDone = { route = Route.Home },
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
                }
            }
        }

        LaunchedEffect(route) { if (route is Route.Home) model.reload() }
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
    onMacros: () -> Unit,
    onSweep: () -> Unit,
    onSelfTest: () -> Unit,
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

            Spacer(Modifier.height(32.dp))
            SectionHead("Appearance")
            DrawerToggle("Dark mode", ThemeState.isDark, ThemeState::toggleDark)

            Spacer(Modifier.height(24.dp))
            SectionHead("Feedback")
            DrawerToggle("Haptics", Feedback.hapticsOn, Feedback::setHaptics)
        }
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
                checkedThumbColor = com.erfanbagheri.controlix.ui.theme.Accent,
                checkedTrackColor = com.erfanbagheri.controlix.ui.theme.Accent,
            ),
        )
    }
}
