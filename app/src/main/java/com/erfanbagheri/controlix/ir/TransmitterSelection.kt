package com.erfanbagheri.controlix.ir

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which transmitter the user picked, persisted in prefs and read reactively
 * by the drawer row.
 */
object TransmitterSelection {
    private const val PREFS = "controlix_transmitter"
    private var prefs: android.content.SharedPreferences? = null

    var choice by mutableStateOf(TransmitterChoice.Internal)
        private set

    // `load`, not `init`: Kotlin reserves `init` in class bodies.
    fun load(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        choice = prefs?.getString(PREFS, null)
            ?.let { name -> TransmitterChoice.entries.firstOrNull { it.name == name } }
            ?: TransmitterChoice.Internal
    }

    fun select(c: TransmitterChoice) {
        choice = c
        prefs?.edit()?.putString(PREFS, c.name)?.apply()
    }
}
