package com.erfanbagheri.controlix.ui

/**
 * The wire format for one store's SharedPreferences payload (issue #83).
 *
 * Pure Kotlin, no Android — a store is a flat key→string map, and the backup
 * carries it as `key=value` lines so [BackupStoreIo] can move bytes without
 * understanding what any store means.
 *
 * Escaping is three rules, and all three are load-bearing: a store value is
 * user data (a pasted IR pattern, a device name), so it can contain a
 * newline, a backslash or an equals sign. A value with a newline in it would
 * otherwise split into a bogus key and silently corrupt the store.
 */
object StorePayload {

    fun encode(values: Map<String, String>): String =
        values.entries.joinToString("\n") { (k, v) -> "${escapeKey(k)}=${escapeValue(v)}" }

    /**
     * A line that is not `key=value` is skipped rather than failing the whole
     * restore: one bad line must not cost the user every other store. A key
     * with an empty value is legitimate and is kept.
     */
    fun decode(payload: String): Map<String, String> {
        if (payload.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (line in payload.split("\n")) {
            if (line.isEmpty()) continue
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = unescape(line.substring(0, eq))
            if (key.isEmpty()) continue
            out[key] = unescape(line.substring(eq + 1))
        }
        return out
    }

    /** Keys are identifiers we wrote, but escape anyway — never trust input. */
    fun escapeKey(s: String): String = escape(s)

    fun escapeValue(s: String): String = escape(s)

    private fun escape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '=' -> append("\\=")
            else -> append(c)
        }
    }

    private fun unescape(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> out.append('\n')
                    else -> out.append(s[i + 1])
                }
                i += 2
            } else {
                out.append(c); i++
            }
        }
        return out.toString()
    }
}
