package com.erfanbagheri.controlix.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.erfanbagheri.controlix.ui.theme.Accent
import com.erfanbagheri.controlix.ui.theme.PaperFaint
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory

/**
 * QR scanner: point at another Controlix share screen. Both phones ship the
 * same bundled DB, so a scanned remote id is enough — the full button set
 * lives locally already.
 *
 * The framing square + corner brackets are drawn by OUR canvas on a box WE
 * define (centered, 70% of min dimension), so brackets and square can never
 * drift apart. The [BarcodeView] behind it just fills the screen and decodes
 * the whole frame — no library viewfinder involved.
 */
@Composable
fun ScanScreen(onResult: (String) -> Unit, onError: (String) -> Unit, onBack: () -> Unit) {
    var granted by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) granted = true else onError("Camera permission is needed to scan a remote.")
    }
    LaunchedEffect(Unit) { permission.launch(Manifest.permission.CAMERA) }

    var fired by remember { mutableStateOf(false) }
    var scanner: BarcodeView? by remember { mutableStateOf(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    BarcodeView(ctx).apply {
                        decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                        decodeContinuous { result ->
                            if (!fired) { fired = true; onResult(result.text) }
                        }
                    }.also { scanner = it }
                },
                onRelease = { scanner = null },
            )
        }
        // Our own scan box + brackets, same geometry, one canvas.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val side = minOf(maxWidth, maxHeight) * 0.7f
            Canvas(Modifier.fillMaxSize()) {
                val s = side.toPx()
                val box = Rect(
                    (size.width - s) / 2f, (size.height - s) / 2f,
                    (size.width + s) / 2f, (size.height + s) / 2f,
                )
                // Dim everything outside the square.
                drawRect(Color.Black.copy(alpha = 0.55f))
                // The square itself: thin accent hairline.
                drawRect(Accent.copy(alpha = 0.9f), topLeft = box.topLeft, size = box.size, style = Stroke(2.dp.toPx()))
                // Corner brackets ON the square's corners.
                val l = s / 6f
                val w = (s * 0.02f).coerceAtLeast(8f)
                fun c(x: Float, y: Float, dx: Float, dy: Float) {
                    drawLine(Accent, Offset(x, y), Offset(x + l * dx, y), w, cap = StrokeCap.Round)
                    drawLine(Accent, Offset(x, y), Offset(x, y + l * dy), w, cap = StrokeCap.Round)
                }
                c(box.left, box.top, 1f, 1f); c(box.right, box.top, -1f, 1f)
                c(box.left, box.bottom, 1f, -1f); c(box.right, box.bottom, -1f, -1f)
            }
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
