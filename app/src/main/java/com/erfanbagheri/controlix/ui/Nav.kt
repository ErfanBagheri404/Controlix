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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.theme.Ink

/** Every screen. Sealed route list, no nav library — app is 4 levels deep max. */
private sealed interface Route {
    data object Home : Route
    data object AddDevice : Route
    data class Ritual(val brandId: Int, val brandName: String, val catSlug: String, val catName: String) : Route
    data class Pad(val remoteId: Int) : Route
    data object Sweep : Route
    data object SelfTest : Route
}

@Composable
fun ControlixNav(ir: IrTransmitter, repo: IrCodeRepository?) {
    val model = rememberDeviceModel()
    var route: Route by remember { mutableStateOf(Route.Home) }

    Box(Modifier.fillMaxSize().background(Ink)) {
        if (repo == null) {
            MissingDb()
            return@Box
        }

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
                    codeCount = remember { runCatching { repo.buttonCount() }.getOrElse { 244601 } },
                    onOpenDevice = { route = Route.Pad(it) },
                    onAddDevice = { route = Route.AddDevice },
                    onSweep = { route = Route.Sweep },
                    onSelfTest = { route = Route.SelfTest },
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

                is Route.Pad -> PadScreen(repo, ir, r.remoteId) { route = Route.Home }

                is Route.Sweep -> SweepScreen(repo, ir) { route = Route.Home }

                is Route.SelfTest -> SelfTestScreen(ir) { route = Route.Home }
            }
        }

        LaunchedEffect(route) { if (route is Route.Home) model.reload() }
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
