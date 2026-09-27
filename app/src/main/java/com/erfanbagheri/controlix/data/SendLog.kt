package com.erfanbagheri.controlix.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Recent sends (issue #68): bounded history of what the blaster fired.
 * Pure Kotlin, no Android dependency.
 *
 * Line format: `remote|button|carrier|epochMs|failure|d1,d2,...`, entries
 * joined by `;;`. Names may not contain `|` or `;` (same rule as
 * [CopiedButtons]); a failure reason is sanitized on encode. A corrupt line
 * decodes to null and drops that line only, never the whole log.
 *
 * A pattern implies a real carrier and a real pattern. An entry with no
 * pattern is a send that never reached the driver — it must carry the reason
 * ([failed]) or it is not worth keeping.
 */
data class SentEntry(
    val remoteName: String,
    val buttonName: String,
    val carrierHz: Int,
    val pattern: IntArray,
    val epochMs: Long,
    /** Null when the transmit succeeded; otherwise why nothing went out. */
    val failed: String? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is SentEntry && remoteName == other.remoteName && buttonName == other.buttonName &&
            carrierHz == other.carrierHz && pattern.contentEquals(other.pattern) &&
            epochMs == other.epochMs && failed == other.failed

    override fun hashCode(): Int =
        (((remoteName.hashCode() * 31 + buttonName.hashCode()) * 31 + carrierHz) * 31 +
            pattern.contentHashCode()) * 31 + epochMs.hashCode() * 31 + (failed?.hashCode() ?: 0)
}

data class SendDay(val date: LocalDate, val entries: List<SentEntry>)

object SendLog {
    const val MAX = 100

    /** Append; evicts oldest-first at the cap. Newest last. */
    fun append(list: List<SentEntry>, entry: SentEntry): List<SentEntry> =
        (list + entry).takeLast(MAX)

    /** A send that never reached the driver: no carrier, no pattern, just why. */
    fun nothingSent(remoteName: String, buttonName: String, reason: String, epochMs: Long) =
        SentEntry(remoteName, buttonName, 0, IntArray(0), epochMs, reason)

    fun encode(e: SentEntry): String =
        "${e.remoteName}|${e.buttonName}|${e.carrierHz}|${e.epochMs}|" +
            "${e.failed?.replace('|', '/')?.replace(';', '/') ?: ""}|" +
            e.pattern.joinToString(",")

    fun decode(s: String): SentEntry? {
        val f = s.split("|")
        if (f.size != 6) return null
        val remote = f[0]
        val button = f[1]
        if (remote.isBlank() || button.isBlank()) return null
        if (remote.contains(";") || button.contains(";")) return null
        val carrier = f[2].toIntOrNull() ?: return null
        val epoch = f[3].toLongOrNull() ?: return null
        if (epoch < 0) return null
        val reason = f[4].ifEmpty { null }
        val pattern = if (f[5].isEmpty()) {
            IntArray(0)
        } else {
            f[5].split(",").map { it.toIntOrNull() ?: return null }.toIntArray()
        }
        if (pattern.isEmpty()) {
            if (reason == null) return null
        } else if (carrier <= 0 || pattern.size < 2 || pattern.any { it <= 0 }) {
            return null
        }
        return SentEntry(remote, button, carrier, pattern, epoch, reason)
    }

    fun encodeList(list: List<SentEntry>): String =
        list.joinToString(";;") { encode(it) }

    fun decodeList(s: String): List<SentEntry> {
        if (s.isBlank()) return emptyList()
        return s.split(";;").mapNotNull { runCatching { decode(it) }.getOrNull() }
    }

    /**
     * Newest day first; entries within a day newest first.
     * Stable: the same list always yields the same grouping.
     */
    fun groupByDay(list: List<SentEntry>, zone: ZoneId = ZoneId.systemDefault()): List<SendDay> {
        val days = LinkedHashMap<LocalDate, MutableList<SentEntry>>()
        for (e in list.sortedByDescending { it.epochMs }) {
            val day = Instant.ofEpochMilli(e.epochMs).atZone(zone).toLocalDate()
            days.getOrPut(day) { mutableListOf() }.add(e)
        }
        return days.map { (date, entries) -> SendDay(date, entries) }
    }
}
