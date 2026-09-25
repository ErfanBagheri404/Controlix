package com.erfanbagheri.controlix.data

import java.util.Locale

/** Pure current-vs-new count diff into human changelog lines. */
object DbChangelog {
    fun format(currentRemotes: Int, currentButtons: Int, newRemotes: Int, newButtons: Int): String =
        "${signed(newRemotes - currentRemotes)} remotes, ${signed(newButtons - currentButtons)} buttons"

    private fun signed(delta: Int): String = String.format(Locale.US, "%+,d", delta)
}
