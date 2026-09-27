package com.erfanbagheri.controlix.feature

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Issue #53 — the switch that lets external apps fire the blaster.
 *
 * Off by default: [ExternalReceiver] is exported (Tasker, Home Assistant and
 * `adb` all need that), so the preference — not the manifest — is the
 * trust boundary. Same prefs shape as Feedback/ResumeState/OrientationState.
 */
object AutomationState {
    private const val PREFS = "controlix_automation"
    private const val KEY = "external_broadcasts"
    private const val TOKEN_KEY = "broadcast_token"

    /** Drawer-facing mirror of the stored value; [init] loads it. */
    var enabled by mutableStateOf(false)
        private set

    /**
     * Issue #95: mirror of the stored token. A Compose mirror (not `remember`)
     * so setting or rotating it recomposes the drawer on its own.
     */
    var token by mutableStateOf("")
        private set

    fun init(ctx: Context) {
        enabled = isEnabled(ctx)
        token = readToken(ctx)
    }

    /** The receiver's gate. Reads prefs directly, so a cold process works. */
    fun isEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, false)

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, value).apply()
    }

    /** The expected shared secret; empty = pre-#95 open mode. */
    fun token(context: Context): String = readToken(context)

    fun setToken(context: Context, value: String) {
        token = value
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(TOKEN_KEY, value).apply()
    }

    /** A fresh 32-char token from the platform CSPRNG, not `random()`. */
    fun newToken(): String = java.util.UUID.randomUUID().toString().replace("-", "")

    private fun readToken(context: Context): String =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(TOKEN_KEY, "") ?: ""
}
