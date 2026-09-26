package com.erfanbagheri.controlix.data

/**
 * Pure fuzzy search + button-set filtering for finding a remote (issue #23).
 * No Android/SQLite types, so it is fully JVM-testable.
 *
 * ponytail: scoring is substring/prefix/subsequence only — no trigram index
 * or edit-distance table. Upgrade path: precompute trigrams offline if the
 * 20-model benchmark ever misses top-3.
 */
object RemoteSearch {

    /** Fold case, strip diacritics, keep letters/digits only. */
    fun normalize(s: String): String = split(s).joinToString("")

    /**
     * Brand/model token splitter: non-alphanumeric runs are separators, so
     * "Samsung UE55NU7100" -> ["samsung", "ue55nu7100"].
     */
    fun split(s: String): List<String> =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }

    /**
     * Stable score in [0,100] of [query] against [candidate].
     * 0 = no match. Exact 100 only on full equality.
     */
    fun score(candidate: String, query: String): Int {
        val q = normalize(query)
        if (q.isEmpty()) return 0
        val tokens = split(candidate)
        if (tokens.isEmpty()) return 0
        val c = tokens.joinToString("")
        if (c == q) return 100
        if (tokens.any { it == q }) return 95
        if (c.startsWith(q)) return 85
        if (c.contains(q)) {
            // Earlier matches are better; the first is a prefix (85 above).
            val pos = c.indexOf(q)
            return 70 - ((pos * 10) / (c.length + 1)).coerceAtMost(10)
        }
        // Acronym: "S X A M" -> "sxam" matches "sam".
        val acronym = tokens.joinToString("") { it.take(1) }
        if (isSubsequence(q, acronym)) return 40
        // Subsequence across the joined tokens (acronym / dropped chars).
        if (isSubsequence(q, c)) return 40
        // Single typo: one substitution, insertion, deletion, transposition.
        return if (q.length >= 3 && withinOneEdit(q, c)) 30 else 0
    }

    private fun withinOneEdit(q: String, c: String): Boolean {
        if (kotlin.math.abs(q.length - c.length) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < q.length && j < c.length) {
            if (q[i] == c[j]) {
                i++; j++
            } else {
                if (edits == 1) return false
                edits++
                when {
                    q.length == c.length -> {
                        // Substitution, or transposition of adjacent chars.
                        if (i + 1 < q.length && j + 1 < c.length &&
                            q[i] == c[j + 1] && q[i + 1] == c[j]
                        ) {
                            i += 2; j += 2
                        } else {
                            i++; j++
                        }
                    }
                    q.length < c.length -> j++ // insertion in query
                    else -> i++ // deletion from query
                }
            }
        }
        // Trailing char counts as the one edit.
        return edits + (q.length - i) + (c.length - j) <= 1
    }

    private fun isSubsequence(q: String, c: String): Boolean {
        var i = 0
        for (ch in c) if (i < q.length && ch == q[i]) i++
        return i == q.length && q.length >= 2
    }

    /**
     * Function words the parser understands → canonical ButtonNames keys.
     *
     * Keys are pre-normalized the same way as the query word ([wordKey]): fold
     * case, drop everything but letters and digits. So "vol+" arrives as "vol",
     * "ch+" as "ch" and "tv/av" as "tvav".
     *
     * The DB holds 19,743 distinct labels written by three different communities
     * (`POWER_OFF`, `STANDBY`, `Standby`, `pwr`). [ButtonNames] already resolves
     * that label side — this table only has to recognize the *query* side, so a
     * user typing "standby" or "pwr" is understood as asking for power.
     */
    private val FUNCTION_WORDS: Map<String, String> = buildMap {
        fun alias(key: String, vararg words: String) = words.forEach { put(it, key) }

        alias("power", "power", "pwr", "standby", "onoff", "poweron", "sleep")
        alias("power_off", "poweroff", "off")
        alias("volume", "vol", "volume")
        alias("channel", "ch", "channel")
        alias("mute", "mute", "muting", "soundmute", "muteon", "silence")
        alias("play_pause", "play", "pause", "playpause")
        alias("up", "up")
        alias("down", "down")
        alias("left", "left")
        alias("right", "right")
        alias("ok", "ok", "okay", "enter", "select", "sel", "confirm")
        alias("home", "home")
        alias("back", "back", "return", "prev", "previous")
        alias("exit", "exit", "quit")
        alias("guide", "guide", "epg")
        alias("menu", "menu", "settings", "setup", "options")
        alias("info", "info")
        alias("source", "source", "input", "av", "ext", "hdmi", "inputselect", "tvav")
        // Directional forms must be registered AFTER their symmetric parents,
        // so a signed "vol+" never collapses to the plain "vol" key.
        alias("vol_up", "volup", "volumeup", "vup")
        alias("vol_down", "voldown", "volumedown", "vdown")
        alias("ch_up", "chup", "channelup", "pageup")
        alias("ch_down", "chdown", "channeldown", "pagedown")
    }

    /** Query-word key: fold case, keep only letters and digits. */
    private fun wordKey(w: String): String = w.lowercase().filter { it.isLetterOrDigit() }

    /** A parsed user query: which name tokens to match, which functions required. */
    data class Query(val nameTokens: List<String>, val functions: Set<String>)

    /** Split a raw query into name tokens and required button-function keys. */
    fun parse(raw: String): Query {
        val words = raw.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val functions = LinkedHashSet<String>()
        val names = ArrayList<String>(words.size)
        for (w in words) {
            // Match on the normalized key but keep the original word as a name
            // token when it is not a function, so "UE55NU7100" still searches.
            // A sign is checked FIRST: it is the only thing that separates
            // "vol" from "vol+", and normalization would drop it.
            val f = signedFunction(w) ?: FUNCTION_WORDS[wordKey(w)]
            if (f == null) names += w else functions += f
        }
        return Query(names, functions)
    }

    /**
     * Signed one-glyph queries arrive before normalization strips the sign:
     * "vol+" asks for the up key specifically, so a remote carrying only
     * "Vol-" must not pass. Unsigned handling stays in [FUNCTION_WORDS].
     */
    private fun signedFunction(word: String): String? = when (wordKey(word)) {
        "vol" -> if (word.contains('-')) "vol_down" else if (word.contains('+')) "vol_up" else null
        "ch", "channel", "page" -> if (word.contains('-')) "ch_down" else if (word.contains('+')) "ch_up" else null
        else -> null
    }

    /**
     * True when [buttonNames] cover every function in [required].
     * Empty [required] always passes.
     */
    fun covers(buttonNames: List<String>, required: Set<String>): Boolean =
        required.all { key -> buttonNames.any { n -> checkFor(key, n) } }

    /**
     * Route a function key to its predicate. Besides the symmetric keys, a key
     * may be directional (`vol_up`, `ch_down`, `power_off`) — added because
     * "vol+" and "vol-" are what users actually type, and a remote carrying
     * only a down key must not pass a "vol+" search. [IrCodeRepository] passes
     * these through unhinted, which just widens its SQL pre-filter.
     */
    private fun checkFor(key: String, name: String): Boolean = when (key) {
        "power" -> ButtonNames.power(name)
        "volume" -> ButtonNames.volUp(name) || ButtonNames.volDown(name)
        "channel" -> ButtonNames.chUp(name) || ButtonNames.chDown(name)
        "vol_up" -> ButtonNames.volUp(name)
        "vol_down" -> ButtonNames.volDown(name)
        "ch_up" -> ButtonNames.chUp(name)
        "ch_down" -> ButtonNames.chDown(name)
        "power_on" -> ButtonNames.power(name) && !ButtonNames.powerOff(name)
        "power_off" -> ButtonNames.powerOff(name)
        "mute" -> ButtonNames.mute(name)
        "play_pause" -> ButtonNames.playPause(name)
        "up" -> ButtonNames.up(name)
        "down" -> ButtonNames.down(name)
        "left" -> ButtonNames.left(name)
        "right" -> ButtonNames.right(name)
        "ok" -> ButtonNames.ok(name)
        "home" -> ButtonNames.home(name)
        "back" -> ButtonNames.back(name)
        "exit" -> ButtonNames.exit(name)
        "guide" -> ButtonNames.guide(name)
        "menu" -> ButtonNames.menu(name)
        "info" -> ButtonNames.info(name)
        "source" -> ButtonNames.source(name)
        else -> false
    }

    /**
     * Combined score for [strings] (e.g. brand name + its model strings)
     * against [nameTokens]. Every token must hit at least one string; 0 means
     * no match. Empty [nameTokens] scores 0 — pure function terms are filters,
     * not ranking terms.
     */
    fun combinedScore(strings: List<String>, nameTokens: List<String>): Int {
        if (nameTokens.isEmpty()) return 0
        val perToken = nameTokens.map { t -> strings.maxOf { score(it, t) } }
        return if (perToken.any { it == 0 }) 0 else perToken.sum()
    }
}
