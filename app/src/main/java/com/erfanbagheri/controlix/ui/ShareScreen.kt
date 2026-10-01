package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.erfanbagheri.controlix.data.IrCodeRepository
import com.erfanbagheri.controlix.data.RemoteShareCodec
import com.erfanbagheri.controlix.ui.theme.PaperFaint
import com.erfanbagheri.controlix.tr
import com.erfanbagheri.controlix.R

/**
 * Share screen: device name + a QR code that carries the remote's **buttons**,
 * so the receiving phone can import it even with a different (or older) code
 * database — see [RemoteShareCodec] (issue #56).
 *
 * The previous format was a bare remote id, which only worked when both phones
 * shipped the exact same database.
 */
@Composable
fun ShareScreen(
    device: SavedDevice,
    repo: IrCodeRepository?,
    onBack: () -> Unit,
) {
    // Read the buttons once; this must not run on every recomposition.
    val payload = remember(device.remoteId, device.key) {
        val buttons = runCatching { repo?.buttons(device.remoteId) }.getOrNull().orEmpty()
        if (buttons.isEmpty()) {
            // Nothing to carry (custom remote): fall back to the id form so the
            // code still scans on a phone that happens to have the same DB.
            legacyShareUri(device.remoteId, device.name, device.brand, device.categorySlug)
        } else {
            RemoteShareCodec.encode(
                brand = device.brand,
                model = device.name,
                categorySlug = device.categorySlug,
                buttons = buttons.map {
                    RemoteShareCodec.SharedButton(it.name, it.carrierHz, it.pattern)
                },
                remoteId = device.remoteId,
            )
        }
    }
    val bitmap = rememberQrBitmap(payload, sizeDp = 240)
    val buttonCount = remember(device.remoteId, device.key) {
        runCatching { repo?.buttons(device.remoteId) }.getOrNull()?.size ?: 0
    }

    Column(
        Modifier.fillMaxSize().applyTopInset().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        BackTitleRow(tr(R.string.screen_share), onBack)
        Spacer(Modifier.height(36.dp))
        Text(device.name, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(device.brand, style = MaterialTheme.typography.labelLarge, color = PaperFaint)
        Spacer(Modifier.height(28.dp))
        bitmap?.let { bmp ->
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = tr(R.string.share_qr_for, device.name),
                modifier = Modifier.size(240.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            if (buttonCount > 0) {
                tr(R.string.share_qr_desc_full, buttonCount)
            } else {
                tr(R.string.share_scan_desc)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun rememberQrBitmap(text: String, sizeDp: Int): android.graphics.Bitmap? {
    return remember(text, sizeDp) {
        runCatching {
            BarcodeEncoder().encodeBitmap(text, BarcodeFormat.QR_CODE, sizeDp, sizeDp)
        }.getOrNull()
    }
}
