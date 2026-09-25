package com.erfanbagheri.controlix.data

/**
 * Pure candidate memory for borrowed pad codes.
 *
 * Rejected wire patterns are skipped for one locked remote + key. When every
 * available pattern has been rejected, the first remains usable so a transient
 * wrong remote can never permanently remove the key.
 *
 * ponytail: identities contain source remote and wire pattern; names/display
 * data can change without invalidating user feedback. Add database migration
 * only if remote IDs stop being stable across IR database refreshes.
 */
data class BorrowedCodeMemory(
    private val decisions: Map<DecisionKey, Decision> = emptyMap(),
) {
    data class DecisionKey(val lockedRemoteId: Int, val key: String)
    data class Decision(val pattern: String, val remoteId: Int, val accepted: Boolean)

    fun reject(lockedRemoteId: Int, key: String, candidate: SiblingButtonPicker.Picked): BorrowedCodeMemory =
        update(lockedRemoteId, key, candidate, accepted = false)

    fun accept(lockedRemoteId: Int, key: String, candidate: SiblingButtonPicker.Picked): BorrowedCodeMemory =
        update(lockedRemoteId, key, candidate, accepted = true)

    fun select(
        lockedRemoteId: Int,
        key: String,
        candidates: List<SiblingButtonPicker.Picked>,
    ): SiblingButtonPicker.Picked? {
        if (candidates.isEmpty()) return null
        val decision = decisions[DecisionKey(lockedRemoteId, key)] ?: return candidates.first()
        val chosen = if (decision.accepted) {
            candidates.firstOrNull { it.matches(decision.pattern, decision.remoteId) }
        } else {
            candidates.firstOrNull { !it.matches(decision.pattern, decision.remoteId) }
        }
        return chosen ?: candidates.first()
    }

    fun needsConfirmation(
        lockedRemoteId: Int,
        key: String,
        candidate: SiblingButtonPicker.Picked,
    ): Boolean = decisions[DecisionKey(lockedRemoteId, key)]?.let {
        !(it.accepted && candidate.matches(it.pattern, it.remoteId))
    } ?: true

    fun serialize(): String = decisions.entries.joinToString("\n") { (key, decision) ->
        listOf(
            key.lockedRemoteId,
            encode(key.key),
            decision.remoteId,
            encode(decision.pattern),
            if (decision.accepted) "a" else "r",
        ).joinToString("|")
    }

    private fun update(
        lockedRemoteId: Int,
        key: String,
        candidate: SiblingButtonPicker.Picked,
        accepted: Boolean,
    ): BorrowedCodeMemory {
        val map = decisions.toMutableMap()
        map[DecisionKey(lockedRemoteId, key)] = Decision(candidate.patternText(), candidate.remoteId, accepted)
        return copy(decisions = map)
    }

    private fun SiblingButtonPicker.Picked.matches(pattern: String, remoteId: Int) =
        patternText() == pattern && this.remoteId == remoteId

    private fun SiblingButtonPicker.Picked.patternText() = pattern.joinToString(",")

    private fun encode(value: String) = value.replace("%", "%25").replace("|", "%7C").replace("\n", "%0A")

    companion object {
        private fun decode(value: String) = value.replace("%0A", "\n").replace("%7C", "|").replace("%25", "%")

        fun parse(raw: String): BorrowedCodeMemory {
            val decisions = raw.lineSequence().mapNotNull { line ->
                val fields = line.split('|')
                if (fields.size != 5) return@mapNotNull null
                val lockedRemoteId = fields[0].toIntOrNull() ?: return@mapNotNull null
                val key = decode(fields[1])
                val remoteId = fields[2].toIntOrNull() ?: return@mapNotNull null
                val pattern = decode(fields[3])
                val accepted = when (fields[4]) {
                    "a" -> true
                    "r" -> false
                    else -> return@mapNotNull null
                }
                DecisionKey(lockedRemoteId, key) to Decision(pattern, remoteId, accepted)
            }.toMap()
            return BorrowedCodeMemory(decisions)
        }
    }
}
