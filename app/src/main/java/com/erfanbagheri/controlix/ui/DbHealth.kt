package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.DbHealth
import com.erfanbagheri.controlix.data.IrCodeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Health(
    val summary: List<DbHealth.Row>,
    val unusable: List<DbHealth.Row>,
    val empty: List<DbHealth.Row>,
    val protocols: List<DbHealth.Row>,
)

/**
 * Read-only audit of the bundled DB: totals, empty remotes, unusable
 * patterns, extra keys, protocols. Counts and explanations only — rows are
 * never deleted or hidden here. Queries run off the main thread; results
 * are read once per repository.
 */
@Composable
fun DbHealthScreen(
    repo: IrCodeRepository,
    onBack: () -> Unit,
) {
    var health by remember { mutableStateOf<Health?>(null) }

    LaunchedEffect(repo) {
        health = withContext(Dispatchers.IO) {
            val totals = repo.totals()
            val sources = repo.sourceCounts()
            val empties = repo.emptyRemotes()
            val unusable = repo.unusableButtons()
            val names = repo.buttonNameCounts()
            val protocols = repo.protocolCounts()
            Health(
                summary = DbHealth.report(totals, sources, empties.size, unusable.size, names, protocols),
                unusable = DbHealth.unusableRows(unusable),
                empty = DbHealth.emptyRows(empties),
                protocols = DbHealth.protocolRows(protocols),
            )
        }
    }

    Column(Modifier.fillMaxSize().applyTopInset().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(14.dp))
        BackRow(onBack)
        Spacer(Modifier.height(8.dp))
        Text("Database health", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Read-only audit of the bundled code database. Every row below is reported as-is — nothing is deleted or hidden.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        val h = health
        if (h == null) {
            Text(
                "Reading database…",
                Modifier.padding(top = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(h.summary, key = { "s:${it.title}" }) { HealthRow(it) }
                item(key = "head-unusable") { SectionHead("Unusable codes") }
                items(h.unusable, key = { "u:${it.title}:${it.value}" }) { HealthRow(it) }
                item(key = "head-empty") { SectionHead("Empty remotes") }
                items(h.empty.size, key = { "e:$it" }) { i -> HealthRow(h.empty[i]) }
                item(key = "head-protocols") { SectionHead("Protocols present") }
                items(h.protocols, key = { "p:${it.title}" }) { HealthRow(it) }
                item(key = "tail") { Spacer(Modifier.height((ScreenChrome.BOTTOM_SPACE_DP + 16).dp)) }
            }
        }
    }
}

/** One flat line: label + why on the left, count on the right, hairline below. */
@Composable
private fun HealthRow(row: DbHealth.Row) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (row.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                row.hint?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(
                row.value,
                style = MaterialTheme.typography.titleMedium,
                color = if (row.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
    }
}
