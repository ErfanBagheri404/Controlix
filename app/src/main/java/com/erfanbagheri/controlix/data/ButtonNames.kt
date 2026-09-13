package com.erfanbagheri.controlix.data

/**
 * Fuzzy button-name matching across the messy community vocabulary.
 * The DB carries 19,743 distinct labels: 'POWER', '0power', 'On_off',
 * 'Vol_up', 'TV_Vol+', '0vol_dn', 'CH+/Page+', 'channel -'...
 * Rules validated against the full label list; the +/- guard rejects
 * compound labels ('VOL+ / FAVOR.- SEL.', 'CH +/DOWN') that are ambiguous
 * between up and down — better a missing key than a wrong-sending one.
 */
object ButtonNames {

    /** lowercase, keep alphanumerics and +/-. */
    private fun norm(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() || it == '+' || it == '-' }

    fun power(n: String): Boolean {
        val x = norm(n)
        return x == "power" || x.startsWith("power") || x.contains("onoff") ||
            x in setOf("on", "off", "standby") || x.contains("standby")
    }

    fun volUp(n: String): Boolean {
        val x = norm(n)
        return x.contains("vol") && !x.contains('-') && (x.contains('+') || x.contains("up"))
    }

    fun volDown(n: String): Boolean {
        val x = norm(n)
        return x.contains("vol") && !x.contains('+') &&
            (x.contains('-') || x.contains("down") || x.contains("dn") || x.contains("minus"))
    }

    fun mute(n: String): Boolean = norm(n).contains("mute")

    fun chUp(n: String): Boolean {
        val x = norm(n)
        return (x.contains("ch") || x.contains("channel")) && !x.contains('-') &&
            (x.contains('+') || x.contains("up"))
    }

    fun chDown(n: String): Boolean {
        val x = norm(n)
        return (x.contains("ch") || x.contains("channel")) && !x.contains('+') &&
            (x.contains('-') || x.contains("down") || x.contains("dn"))
    }
}
