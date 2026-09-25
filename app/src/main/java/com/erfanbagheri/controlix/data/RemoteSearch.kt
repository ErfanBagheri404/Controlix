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

    /** Function words the parser understands → canonical ButtonNames keys. */
    private val FUNCTION_WORDS: Map<String, String> = mapOf(
        "power" to "power", "vol" to "volume", "volume" to "volume",
        "ch" to "channel", "channel" to "channel", "mute" to "mute",
        "play" to "play_pause", "pause" to "play_pause",
        "up" to "up", "down" to "down", "left" to "left", "right" to "right",
        "ok" to "ok", "home" to "home", "back" to "back", "exit" to "exit",
        "guide" to "guide", "menu" to "menu", "info" to "info",
        "source" to "source", "input" to "source",
    )

    /** A parsed user query: which name tokens to match, which functions required. */
    data class Query(val nameTokens: List<String>, val functions: Set<String>)

    /** Split a raw query into name tokens and required button-function keys. */
    fun parse(raw: String): Query {
        val words = raw.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val functions = LinkedHashSet<String>()
        val names = ArrayList<String>(words.size)
        for (w in words) {
            val f = FUNCTION_WORDS[w]
            if (f == null) names += w else functions += f
        }
        return Query(names, functions)
    }

    /**
     * True when [buttonNames] cover every function in [required].
     * Empty [required] always passes.
     */
    fun covers(buttonNames: List<String>, required: Set<String>): Boolean =
        required.all { key ->
            buttonNames.any { n -> checkFor(key, n) }
        }

    private fun checkFor(key: String, name: String): Boolean = when (key) {
        "power" -> ButtonNames.power(name)
        "volume" -> ButtonNames.volUp(name) || ButtonNames.volDown(name)
        "channel" -> ButtonNames.chUp(name) || ButtonNames.chDown(name)
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
