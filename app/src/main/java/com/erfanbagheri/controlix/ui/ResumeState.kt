package com.erfanbagheri.controlix.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Issue #4 — resume the last used remote on launch.
 * Same prefs shape as Feedback/ThemeState. `explicit` is null until the user
 * touches the toggle, so the default can follow "has a device" instead of a
 * value baked in at first run.
 */
/** null = never toggled → on for returning users (any saved device). */
fun effectiveResumeEnabled(explicit: Boolean?, hasDevices: Boolean): Boolean =
    explicit ?: hasDevices

/** The pad to open on cold start, or null for Home. */
fun resumeRestoreTarget(enabled: Boolean, lastRemoteId: Int, enabledDeviceIds: Set<Int>): Int? =
    lastRemoteId.takeIf { enabled && it in enabledDeviceIds }

object ResumeState {
    private const val PREFS = "controlix_resume"
    private var prefs: SharedPreferences? = null

    /** null = user never toggled it. */
    var explicit by mutableStateOf<Boolean?>(null)
        private set

    var lastRemoteId by mutableStateOf(-1)
        private set

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        explicit = prefs?.takeIf { it.contains("resume") }?.getBoolean("resume", false)
        lastRemoteId = prefs?.getInt("lastRemote", -1) ?: -1
    }

    fun setEnabled(v: Boolean) {
        explicit = v
        prefs?.edit()?.putBoolean("resume", v)?.apply()
    }

    fun recordLastRemote(remoteId: Int) {
        lastRemoteId = remoteId
        prefs?.edit()?.putInt("lastRemote", remoteId)?.apply()
    }
}
