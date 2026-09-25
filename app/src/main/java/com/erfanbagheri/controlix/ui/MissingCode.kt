package com.erfanbagheri.controlix.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.erfanbagheri.controlix.data.Contribution
import com.erfanbagheri.controlix.data.ContributionBundle
import com.erfanbagheri.controlix.data.ContributionField
import com.erfanbagheri.controlix.data.ContributionRejection
import java.io.File

/**
 * "Missing a code?" — one flat form, live typed validation, explicit share.
 *
 * Offline-first contract: nothing is uploaded from here. The share sheet is
 * user-initiated; the app itself has no INTERNET permission. Controlix
 * transmits only, so it cannot learn a code — the pattern is pasted from a
 * source the user can name, and unknown or proprietary provenance is refused.
 */
@Composable
fun MissingCodeScreen(
    initialBrand: String = "",
    initialCategory: String = "",
    initialRemote: String = "",
    initialButton: String = "",
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var brand by remember { mutableStateOf(initialBrand) }
    var category by remember { mutableStateOf(initialCategory) }
    var remote by remember { mutableStateOf(initialRemote) }
    var button by remember { mutableStateOf(initialButton) }
    var carrier by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var provenance by remember { mutableStateOf("") }
    // No BuildConfig: buildFeatures.buildConfig stays off, one string isn't worth it.
    var version by remember { mutableStateOf(appVersionName(context)) }
    var showAll by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }

    val draft = Contribution(
        brand = brand.trim(),
        category = category.trim(),
        remoteName = remote.trim(),
        buttonName = button.trim(),
        carrierHz = carrier.trim().toIntOrNull() ?: 0,
        pattern = pattern.trim(),
        provenance = provenance.trim(),
        appVersion = version.trim(),
    )
    val reasons = remember(draft) { draft.validate() }
    fun reason(field: ContributionField): ContributionRejection? = reasons.firstOrNull { it.field == field }

    Column(Modifier.fillMaxSize().applyTopInset().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(14.dp))
        BackTitleRow("Missing a code?", onBack)
        Spacer(Modifier.height(12.dp))
        Text(
            "Paste the code, name its source. Sharing is manual — Controlix never uploads anything itself.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            FlatField("Brand", brand, { brand = it }, reason(ContributionField.BRAND), showAll, "Sony")
            FlatField("Category", category, { category = it }, reason(ContributionField.CATEGORY), showAll, "tvs")
            FlatField("Remote name / model", remote, { remote = it }, reason(ContributionField.REMOTE_NAME), showAll, "RM-ED009")
            FlatField("Button name", button, { button = it }, reason(ContributionField.BUTTON_NAME), showAll, "Volume up")
            FlatField(
                "Carrier Hz", carrier, { carrier = it.filter(Char::isDigit) },
                reason(ContributionField.CARRIER), showAll, "38000", numeric = true,
            )
            FlatField(
                "Pattern (microseconds)", pattern, { pattern = it },
                reason(ContributionField.PATTERN), showAll,
                "900 451 560 451 1680 560",
            )
            FlatField(
                "Source / provenance", provenance, { provenance = it },
                reason(ContributionField.PROVENANCE), showAll,
                "Public irdb dump, retested on my own remote",
            )
            Text(
                "Controlix cannot learn codes — its IR hardware only transmits. Only name a source you can " +
                    "vouch for; codes with unknown or proprietary provenance are refused.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlatField("App version", version, { version = it }, reason(ContributionField.APP_VERSION), showAll, null)
            Spacer(Modifier.height(20.dp))
        }

        BigPressButton("Share with maintainer", ActionIcon.Share) {
            if (reasons.isNotEmpty()) {
                showAll = true
                shareError = "Fix the ${reasons.size} flagged ${if (reasons.size == 1) "field" else "fields"} first."
            } else {
                val bundle = ContributionBundle(listOf(draft))
                shareError = if (shareContribution(context, bundle)) null
                else "No app available to share. Copy the JSON manually."
            }
        }
        shareError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Flat labelled row, hairline above each field, typed reason beneath. */
@Composable
private fun FlatField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    rejection: ContributionRejection?,
    showReason: Boolean,
    placeholder: String?,
    numeric: Boolean = false,
) {
    val show = rejection != null && (showReason || value.isNotBlank())
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            isError = show,
            singleLine = true,
            shape = RoundedCornerShape(0.dp),
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(),
        )
        if (show) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Top) {
                Text(
                    rejection.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Installed version string; PackageManager is local, no network, no BuildConfig. */
private fun appVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
}.getOrDefault("unknown")

/**
 * Writes the bundle to app cache and hands it to the system share sheet as
 * a .json attachment plus a prefilled body. No network, no repo API, no token.
 */
private fun shareContribution(context: Context, bundle: ContributionBundle): Boolean {
    val json = bundle.toJson()
    val dir = File(context.cacheDir, "contributions").apply { mkdirs() }
    val file = File(dir, ContributionBundle.FILE_NAME)
    return runCatching {
        file.writeText(json)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val first = bundle.buttons.first()
        val body = "Missing IR code for ${first.brand} ${first.remoteName} · ${first.buttonName} " +
            "(${first.carrierHz} Hz), sent from Controlix ${first.appVersion}. " +
            "Source: ${first.provenance}. JSON attached — carrier plus full pattern, no proprietary data."
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Missing IR code: ${first.brand} ${first.remoteName}")
            putExtra(Intent.EXTRA_TEXT, body)
            clipData = ClipData.newRawUri(ContributionBundle.FILE_NAME, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share contribution"))
        true
    }.getOrDefault(false)
}
