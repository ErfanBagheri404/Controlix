package com.erfanbagheri.controlix.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.l10nApply
import com.erfanbagheri.controlix.feature.AutomationState
import com.erfanbagheri.controlix.ir.IrTransmitter
import com.erfanbagheri.controlix.ui.theme.ControlixTheme
import com.erfanbagheri.controlix.ui.theme.ThemeState

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(l10nApply(newBase))
    }

    /**
     * Hardware VOLUME_UP / VOLUME_DOWN drive the on-screen remote while a pad
     * is composed (PadScreen binds VolumeKeys); everywhere else the keys keep
     * their system behaviour. Consuming both ACTION_DOWN and its release is
     * what keeps the system volume slider from sliding in.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isDown = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (isUp || isDown) {
            when (event.action) {
                KeyEvent.ACTION_DOWN ->
                    if (VolumeKeys.press(isUp, event.repeatCount == 0)) return true
                KeyEvent.ACTION_UP ->
                    if (VolumeKeys.release(isUp)) return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

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
                AutomationState.init(ctx)
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