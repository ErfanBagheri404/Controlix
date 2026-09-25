package com.erfanbagheri.controlix.data

/**
 * Pure AC selection model. An [AcSelection] (target temperature, mode,
 * power) resolves against the remote's actual button set into the real
 * button to transmit — or into [AcOutcome.NotSupported] naming the
 * buttons the remote does have. Never invents a pattern: AC remotes are
 * stateful (temp + mode encoded per press), so a missing combination
 * must stay silent with an honest explanation, not a guessed code.
 *
 * Data shape in the bundled DB (acs category): per-(mode, temp) labels
 * ("Cool_23", "Auto_25_slow"), standalone mode labels ("Cool", "FAN ONLY"),
 * power ("Off", "POWER"), fan steps ("FAN+", "FanSlower"), swing, and
 * temperature step keys ("TEMP+", "Down_temp").
 */
enum class AcMode(val label: String) {
    COOL("Cool"),
    HEAT("Heat"),
    DRY("Dry"),
    FAN("Fan"),
    AUTO("Auto"),
}

data class AcSelection(val tempC: Int, val mode: AcMode, val power: Boolean) {

    /** e.g. "Cool 24°C" — used verbatim in the "not supported" message. */
    fun describe(): String = "$tempC°C ${mode.label}"

    fun resolve(available: List<EffectiveButtons.Resolved>): AcOutcome {
        if (!power) {
            return match(available) { b -> ButtonNames.powerOff(b.name) } ?: missing(available)
        }
        // Exact (temp, mode) state — the remote's real stateful code.
        match(available) { b -> ButtonNames.acCombo(b.name, mode.label, tempC) }
            ?.let { return it }
        // Remotes that store no per-temp states send the mode (or plain
        // power) key instead. Only when this remote has no states for the
        // mode at all — a remote that stores some states must not have its
        // missing ones guessed.
        val storesStates = available.any { ButtonNames.acComboTemp(it.name, mode.label) != null }
        if (!storesStates) {
            match(available) { b -> ButtonNames.acModeStandalone(b.name, mode.label) }
                ?.let { return it }
            match(available) { b -> ButtonNames.power(b.name) }
                ?.let { return it }
        }
        return missing(available)
    }

    private fun match(
        available: List<EffectiveButtons.Resolved>,
        pred: (EffectiveButtons.Resolved) -> Boolean,
    ): AcOutcome.Resolved? = available.firstOrNull(pred)?.let { AcOutcome.Resolved(it) }

    private fun missing(available: List<EffectiveButtons.Resolved>) =
        AcOutcome.NotSupported(available.map { it.name }.distinct().sorted())

    companion object {
        /** Temperatures the remote actually carries for [mode], in Celsius. */
        fun supportedTemps(available: List<EffectiveButtons.Resolved>, mode: AcMode): List<Int> =
            available.mapNotNull { ButtonNames.acComboTemp(it.name, mode.label) }
                .distinct().sorted()

        /** Modes the remote actually carries (standalone or per-temp state). */
        fun supportedModes(available: List<EffectiveButtons.Resolved>): Set<AcMode> =
            AcMode.entries.filter { m ->
                available.any { ButtonNames.acMode(it.name, m.label) }
            }.toSet()

        /** Target ladder offered by the pad; unsupported values stay disabled. */
        fun presetTemps(): List<Int> = (16..30).toList()

        /** Modes offered by the pad; unsupported modes stay disabled. */
        fun presetModes(): List<AcMode> = AcMode.entries.toList()
    }
}

sealed interface AcOutcome {
    data class Resolved(val button: EffectiveButtons.Resolved) : AcOutcome
    data class NotSupported(val buttonsItDoesHave: List<String>) : AcOutcome
}
