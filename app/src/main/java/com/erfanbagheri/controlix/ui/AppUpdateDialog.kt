package com.erfanbagheri.controlix.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.R
import com.erfanbagheri.controlix.data.ReleaseInfo
import com.erfanbagheri.controlix.data.WhatsNewStore
import com.erfanbagheri.controlix.tr
import com.erfanbagheri.controlix.ui.theme.Accent

/**
 * "A newer release exists" modal (issue #51 follow-up). The drawer row only
 * writes a status line; this is the one dialog that can act on the result.
 * Offers the release page in the browser, never a silent in-app download.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateDialog(
    release: ReleaseInfo,
    currentVersion: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(0.dp), // flat styling per Studio Dark design guidelines
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionIconView(ActionIcon.Down, 24.dp, Accent)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = tr(R.string.update_modal_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = tr(R.string.update_modal_body, release.tagName, currentVersion),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // Release notes, same cleanup as the what's-new modal so the
                    // two never disagree on how GitHub markdown renders.
                    val notes = WhatsNewStore.cleanReleaseBody(release.body, "")
                    if (notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(0.dp),
                    ) {
                        Text(
                            text = tr(R.string.update_not_now),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)),
                                )
                            }.onFailure { onDismiss() }
                            onDismiss()
                        },
                        shape = RoundedCornerShape(0.dp),
                    ) {
                        Text(
                            text = tr(R.string.update_open_github),
                            style = MaterialTheme.typography.labelLarge,
                            color = Accent,
                        )
                    }
                }
            }
        }
    }
}