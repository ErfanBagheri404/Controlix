package com.erfanbagheri.controlix.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.erfanbagheri.controlix.data.CopiedButton
import com.erfanbagheri.controlix.data.CopiedButtons

/**
 * Copied-button store (issue #10), same SharedPreferences shape as
 * DeviceStore/MacroStore. One global clipboard (last copied button) plus a
 * per-remote local list, keyed `remote_{id}`. Pure format lives in
 * [CopiedButtons]; this class is the thin Android persistence layer.
 */
class CopiedButtonStore(context: Context) {
    private val prefs = context.getSharedPreferences("copied_buttons", Context.MODE_PRIVATE)

    fun clipboard(): CopiedButton? =
        CopiedButtons.decode(prefs.getString("clipboard", "") ?: "")

    fun copyToClipboard(button: CopiedButton) {
        prefs.edit().putString("clipboard", CopiedButtons.encode(button)).apply()
    }

    fun clearClipboard() {
        prefs.edit().remove("clipboard").apply()
    }

    fun local(remoteId: Int): List<CopiedButton> =
        CopiedButtons.decodeList(prefs.getString(key(remoteId), "") ?: "")

    /** Paste onto a remote's local list; returns the updated list. */
    fun paste(remoteId: Int, button: CopiedButton): List<CopiedButton> {
        val updated = CopiedButtons.addLocal(local(remoteId), button)
        prefs.edit().putString(key(remoteId), CopiedButtons.encodeList(updated)).apply()
        return updated
    }

    fun remove(remoteId: Int, name: String) {
        val updated = CopiedButtons.removeLocal(local(remoteId), name)
        prefs.edit().putString(key(remoteId), CopiedButtons.encodeList(updated)).apply()
    }

    private fun key(remoteId: Int): String = "remote_$remoteId"
}

class CopiedButtonModel(private val store: CopiedButtonStore) {
    var clipboard: CopiedButton? by mutableStateOf(store.clipboard())
        private set

    fun copyToClipboard(button: CopiedButton) {
        store.copyToClipboard(button)
        clipboard = store.clipboard()
    }

    fun local(remoteId: Int): List<CopiedButton> = store.local(remoteId)

    fun paste(remoteId: Int, button: CopiedButton): List<CopiedButton> {
        val updated = store.paste(remoteId, button)
        return updated
    }

    fun remove(remoteId: Int, name: String) = store.remove(remoteId, name)
}

@Composable
fun rememberCopiedButtonModel(context: Context = LocalContext.current): CopiedButtonModel {
    val model = remember { CopiedButtonModel(CopiedButtonStore(context)) }
    return model
}
