package com.erfanbagheri.controlix.data

/**
 * Copied buttons (issue #10): a button copied from one saved remote's pad
 * onto another remote's local list. Pure Kotlin, no Android dependency.
 *
 * Clipboard/list format: `name|carrierHz|d1,d2,...`, entries joined by `;;`.
 * Names may not contain `|` or `;`; patterns must be >= 2 positive ints.
 */
data class CopiedButton(
    val name: String,
    val carrierHz: Int,
    val pattern: IntArray,
) {
    override fun equals(other: Any?): Boolean =
        other is CopiedButton && name == other.name && carrierHz == other.carrierHz &&
            pattern.contentEquals(other.pattern)

    override fun hashCode(): Int = (name.hashCode() * 31 + carrierHz) * 31 + pattern.contentHashCode()
}

object CopiedButtons {

    fun encode(b: CopiedButton): String =
        "${b.name}|${b.carrierHz}|${b.pattern.joinToString(",")}"

    fun decode(s: String): CopiedButton? {
        val f = s.split("|")
        if (f.size != 3) return null
        val name = f[0]
        if (name.isBlank() || name.contains(";")) return null
        val carrier = f[1].toIntOrNull() ?: return null
        if (carrier <= 0) return null
        val pattern = f[2].split(",").map { it.toIntOrNull() ?: return null }.toIntArray()
        if (pattern.size < 2 || pattern.any { it <= 0 }) return null
        return CopiedButton(name, carrier, pattern)
    }

    fun encodeList(list: List<CopiedButton>): String =
        list.joinToString(";;") { encode(it) }

    fun decodeList(s: String): List<CopiedButton> {
        if (s.isBlank()) return emptyList()
        return s.split(";;").mapNotNull { runCatching { decode(it) }.getOrNull() }
    }

    /** Paste: same name (case-insensitive) is replaced, otherwise appended. */
    fun addLocal(list: List<CopiedButton>, button: CopiedButton): List<CopiedButton> =
        list.filterNot { it.name.equals(button.name, ignoreCase = true) } + button

    fun removeLocal(list: List<CopiedButton>, name: String): List<CopiedButton> =
        list.filterNot { it.name.equals(name, ignoreCase = true) }

    /**
     * The local copy that answers a pad key, mirroring PadScreen.fire():
     * semantic CHECKS key first (so "VOL_UP" answers "volume_up"), then
     * exact name, then containment. Null when no local copy covers the key.
     */
    fun localForKey(local: List<CopiedButton>, key: String): CopiedButton? {
        val predicate = EffectiveButtons.CHECKS.firstOrNull { it.first == key }?.second
        if (predicate != null) local.firstOrNull { predicate(it.name) }?.let { return it }
        return local.firstOrNull { it.name.equals(key, ignoreCase = true) }
            ?: local.firstOrNull { it.name.contains(key, ignoreCase = true) }
    }
}
