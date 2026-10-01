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
import com.erfanbagheri.controlix.tr
import com.erfanbagheri.controlix.R
import com.erfanbagheri.controlix.data.Copy

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
    val reportStore = remember { MissingCodeReportStore(context) }
    val pending = remember { reportStore.pending() }
    var brand by remember { mutableStateOf(initialBrand.ifBlank { pending.firstOrNull()?.brand ?: "" }) }
    var category by remember { mutableStateOf(initialCategory.ifBlank { pending.firstOrNull()?.category ?: "" }) }
    var remote by remember { mutableStateOf(initialRemote.ifBlank { pending.firstOrNull()?.remote ?: "" }) }
    var button by remember { mutableStateOf(initialButton.ifBlank { pending.firstOrNull()?.button ?: "" }) }
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
        BackTitleRow(tr(R.string.menu_missing_code), onBack)
        Spacer(Modifier.height(12.dp))
        Text(
            tr(R.string.missing_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Issue #79: what the pad already collected. Prefilled fields are
        // visible above; this states where the report goes before it does.
        pending.firstOrNull()?.let { p ->
            Spacer(Modifier.height(12.dp))
            Text(
                tr(
                    R.string.missing_from_pad,
                    p.brand + if (p.remote.isNotBlank()) " ${p.remote}" else "",
                    if (p.wholeRemote) tr(R.string.missing_no_supported_keys)
                    else tr(R.string.missing_no_button_code, p.button),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(20.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            FlatField(tr(R.string.field_brand), brand, { brand = it }, reason(ContributionField.BRAND), showAll, "Sony")
            FlatField(tr(R.string.field_category), category, { category = it }, reason(ContributionField.CATEGORY), showAll, "tvs")
            FlatField(tr(R.string.field_remote_model), remote, { remote = it }, reason(ContributionField.REMOTE_NAME), showAll, "RM-ED009")
            FlatField(tr(R.string.field_button_name), button, { button = it }, reason(ContributionField.BUTTON_NAME), showAll, tr(R.string.widget_key_volume_up))
            FlatField(
                tr(R.string.field_carrier), carrier, { carrier = it.filter(Char::isDigit) },
                reason(ContributionField.CARRIER), showAll, "38000", numeric = true,
            )
            FlatField(
                tr(R.string.field_pattern), pattern, { pattern = it },
                reason(ContributionField.PATTERN), showAll,
                "900 451 560 451 1680 560",
            )
            FlatField(
                tr(R.string.field_source), provenance, { provenance = it },
                reason(ContributionField.PROVENANCE), showAll,
                tr(R.string.missing_prov_example),
            )
            Text(
                tr(R.string.missing_learn_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlatField(tr(R.string.field_app_version), version, { version = it }, reason(ContributionField.APP_VERSION), showAll, null)
            Spacer(Modifier.height(20.dp))
        }

        // Issue #79: nothing is shared until the user confirms the
        // destination. First tap arms the button, second tap shares.
        var armed by remember { mutableStateOf(false) }
        if (armed) {
            Spacer(Modifier.height(8.dp))
            Text(
                tr(R.string.missing_share_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BigPressButton(
            if (armed) tr(R.string.mc_confirm_share) else tr(R.string.mc_share_with),
            ActionIcon.Share,
        ) {
            if (reasons.isNotEmpty()) {
                showAll = true
                shareError = tr(R.string.missing_fix_flagged, if (reasons.size == 1) tr(R.string.missing_field_one) else tr(R.string.missing_field_many))
                return@BigPressButton
            }
            if (!armed) {
                armed = true
                return@BigPressButton
            }
            val bundle = ContributionBundle(listOf(draft))
            shareError = if (shareContribution(context, bundle)) null
            else tr(R.string.missing_no_app)
            if (shareError == null) reportStore.clear()
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
                    Copy.rejection(rejection),
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
        val body = tr(
            R.string.missing_share_body, first.brand, first.remoteName, first.buttonName,
            first.carrierHz, first.appVersion, first.provenance,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, tr(R.string.missing_share_subject, first.brand, first.remoteName))
            putExtra(Intent.EXTRA_TEXT, body)
            clipData = ClipData.newRawUri(ContributionBundle.FILE_NAME, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, tr(R.string.missing_share_contribution)))
        true
    }.getOrDefault(false)
}
