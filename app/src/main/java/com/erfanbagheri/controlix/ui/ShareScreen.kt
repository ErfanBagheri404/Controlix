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
import com.erfanbagheri.controlix.ui.theme.PaperFaint

/**
 * Share screen: device name + QR code encoding the remote data
 * (format: "controlix://remote/{id}/{name}/{brand}/{catSlug}").
 * Users can point another phone's camera at it to "scan" the remote.
 */
@Composable
fun ShareScreen(
    device: SavedDevice,
    onBack: () -> Unit,
) {
    val payload = "controlix://remote/${device.remoteId}/${device.name}/${device.brand}/${device.categorySlug}"
    val bitmap = rememberQrBitmap(payload, sizeDp = 240)

    Column(
        Modifier.fillMaxSize().applyTopInset().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        BackTitleRow("Share remote", onBack)
        Spacer(Modifier.height(36.dp))
        Text(device.name, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(device.brand, style = MaterialTheme.typography.labelLarge, color = PaperFaint)
        Spacer(Modifier.height(28.dp))
        bitmap?.let { bmp ->
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "QR code for ${device.name}",
                modifier = Modifier.size(240.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Scan with another Controlix app to import this remote.",
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
