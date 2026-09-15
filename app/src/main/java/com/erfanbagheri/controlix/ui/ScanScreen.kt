package com.erfanbagheri.controlix.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.erfanbagheri.controlix.ui.theme.Accent
import com.erfanbagheri.controlix.ui.theme.PaperFaint
import com.google.zxing.BarcodeFormat
import com.google.zxing.integration.android.IntentIntegrator
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory

/**
 * QR scanner: point at another Controlix share screen. Both phones ship the
 * same bundled DB, so a scanned remote id is enough — the full button set
 * lives locally already.
 */
@Composable
fun ScanScreen(onResult: (String) -> Unit, onError: (String) -> Unit, onBack: () -> Unit) {
    var granted by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) granted = true else onError("Camera permission is needed to scan a remote.")
    }
    LaunchedEffect(Unit) { permission.launch(Manifest.permission.CAMERA) }

    var fired by remember { mutableStateOf(false) }
    var scanner: DecoratedBarcodeView? by remember { mutableStateOf(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    DecoratedBarcodeView(ctx).apply {
                        barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                        decodeContinuous { result ->
                            if (!fired) { fired = true; onResult(result.text) }
                        }
                    }.also { scanner = it }
                },
                onRelease = { scanner = null },
            )
        }
        // Corner brackets.
        Canvas(Modifier.fillMaxSize().padding(48.dp)) {
            val l = size.minDimension / 6f
            fun c(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(Accent, Offset(x, y), Offset(x + l * dx, y), 10f, cap = StrokeCap.Round)
                drawLine(Accent, Offset(x, y), Offset(x, y + l * dy), 10f, cap = StrokeCap.Round)
            }
            c(0f, 0f, 1f, 1f); c(size.width, 0f, -1f, 1f)
            c(0f, size.height, 1f, -1f); c(size.width, size.height, -1f, -1f)
        }
        Column(Modifier.align(Alignment.TopCenter).applyTopInset().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(12.dp))
            BackTitleRow("Scan remote", onBack)
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f)).padding(24.dp),
        ) {
            Text(
                "Point the camera at the QR code on another Controlix phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = PaperFaint,
            )
        }
    }

    DisposableEffect(scanner) {
        val v = scanner
        v?.resume()
        onDispose { v?.pause() }
    }
}
