package com.erfanbagheri.controlix.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erfanbagheri.controlix.data.SendLog
import com.erfanbagheri.controlix.data.SentEntry
import com.erfanbagheri.controlix.ir.IrTransmitter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.erfanbagheri.controlix.tr
import com.erfanbagheri.controlix.locale
import com.erfanbagheri.controlix.R
import com.erfanbagheri.controlix.data.Copy

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM").withLocale(locale())
private val TIME = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Recent sends (issue #68): what the blaster fired, newest first, grouped by
 * day. A row re-fires its exact code through the same transmit path the pad
 * uses; a "nothing sent" row carries the reason and cannot be re-fired —
 * there is no code behind it.
 */
@Composable
fun RecentSendsScreen(
    model: SendLogModel,
    transmitter: IrTransmitter,
    toast: ToastState,
    onBack: () -> Unit,
) {
    val entries = model.entries
    val days = SendLog.groupByDay(entries)

    fun refire(e: SentEntry) {
        val result = transmitter.transmitButtonResult(e.carrierHz, e.pattern)
        model.record(
            if (result is SendResult.Sent) {
                SentEntry(e.remoteName, e.buttonName, e.carrierHz, e.pattern, System.currentTimeMillis())
            } else {
                SendLog.nothingSent(e.remoteName, e.buttonName, sendFailureReason(result), System.currentTimeMillis())
            },
        )
        toast.show(
            when (result) {
                SendResult.Sent -> tr(R.string.resent_toast, e.buttonName)
                SendResult.NoHardware -> tr(R.string.toast_no_ir_blaster)
                is SendResult.Failed -> tr(R.string.toast_couldnt_send)
            },
        )
    }

    Column(Modifier.fillMaxSize().applyTopInset().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(14.dp))
        BackRow(onBack)
        Spacer(Modifier.height(8.dp))
        Text(tr(R.string.recent_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            tr(R.string.recent_intro, SendLog.MAX),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        if (days.isEmpty()) {
            Text(
                tr(R.string.recent_nothing),
                Modifier.padding(top = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                days.forEach { day ->
                    item(key = "d:${day.date}") {
                        SectionHead(day.date.format(DAY))
                    }
                    items(day.entries, key = { "${day.date}:${it.epochMs}:${it.buttonName}" }) { e ->
                        SendRow(e, if (e.pattern.isEmpty()) null else ::refire)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                    }
                }
                item(key = "tail") { Spacer(Modifier.height((ScreenChrome.BOTTOM_SPACE_DP + 16).dp)) }
            }
        }
    }
}

/** Flat row: what fired + when on the left, why not on the failure line. */
@Composable
private fun SendRow(entry: SentEntry, refire: ((SentEntry) -> Unit)?) {
    val failed = entry.failed
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .let { m -> if (refire != null) m.clickable { refire(entry) } else m }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    tr(R.string.recent_entry, entry.remoteName, entry.buttonName),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (failed != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (failed != null) {
                        tr(R.string.recent_failed, Copy.sendFailure(failed))
                    } else {
                        tr(R.string.recent_signal, entry.carrierHz, entry.pattern.size)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (failed != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                Instant.ofEpochMilli(entry.epochMs).atZone(ZoneId.systemDefault()).toLocalDateTime().format(TIME),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (refire != null) {
                Spacer(Modifier.width(8.dp))
                ActionIconView(
                    ActionIcon.Play, 20.dp, MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}
