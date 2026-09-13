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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ctx = LocalContext.current
            val ir = remember { IrTransmitter(ctx) }
            val repo = remember {
                runCatching { IrCodeRepository(ctx) }
                    .onFailure { it.printStackTrace() }
                    .getOrNull()
            }
            ControlixTheme {
                ControlixNav(ir = ir, repo = repo)
            }
        }
    }
}