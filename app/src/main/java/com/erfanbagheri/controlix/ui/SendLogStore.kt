package com.erfanbagheri.controlix.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.erfanbagheri.controlix.data.SendLog
import com.erfanbagheri.controlix.data.SentEntry

/** Why nothing went out — the reason recorded on a failed send. */
fun sendFailureReason(result: SendResult): String = when (result) {
    SendResult.NoHardware -> "no IR blaster on this device"
    is SendResult.Failed -> result.reason
    SendResult.Sent -> "not sent"
}

/**
 * Recent sends (issue #68), same SharedPreferences shape as
 * DeviceStore/CopiedButtonStore. One bounded string, pure format in
 * [SendLog]; this class is the thin Android persistence layer.
 */
class SendLogStore(context: Context) {
    private val prefs = context.getSharedPreferences("send_log", Context.MODE_PRIVATE)

    fun load(): List<SentEntry> =
        SendLog.decodeList(prefs.getString("entries", "") ?: "")

    private fun save(list: List<SentEntry>) {
        prefs.edit().putString("entries", SendLog.encodeList(list)).apply()
    }

    fun record(entry: SentEntry) = save(SendLog.append(load(), entry))

    fun recordAll(entries: List<SentEntry>) {
        if (entries.isEmpty()) return
        save(entries.fold(load(), SendLog::append))
    }

    fun clear() {
        prefs.edit().remove("entries").apply()
    }
}

class SendLogModel(private val store: SendLogStore) {
    var entries: List<SentEntry> by mutableStateOf(store.load())
        private set

    fun record(e: SentEntry) {
        store.record(e)
        entries = store.load()
    }

    fun clear() {
        store.clear()
        entries = emptyList()
    }

    fun reload() {
        entries = store.load()
    }
}

@Composable
fun rememberSendLogModel(context: Context = LocalContext.current): SendLogModel {
    val model = remember { SendLogModel(SendLogStore(context)) }
    return model
}
