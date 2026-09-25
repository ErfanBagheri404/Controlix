package com.erfanbagheri.controlix.data

/**
 * Issue #17: the manual key list for an unmatched remote — every distinct
 * button the remote actually has, minus the ones already on the standard pad.
 * Pure function over the remote's own buttons: no sibling codes, no invented
 * data.
 *
 * ponytail: labels dedupe case-sensitively; near-duplicates ('ASPECT' vs
 * 'Aspect') stay separate on purpose — they may be distinct wire codes.
 * Fold case when the DB proves they always share a pattern.
 */
object ManualKeys {

    /** One tryable key: its label and the button that carries it. */
    data class Entry(val label: String, val button: IrCodeRepository.Button)

    /** Counts for the expand header ("27 on pad · 14 more"). */
    data class Summary(val onPad: Int, val extras: List<Entry>) {
        val extraCount: Int get() = extras.size
    }

    /** True when the standard pad already offers this button's function. */
    fun isOnPad(name: String): Boolean = EffectiveButtons.CHECKS.any { it.second(name) }

    /**
     * Distinct non-standard labels, sorted case-insensitively with a
     * case-sensitive tiebreak so the order is deterministic.
     */
    fun build(buttons: List<IrCodeRepository.Button>): List<Entry> {
        val seen = HashSet<String>()
        return buttons
            .filter { !isOnPad(it.name) && seen.add(it.name) }
            .map { Entry(it.name, it) }
            .sortedWith(compareBy({ it.label.lowercase() }, { it.label }))
    }

    /**
     * @param buttons the remote's own buttons
     * @return how many distinct standard keys the remote covers, plus the
     *         manual extras beyond them.
     */
    fun summarize(buttons: List<IrCodeRepository.Button>): Summary {
        val onPad = EffectiveButtons.CHECKS.count { (_, pred) -> buttons.any { pred(it.name) } }
        return Summary(onPad, build(buttons))
    }
}
