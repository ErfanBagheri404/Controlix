package com.erfanbagheri.controlix.ui

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.theme.ControlixTheme
import com.erfanbagheri.controlix.ui.theme.ThemeState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before setContent: the initial route reads ResumeState during composition.
        ResumeState.init(this)
        setContent {
            val ctx = LocalContext.current
            val ir = remember { IrTransmitter(ctx) }
            val repo = remember {
                runCatching { IrCodeRepository(ctx) }
                    .onFailure { it.printStackTrace() }
                    .getOrNull()
            }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                Feedback.init(ctx); ThemeState.init(ctx); OrientationState.init(ctx)
            }
            androidx.compose.runtime.LaunchedEffect(OrientationState.mode) {
                (ctx as? Activity)?.requestedOrientation = Orientation.resolve(OrientationState.mode)
            }
            ControlixTheme {
                ControlixNav(ir = ir, repo = repo, coldStart = savedInstanceState == null)
            }
        }
    }
}