package com.erfanbagheri.controlix.data

/**
 * Read-only database health: pure classification and human labels for what
 * the bundled DB actually contains. No query here writes, deletes or hides a
 * row — the 127 zero-button remotes and 13 non-expandable codes are known
 * facts that stay in the DB and stay reported.
 */
object DbHealth {

    /** One flat line on the health screen. */
    data class Row(
        val title: String,
        val value: String,
        val hint: String? = null,
        val danger: Boolean = false,
    )

    /**
     * A button name that no standard pad function claims — the 'extra' keys
     * the expanded keypad still offers. Reuses [EffectiveButtons.CHECKS] so
     * the health view and the borrow layer can never disagree.
     */
    fun unmapped(name: String): Boolean =
        EffectiveButtons.CHECKS.none { (_, pred) -> pred(name) }

    /** Button occurrences behind [unmapped] names, across the whole DB. */
    fun unmappedCount(names: Map<String, Int>): Int =
        names.entries.sumOf { (name, count) -> if (unmapped(name)) count else 0 }

    /**
     * The summary block: totals first, then every finding in report order.
     * @param names      every distinct button label -> how many buttons carry it
     * @param protocols  (protocol or null for raw) -> button count
     */
    fun report(
        totals: IrCodeRepository.Totals,
        sources: List<IrCodeRepository.SourceCount>,
        emptyRemotes: Int,
        unusableCodes: Int,
        names: Map<String, Int>,
        protocols: List<Pair<String?, Int>>,
    ): List<Row> = listOf(
        Row("Remotes", totals.remotes.toString(), "every remote row in the bundled DB"),
        Row("Buttons", totals.buttons.toString(), "codes that can be transmitted"),
        Row("Brands", totals.brands.toString(), null),
        Row("Sources", sources.size.toString(), sources.joinToString(" · ") { "${it.name} ${it.remotes}" }),
        Row(
            "Empty remotes",
            emptyRemotes.toString(),
            "ship with no buttons at all — searchable but unable to transmit",
            danger = emptyRemotes > 0,
        ),
        Row(
            "Unusable codes",
            unusableCodes.toString(),
            "button patterns the transmitter cannot expand",
            danger = unusableCodes > 0,
        ),
        Row(
            "Extra / unmapped keys",
            unmappedCount(names).toString(),
            "no standard pad function — kept for the expanded keypad",
            danger = false,
        ),
        Row("Protocols present", protocols.size.toString(), "including raw frames as their own entry"),
    )

    /** Why a remote has no buttons: the source row it came from was empty. */
    fun emptyRows(remotes: List<IrCodeRepository.EmptyRemote>): List<Row> = remotes.map { r ->
        Row(
            title = r.fileName,
            value = r.source,
            hint = if (r.fileName.endsWith(",-1.irdb")) {
                "no buttons in the source row — the irdb filename ends ,-1 (unmapped source entry)"
            } else {
                "no buttons in the source file"
            },
        )
    }

    /** One line per non-expandable pattern, naming the button and its remote. */
    fun unusableRows(buttons: List<IrCodeRepository.UnusableButton>): List<Row> = buttons.map { b ->
        Row(title = b.name, value = b.remoteFileName, hint = "pattern cannot be expanded", danger = true)
    }

    /** Protocol tally, biggest first as the caller ordered it. Raw is 'raw'. */
    fun protocolRows(protocols: List<Pair<String?, Int>>): List<Row> = protocols.map { (proto, count) ->
        Row(title = proto ?: "raw", value = count.toString())
    }
}
