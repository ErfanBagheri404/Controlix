package com.erfanbagheri.controlix.data

/**
 * A remote's usable button set: its own codes, plus sibling codes the locked
 * remote is missing. Consumers ask this instead of [IrCodeRepository.buttons]
 * so a remote never reports "no channel up" while the DB holds one.
 *
 * Rejected sibling patterns are skipped on later sessions; accepted ones are
 * preferred. The memory layer is optional so setup callers keep their
 * transient best-first ranking.
 */
object EffectiveButtons {

    /** Checks every standard remote key, in the order the pad shows them. */
    val CHECKS: List<Pair<String, (String) -> Boolean>> = buildList {
        add("power" to ButtonNames::power)
        add("volume_up" to ButtonNames::volUp)
        add("volume_down" to ButtonNames::volDown)
        add("channel_up" to ButtonNames::chUp)
        add("channel_down" to ButtonNames::chDown)
        add("mute" to ButtonNames::mute)
        // D-pad, app keys, transport, keypad — all borrowable too.
        add("up" to ButtonNames::up)
        add("down" to ButtonNames::down)
        add("left" to ButtonNames::left)
        add("right" to ButtonNames::right)
        add("ok" to ButtonNames::ok)
        add("home" to ButtonNames::home)
        add("back" to ButtonNames::back)
        add("exit" to ButtonNames::exit)
        add("guide" to ButtonNames::guide)
        add("menu" to ButtonNames::menu)
        add("info" to ButtonNames::info)
        add("source" to ButtonNames::source)
        add("play_pause" to ButtonNames::playPause)
        for (d in '0'..'9') add(d.toString() to { n: String -> ButtonNames.digit(n, d) })
    }

    /**
     * @param lockedButtons the remote's own buttons
     * @param siblings      every button of the brand (any remote)
     * @param memory        persisted accept/reject feedback; when given,
     *                      rejected sibling patterns are skipped and an
     *                      accepted one is preferred on later sessions
     * @return own buttons plus the best sibling code for each missing key,
     *         each marked with the remote it came from.
     */
    fun resolve(
        lockedRemoteId: Int,
        lockedButtons: List<IrCodeRepository.Button>,
        siblings: List<IrCodeRepository.Button>,
        memory: BorrowedCodeMemory? = null,
    ): List<Resolved> {
        val out = ArrayList<Resolved>(lockedButtons.size)
        val lockedNames = lockedButtons.map { it.name }
        // Protocol the locked remote already speaks, for vote-tie breaks.
        val lockedProtocol = lockedButtons.firstNotNullOfOrNull { it.protocol }

        // Own buttons first, so macro playback and the pad see real codes.
        val ownByKey = CHECKS.mapNotNull { (key, pred) ->
            lockedButtons.firstOrNull { pred(it.name) }?.let { key to it }
        }.toMap()
        for ((key, b) in ownByKey) out += Resolved(key, b.name, b.carrierHz, b.pattern, b.remoteId, 1, borrowed = false)

        val ownCandidates = siblings.map {
            SiblingButtonPicker.Candidate(it.remoteId, it.name, it.pattern, it.carrierHz, it.protocol)
        }
        for ((key, pred) in CHECKS) {
            if (ownByKey.containsKey(key)) continue
            val ranked = SiblingButtonPicker.pick(
                lockedRemoteId = lockedRemoteId,
                lockedButtons = lockedNames,
                candidates = ownCandidates,
                predicate = pred,
                lockedProtocol = lockedProtocol,
            )
            val best = memory?.select(lockedRemoteId, key, ranked) ?: ranked.firstOrNull() ?: continue
            out += Resolved(key, best.name, best.carrierHz, best.pattern, best.remoteId, best.votes, borrowed = true)
        }

        // Non-standard buttons stay available for the pad's expanded keypad.
        for (b in lockedButtons) {
            if (CHECKS.any { it.second(b.name) }) continue
            out += Resolved(b.name, b.name, b.carrierHz, b.pattern, b.remoteId, 1, borrowed = false)
        }
        return out
    }

    /** One usable button: its semantic key, where it came from, and the votes. */
    data class Resolved(
        val key: String,
        val name: String,
        val carrierHz: Int,
        val pattern: IntArray,
        val remoteId: Int,
        val votes: Int,
        val borrowed: Boolean,
    )
}
