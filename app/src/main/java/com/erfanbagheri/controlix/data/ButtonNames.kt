package com.erfanbagheri.controlix.data

/**
 * Fuzzy button-name matching across the messy community vocabulary.
 * The DB carries 19,743 distinct labels: 'POWER', '0power', 'On_off',
 * 'Vol_up', 'TV_Vol+', '0vol_dn', 'CH+/Page+', 'channel -'...
 * Rules validated against the full label list; the +/- guard rejects
 * compound labels ('VOL+ / FAVOR.- SEL.', 'CH +/DOWN') that are ambiguous
 * between up and down — better a missing key than a wrong-sending one.
 *
 * Covers EVERY action the pad renders, not only power/volume/channel: the
 * borrow layer can only fill a key that has a predicate here.
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

    // ── D-pad ────────────────────────────────────────────────────────────
    // Guard: page/volume/channel "up" labels are different keys — route
    // them back to their own predicate instead of the d-pad.

    fun up(n: String): Boolean {
        val x = norm(n)
        return (x == "up" || x.endsWith("up")) &&
            !x.contains("page") && !x.contains("vol") && !x.contains("ch") && !x.contains('+')
    }

    fun down(n: String): Boolean {
        val x = norm(n)
        return (x == "down" || x.endsWith("down")) &&
            !x.contains("page") && !x.contains("vol") && !x.contains("ch")
    }

    fun left(n: String): Boolean = norm(n).let { it == "left" || it.endsWith("left") }

    fun right(n: String): Boolean = norm(n).let { it == "right" || it.endsWith("right") }

    fun ok(n: String): Boolean = norm(n) in setOf("ok", "okay", "enter", "select", "sel", "action")

    // ── App keys ─────────────────────────────────────────────────────────

    fun home(n: String): Boolean = norm(n).let { it == "home" || it == "tvhome" || it.endsWith("home") }

    fun back(n: String): Boolean = norm(n).let { it == "back" || it == "return" || it.endsWith("back") }

    fun exit(n: String): Boolean = norm(n) in setOf("exit", "quit")

    fun guide(n: String): Boolean = norm(n).let { it == "guide" || it == "epg" || it.endsWith("guide") }

    /** Settings/Setup/Options drive the same menu key on most TVs. */
    fun menu(n: String): Boolean = norm(n) in setOf("menu", "settings", "setup", "options", "option")

    /**
     * Deliberately NOT 'Display': on several TVs Display cycles inputs —
     * aliasing it here would send an input switch from the info key.
     */
    fun info(n: String): Boolean = norm(n).let { it == "info" || it.startsWith("info") }

    fun source(n: String): Boolean =
        norm(n).let { it.contains("input") || it.contains("source") || it == "tvav" }

    /** 'replay' contains 'play' but is a different key — reject it. */
    fun playPause(n: String): Boolean = norm(n).let { it.contains("play") && !it.contains("replay") }

    // ── Keypad ───────────────────────────────────────────────────────────

    /** A label for some digit key: trailing single digit, letters before it. */
    fun digit(n: String): Boolean {
        val x = norm(n)
        return x.isNotEmpty() && x.last().isDigit() && x.count { it.isDigit() } == 1 &&
            x.dropLast(1).all { it.isLetter() }
    }

    /** The label is specifically this digit ('KEY_5' yes, 'KEY_6' no). */
    fun digit(n: String, d: Char): Boolean = digit(n) && norm(n).last() == d

    // ── AC ─────────────────────────────────────────────────────────────
    // Stateful remotes carry one pattern per (mode, temperature) state:
    // "Cool_23", "Auto_25_slow". Some carry only a bare mode key ("Cool",
    // "FAN ONLY"), a power key ("Off"), fan-speed keys ("FAN HIGH",
    // "FanSlower") and swing. Anything the remote does not carry must stay
    // unsent — the pad says so instead of approximating a code.
    // ponytail: Fahrenheit labels ("68f", "68_degrees", "Cool_68") and
    // temp-first labels ("TEMP_23", "16c") are not matched, and a bare
    // "POWER" toggle is never treated as an off key — guessing either would
    // transmit the wrong state. Upgrade path: per-brand unit metadata.

    private val acModes = listOf("cool", "heat", "dry", "fan", "auto")

    /** True off key: bare "Off" or a power+off compound — never plain "Power". */
    fun powerOff(n: String): Boolean {
        val x = norm(n)
        return x == "off" || x == "acoff" || x.contains("poweroff") ||
            (x.contains("power") && x.contains("off"))
    }

    /** Exact (mode, Celsius temp) state, e.g. acCombo("Cool_23", "Cool", 23). */
    fun acCombo(n: String, modeLabel: String, tempC: Int): Boolean =
        acComboTemp(n, modeLabel) == tempC

    /** A bare mode key ("Cool", "FAN ONLY") — no state, no temperature. */
    fun acModeStandalone(n: String, modeLabel: String): Boolean {
        val x = norm(n)
        val mode = modeLabel.lowercase()
        if (x == mode) return true
        return mode == "fan" && x == "fanonly"
    }

    /** This label names the mode at all: a state or a bare mode key. */
    fun acMode(n: String, modeLabel: String): Boolean {
        val x = norm(n)
        return acComboTemp(x, modeLabel) != null || acModeStandalone(x, modeLabel)
    }

    /** Celsius state temperature carried by a combo label, else null. */
    fun acComboTemp(n: String, modeLabel: String): Int? {
        val x = norm(n)
        val mode = modeLabel.lowercase()
        if (!x.startsWith(mode)) return null
        val digits = x.removePrefix(mode).takeWhile { it.isDigit() }
        if (digits.length != 2) return null
        val t = digits.toInt()
        return if (t in 10..32) t else null
    }

    /** A fan-speed / fan-step key ("FAN HIGH", "FanSlower", "Speed2"). */
    fun acFanSpeed(n: String): Boolean {
        val x = norm(n)
        if (x == "fan" || x == "fanonly") return false // mode keys, not speeds
        if (acModes.any { acComboTemp(x, it) != null }) return false
        return x.startsWith("fan") || x.startsWith("speed") ||
            x.contains("fanspeed") || x.contains("airspeed")
    }

    /** A swing / louver key. */
    fun acSwing(n: String): Boolean = norm(n).contains("swing")
}
