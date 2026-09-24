package com.erfanbagheri.controlix.data

/**
 * Cross-remote button fill. Community IR databases are split across many
 * remote variants: the remote a user locked onto often lacks channel or
 * volume codes that sibling remotes of the same brand carry. When a code is
 * missing we look at the brand's other remotes and pick the pattern that the
 * most of them agree on — a code three remotes share is far likelier to be
 * the real one than a pattern that appears once.
 *
 * Ordering rule: the locked remote's own button first (already in hand),
 * then candidates by descending vote count, ties broken by remote id so the
 * result is deterministic.
 */
object SiblingButtonPicker {

    /** One button carrying a name, from one remote. */
    data class Candidate(
        val remoteId: Int,
        val name: String,
        val pattern: IntArray,
        val carrierHz: Int = 38000,
        val protocol: String? = null,
    ) {
        override fun equals(other: Any?): Boolean =
            other is Candidate && remoteId == other.remoteId && name == other.name &&
                pattern.contentEquals(other.pattern)

        override fun hashCode(): Int =
            (remoteId * 31 + name.hashCode()) * 31 + pattern.contentHashCode()
    }

    /** A resolved button: the winning pattern and how many remotes agreed. */
    data class Picked(
        val remoteId: Int,
        val name: String,
        val pattern: IntArray,
        val votes: Int,
        val carrierHz: Int = 38000,
        val protocol: String? = null,
    )

    /**
     * @param lockedRemoteId the remote currently in use
     * @param lockedButtons  button names that remote already has
     * @param candidates     buttons from every remote of the brand
     * @param predicate      fuzzy name test (ButtonNames.volUp etc.)
     * @param lockedProtocol protocol the locked remote already speaks; used
     *                        only to break equal-vote ties
     * @return matching patterns, best first. Empty when nothing matches.
     *
     * Ordering rule: the locked remote's own button first (already in hand),
     * then by vote count, then by agreement with the locked remote's
     * protocol. Protocol is only a tiebreak — a lone code in the right
     * protocol does not outrank a code three remotes agree on.
     */
    fun pick(
        lockedRemoteId: Int,
        lockedButtons: List<String>,
        candidates: List<Candidate>,
        predicate: (String) -> Boolean,
        lockedProtocol: String? = null,
    ): List<Picked> {
        val own = lockedButtons.any(predicate)
        val matching = candidates.filter { predicate(it.name) }

        // Group by wire pattern; identical patterns across remotes are one code.
        val byPattern = LinkedHashMap<String, MutableList<Candidate>>()
        for (c in matching) byPattern.getOrPut(c.pattern.joinToString(",")) { mutableListOf() }.add(c)

        val picked = byPattern.values.map { group ->
            val votes = group.map { it.remoteId }.distinct().size
            // A group that contains the locked remote is that remote's own code.
            val fromLocked = group.firstOrNull { it.remoteId == lockedRemoteId }
            val best = fromLocked ?: group.first()
            // Majority protocol of the group; the locked remote's own protocol
            // wins its own group so its Picked keeps its identity.
            val proto = fromLocked?.protocol
                ?: group.groupingBy { it.protocol }.eachCount().maxByOrNull { it.value }?.key
            Picked(best.remoteId, best.name, best.pattern, votes, best.carrierHz, proto)
        }

        return picked.sortedWith(
            compareByDescending<Picked> { it.remoteId == lockedRemoteId && own }
                .thenByDescending { it.votes }
                .thenByDescending { lockedProtocol != null && it.protocol == lockedProtocol }
                .thenBy { it.remoteId }
        )
    }

    /**
     * The locked remote's own code, or the highest-vote sibling code.
     * Convenience for call sites that only need the first answer.
     */
    fun best(
        lockedRemoteId: Int,
        lockedButtons: List<String>,
        candidates: List<Candidate>,
        predicate: (String) -> Boolean,
        lockedProtocol: String? = null,
    ): Picked? = pick(lockedRemoteId, lockedButtons, candidates, predicate, lockedProtocol).firstOrNull()

    /** Which of the ritual's follow-up tests this remote still cannot answer. */
    fun missing(
        lockedButtons: List<String>,
        checks: List<Pair<String, (String) -> Boolean>>,
    ): List<String> = checks.filterNot { (_, pred) -> lockedButtons.any(pred) }.map { it.first }
}
