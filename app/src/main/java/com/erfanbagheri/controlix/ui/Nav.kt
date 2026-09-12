package com.erfanbagheri.controlix.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.theme.Ink
import com.erfanbagheri.controlix.ui.theme.Paper
import androidx.compose.material3.Text

/** Every screen the app can show. One sealed route list, no nav library. */
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
    val context = LocalContext.current
    val model = rememberDeviceModel(context)
    var route: Route by remember { mutableStateOf(Route.Home) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        if (repo == null) {
            MissingDb()
            return@Box
        }

        // Cross-fade + slide: forward pushes in from the right, back from left.
        androidx.compose.animation.AnimatedContent(
            targetState = route,
            transitionSpec = {
                val forward = when {
                    initialState is Route.Home && targetState !is Route.Home -> true
                    initialState is Route.AddDevice && targetState is Route.Ritual -> true
                    else -> false
                }
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(280)) { it / 4 * dir } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(280)) { -it / 6 * dir } + fadeOut(tween(160)))
            },
            label = "route",
        ) { r ->
            when (r) {
                is Route.Home -> HomeScreen(
                    devices = model.devices,
                    repo = repo,
                    onAddDevice = { route = Route.AddDevice },
                    onOpenDevice = { route = Route.Pad(it.remoteId) },
                    onSweep = { route = Route.Sweep },
                    onSelfTest = { route = Route.SelfTest },
                )

                is Route.AddDevice -> AddDeviceScreen(
                    repo = repo,
                    onPick = { catSlug, brandId, brandName, catName ->
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
                                brand = r.catName,
                                categorySlug = r.catSlug,
                                glyph = glyphFor(r.catSlug),
                                buttonCount = repo.buttons(remoteId).size,
                            )
                        )
                        route = Route.Pad(remoteId)
                    },
                    onBack = { route = Route.Home },
                )

                is Route.Pad -> PadScreen(
                    repo = repo,
                    transmitter = ir,
                    remoteId = r.remoteId,
                    onBack = { route = Route.Home },
                )

                is Route.Sweep -> SweepScreen(
                    repo = repo,
                    transmitter = ir,
                    onBack = { route = Route.Home },
                )

                is Route.SelfTest -> SelfTestScreen(
                    transmitter = ir,
                    onBack = { route = Route.Home },
                )
            }
        }

        // Keep the persisted list fresh whenever we land back home.
        LaunchedEffect(route) {
            if (route is Route.Home) model.reload()
        }
    }
}

@Composable
private fun MissingDb() {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(
            "The code database didn't load. Reinstall the app — the bundled DB ships inside the APK.",
            style = MaterialTheme.typography.bodyLarge,
            color = Paper,
            modifier = Modifier.padding(28.dp),
        )
    }
}

private infix fun androidx.compose.animation.EnterTransition.togetherWith(
    exit: androidx.compose.animation.ExitTransition
) = androidx.compose.animation.ContentTransform(this, exit)

private typealias AnimatedContentTransitionScopeAlias<T> = AnimatedContentTransitionScope<T>