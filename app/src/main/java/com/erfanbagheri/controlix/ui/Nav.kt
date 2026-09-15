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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.platform.LocalView
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.theme.Ink
import com.erfanbagheri.controlix.ui.theme.PaperDim
import kotlinx.coroutines.launch

/** Every screen. Sealed route list, no nav library — app is 4 levels deep max. */
private sealed interface Route {
    data object Home : Route
    data object AddDevice : Route
    data class Ritual(val brandId: Int, val brandName: String, val catSlug: String, val catName: String) : Route
    data class Pad(val remoteId: Int) : Route
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

    Box(Modifier.fillMaxSize().background(Ink)) {
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
                        devices = model.devices.map {
                            DeviceEntry(it.remoteId, it.name, it.buttonCount, it.categorySlug)
                        },
                        codeCount = remember { runCatching { repo.buttonCount() }.getOrElse { 450325 } },
                        onOpenMenu = { Feedback.tap(view); scope.launch { drawerState.open() } },
                        onAddDevice = { route = Route.AddDevice },
                        onOpenDevice = { route = Route.Pad(it) },
                        onRemoveDevice = { model.remove(it) },
                    )

                    is Route.AddDevice -> AddDeviceScreen(
                        repo = repo,
                        onPick = { brandId, brandName, catSlug, catName ->
                            route = Route.Ritual(brandId, brandName, catSlug, catName)
                        },
                        onBack = { route = Route.Home },
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
                            repo = repo,
                            transmitter = ir,
                            remoteId = r.remoteId,
                            deviceName = saved?.name,
                            onRename = { model.rename(r.remoteId, it) },
                            onBack = { route = Route.Home },
                        )
                    }

                    is Route.Sweep -> SweepScreen(repo, ir) { route = Route.Home }

                    is Route.SelfTest -> SelfTestScreen(ir) { route = Route.Home }

                    is Route.Macros -> MacrosScreen(
                        repo = repo,
                        transmitter = ir,
                        devices = model.devices.map {
                            DeviceEntry(it.remoteId, it.name, it.buttonCount, it.categorySlug)
                        },
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
 * Hamburger menu: the tools section moved off the deck + the haptics
 * toggle. Flat rows, hairline separators stay out — spacing does the work.
 */
@Composable
private fun MenuDrawer(
    onMacros: () -> Unit,
    onSweep: () -> Unit,
    onSelfTest: () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = Ink,
        modifier = Modifier.width(300.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(56.dp))
            Text("Menu", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(32.dp))

            SectionHead("Tools")
            DrawerRow(ActionIcon.Macros, "Macros", onMacros)
            DrawerRow(ActionIcon.Sweep, "Power-off sweep", onSweep)
            DrawerRow(ActionIcon.CameraTest, "IR self-test", onSelfTest)

            Spacer(Modifier.height(32.dp))
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
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DrawerToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val toggleView = LocalView.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = {
                Feedback.tap(toggleView)
                onChange(it)
            },
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = Ink,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedThumbColor = PaperDim,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

@Composable
private fun MissingDb() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "The code database didn't load. Reinstall the app — the bundled DB ships inside the APK.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(28.dp),
        )
    }
}
