package com.erfanbagheri.controlix.data

/**
 * Missing-code reports (issue #79). A queued *report*, not a code: the pad
 * knows a key failed and who owns the remote, and that is worth keeping.
 * Pure Kotlin, no Android dependency, so the queue is JVM-testable.
 *
 * Format: `brand~category~remote~button`, entries joined by `;;`. Fields
 * escape `~` and `;` with a backslash so a brand like "Bang & Olufsen" or a
 * button named "vol+" can never corrupt the record. A line that fails to
 * decode is dropped whole, never partially applied.
 *
 * Metadata only — a report carries identity and the failed button, never a
 * code, so nothing proprietary can ride along.
 */
data class MissingCodeReport(
    val brand: String,
    val category: String,
    val remote: String,
    val button: String,
) {
    /** A remote with no supported key at all reports the remote, not one key. */
    val wholeRemote: Boolean get() = button.isBlank()
}

object MissingCodeReports {

    private const val GROUP = ";;"

    fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("~", "\\~").replace(";", "\\;")

    fun unescape(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                out.append(s[i + 1]); i += 2
            } else {
                out.append(c); i++
            }
        }
        return out.toString()
    }

    fun encode(r: MissingCodeReport): String = listOf(r.brand, r.category, r.remote, r.button)
        .joinToString("~") { escape(it) }

    fun decode(s: String): MissingCodeReport? {
        if (s.isBlank()) return null
        val f = splitUnescaped(s, "~")
        if (f.size != 4) return null
        val brand = unescape(f[0]).trim()
        if (brand.isBlank()) return null
        val button = unescape(f[3]).trim()
        // A blank button is legal and means "the whole remote is dead".
        return MissingCodeReport(brand, unescape(f[1]).trim(), unescape(f[2]).trim(), button)
    }

    fun encodeList(list: List<MissingCodeReport>): String = list.joinToString(GROUP) { encode(it) }

    fun decodeList(s: String): List<MissingCodeReport> {
        if (s.isBlank()) return emptyList()
        return splitUnescaped(s, GROUP).mapNotNull { runCatching { decode(it) }.getOrNull() }
    }

    /**
     * Split on [sep], honouring backslash escapes, so an escaped `\;` inside a
     * field is not a group separator. Unescaping is the caller's job.
     */
    private fun splitUnescaped(s: String, sep: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            when {
                s[i] == '\\' && i + 1 < s.length -> {
                    cur.append(s[i]).append(s[i + 1]); i += 2
                }
                s.startsWith(sep, i) -> {
                    out.add(cur.toString()); cur.setLength(0); i += sep.length
                }
                else -> {
                    cur.append(s[i]); i++
                }
            }
        }
        out.add(cur.toString())
        return out
    }
}

/**
 * The queue's decisions, as pure functions so they can be asserted without a
 * device: [MissingCodeQueuePolicy].
 */
object MissingCodeQueuePolicy {

    /** Nothing to report: no brand means nothing to send. */
    fun canQueue(r: MissingCodeReport): Boolean = r.brand.isNotBlank()

    /** A queued report must not be offered twice for the same dead key. */
    fun add(list: List<MissingCodeReport>, r: MissingCodeReport): List<MissingCodeReport> =
        if (list.any { it.brand == r.brand && it.remote == r.remote && it.button == r.button }) list
        else list + r

    fun remove(list: List<MissingCodeReport>, r: MissingCodeReport): List<MissingCodeReport> =
        list.filterNot { it == r }

    /**
     * Submission is impossible without the user confirming the destination.
     * Sharing is an explicit share sheet; the app holds no INTERNET
     * permission and never uploads on its own.
     */
    fun canSubmit(confirmed: Boolean, list: List<MissingCodeReport>): Boolean =
        confirmed && list.isNotEmpty()
}
