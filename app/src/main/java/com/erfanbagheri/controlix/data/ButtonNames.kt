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
    fun norm(s: String): String =
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

    // ── Non-TV families (fans, purifiers, humidifiers, heaters, …) ────────
    // ponytail: these are label heuristics validated against the DB's own
    // vocabulary (see docs/DB.md). Upgrade path: per-brand metadata.

    fun speedUp(n: String): Boolean {
        val x = norm(n)
        return (x.contains("speed") || x.contains("fan")) &&
            (x.contains("up") || x.contains("+") || x.contains("faster") || x.contains("higher"))
    }
    fun speedDown(n: String): Boolean {
        val x = norm(n)
        return (x.contains("speed") || x.contains("fan")) &&
            (x.contains("down") || x.contains("dn") || x.contains("-") || x.contains("slower") || x.contains("lower"))
    }
    /** A discrete speed/fan level key (SPEED 1, FAN HIGH, LOW, MEDIUM, …). */
    fun speedLevel(n: String): Boolean {
        val x = norm(n)
        if (x == "speed" || x == "fan" || x == "fanonly") return false
        if (x.startsWith("speed") || x.startsWith("fan")) {
            val tail = x.removePrefix("speed").removePrefix("fan").removePrefix("speed_").removePrefix("fan_").trim()
            val levelWords = setOf("low", "med", "medium", "mid", "high", "max", "turbo", "quiet",
                "slow", "normal", "1", "2", "3", "4", "5", "6", "7", "8", "9")
            return tail in levelWords || tail.all { it.isDigit() }
        }
        return false
    }
    /**
     * A mode key (Mode, Auto, Turbo, Sleep, Night, Eco, Quiet, Natural, …),
     * plus compound mode+duration labels like "Heat_5m" or "Dry_1h": a mode
     * word at the front followed by digits and an optional unit.
     */
    fun modeKey(n: String): Boolean {
        val x = norm(n)
        if (x == "mode" || x.startsWith("mode")) return true
        if (x in setOf("auto", "turbo", "sleep", "night", "eco", "quiet", "natural", "wind",
                "ionizer", "plasma", "purify", "dry", "cool", "heat", "warm", "vent",
                "fanonly", "breeze", "nightmode", "moon", "snow", "freeze")) {
            return true
        }
        // mode prefix + a duration tail: Heat_5m, Cool_30, Vent_1h.
        val m = compoundTail(n) ?: return false
        return setOf("heat", "dry", "vent", "cool", "warm", "auto", "fan", "night", "snow", "freeze")
            .any { m.first.startsWith(it) }
    }

    /**
     * A duration tail on a compound label, e.g. 5m / 30 / 1h / 3h in
     * "Heat_5m", or null when the label has none.
     */
    internal fun compoundTail(n: String): Pair<String, String>? {
        val i = n.indexOfAny(charArrayOf('_', '-'))
        if (i <= 0) return null
        val tail = n.substring(i + 1).trim()
        if (tail.isEmpty() || tail.length > 8) return null
        val digits = tail.filter { it.isDigit() }
        val unit = tail.filterNot { it.isDigit() }.lowercase()
        // Digit-less tails are only "on"/"off" ("Vent_On"): a mode toggle, not a duration.
        if (digits.isEmpty() && unit !in setOf("on", "off")) return null
        if (unit != "" && unit !in setOf("m", "min", "mins", "minute", "h", "hr", "hrs", "hour", "hours", "s", "sec", "on", "off")) return null
        return n.substring(0, i).lowercase() to tail
    }

    /**
     * The mode half of a compound label — "heat" for "Heat_5m", "vent" for
     * "Vent_On" — or null when the label is not compound.
     *
     * The pad uses this to group a fan's 22 mode keys by what they actually
     * do: Heat / Vent / Dry / HeatDry each get their own row instead of one
     * 22-chip wall. Without it every duration variant is a separate-looking
     * control and the row is unreadable.
     *
     * ponytail: grouping is a string split on the first separator, so a brand
     * that writes "5mHeat" groups under a junk head. Upgrade path: per-brand
     * button maps carrying the real (mode, duration) pair.
     */
    fun modeHead(n: String): String? = compoundTail(n)?.first

    /**
     * The duration half of a compound label — "5m" for "Heat_5m", "On" for
     * "Vent_On" — or null when the label is not compound. The pad shows this
     * as the chip text once the head has been promoted to the row label, so
     * a grouped row reads "Heat: 5m 15m 30m 1h" instead of four "Heat_5m"s.
     */
    fun modeTail(n: String): String? = compoundTail(n)?.second

    /** An oscillation / swing / rotation / louver key. */
    fun oscillate(n: String): Boolean {
        val x = norm(n)
        return x.contains("osc") || x.contains("rotat") || x.contains("swing") ||
            x.contains("louver") || x.contains("turn") || x.contains("wide") ||
            x.contains("vertical") || x.contains("horizontal")
    }
    /** A timer key. */
    fun timerKey(n: String): Boolean {
        val x = norm(n)
        return x.contains("timer") || x == "time" || x.startsWith("hour") || x.contains("ontimer") ||
            x.contains("offtimer")
    }
    /** A light / lamp key. */
    fun lightKey(n: String): Boolean {
        val x = norm(n)
        return x.contains("light") || x.contains("lamp") || x.contains("mood")
    }
    /** A temperature step up (+) key. */
    fun tempUp(n: String): Boolean {
        val x = norm(n)
        return (x.contains("temp") || x.contains("temperature")) &&
            (x.contains("up") || x.contains("+") || x.contains("plus"))
    }
    /** A temperature step down (−) key. */
    fun tempDown(n: String): Boolean {
        val x = norm(n)
        return (x.contains("temp") || x.contains("temperature")) &&
            (x.contains("down") || x.contains("dn") || x.contains("-") || x.contains("minus"))
    }
    /** A color key (lighting). Multi-word names ("Ice Blue", "Dark Green") match
     *  on their last word; "R"/"G"/"B"/"W" single letters count. */
    fun colorKey(n: String): Boolean {
        val words = norm(n).split(' ', '_').filter { it.isNotEmpty() }
        return words.any { it in COLORS } && words.size <= 3
    }

    private val COLORS = setOf(
        "red", "green", "blue", "white", "yellow", "orange", "purple", "pink", "cyan",
        "lime", "amber", "violet", "indigo", "teal", "aqua", "navy", "rainbow", "warm",
        "cool", "neutral", "cozy", "pastel", "r", "g", "b", "w", "c", "y", "o", "p",
    )
    /** An effect key (lighting). */
    fun effectKey(n: String): Boolean {
        val x = norm(n)
        return x in setOf("flash", "fade", "strobe", "smooth", "jump", "quick", "wave",
            "blink", "sparkle", "glow")
    }
    /** A brightness key. */
    fun brightnessKey(n: String): Boolean {
        val x = norm(n)
        return x.contains("bright") || x.startsWith("dim") || x.contains("darker")
    }
    /** A humidity / mist key. */
    fun humidityKey(n: String): Boolean {
        val x = norm(n)
        return x.contains("hum") || x.contains("mist") || x.contains("vapor")
    }
    /** A cleaning / dock / spot key (vacuums). */
    fun cleaningKey(n: String): Boolean {
        val x = norm(n)
        return x.contains("clean") || x.contains("spot") || x.contains("edge") ||
            x.contains("suction") || x.contains("spiral") || x.contains("zigzag") ||
            x.contains("square") || x.contains("zone")
    }
    /** A dock / home / return key (vacuums). */
    fun dockKey(n: String): Boolean {
        val x = norm(n)
        return x == "home" || x == "dock" || x.contains("recharge") || x.contains("return")
    }
    /** A start / stop / pause key (vacuums). */
    fun startStopKey(n: String): Boolean {
        val x = norm(n)
        return x.contains("start") || x.contains("pause") || x == "stop" || x.contains("stopstart")
    }
    /** An audio source / input key. */
    fun audioSourceKey(n: String): Boolean {
        val x = norm(n)
        return x.contains("source") || x.contains("input") || x.contains("bluetooth") ||
            x == "bt" || x.contains("optical") || x.contains("opt") || x.contains("hdmi") ||
            x == "aux" || x.contains("usb") || x == "tv" || x == "pc" || x == "fm" ||
            x.contains("cd") || x.contains("phono") || x.contains("dvd") || x.contains("sat")
    }
    /** An audio sound-mode key. */
    fun audioModeKey(n: String): Boolean {
        val x = norm(n)
        return x in setOf("music", "movie", "cinema", "standard", "surround", "night",
            "game", "stereo", "theater", "news", "speech", "voice", "nightmode", "sports",
            "pop", "rock", "jazz", "classical", "flat", "user")
    }
    /** A bass / treble key. */
    fun bassTrebleKey(n: String): Boolean {
        val x = norm(n)
        return x.contains("bass") || x.contains("treble") || x.contains("eq") ||
            x.contains("equalizer") || x.contains("sub")
    }
    /** A transport key (playback). */
    fun transportKey(n: String): Boolean {
        val x = norm(n)
        return x.contains("play") || x == "stop" || x.contains("pause") || x == "next" ||
            x == "prev" || x.contains("previous") || x.contains("rewind") ||
            x.contains("fastforward") || x.contains("forward") || x.contains("record")
    }
    /** A freeze / blank / aspect key (projectors). */
    fun projectorKey(n: String): Boolean {
        val x = norm(n)
        return x.contains("freeze") || x.contains("blank") || x.contains("aspect") ||
            x.contains("keystone") || x.contains("zoom") || x.contains("focus")
    }
    /** A lock / child-lock key. */
    fun lockKey(n: String): Boolean = norm(n).contains("lock")
}
