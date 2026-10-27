package com.erfanbagheri.controlix.ui

import com.erfanbagheri.controlix.R
import com.erfanbagheri.controlix.data.ButtonNames
import com.erfanbagheri.controlix.data.EffectiveButtons
import com.erfanbagheri.controlix.data.FontScale

/**
 * Pad for every device family that is neither TV-like nor AC.
 *
 * The TV pad (d-pad + VOL/CH + keypad) and the AC pad (mode/temperature
 * state machine) are both wrong for a fan, a soundbar or an LED strip:
 * those remotes have no channel, no d-pad, and no (mode,temp) grid. This
 * pad renders rows straight from the codes the remote actually carries, so
 * one layout serves all of them honestly.
 *
 * Data-driven: [sectionsFor] turns (family, buttons) into labelled rows and
 * [DevicePadScreen] renders whatever comes back. Adding a family is a slug
 * in [familyFor] plus a row order — not a new screen.
 *
 * Honest rules (same as AcPad):
 *  - a key that does not exist is never shown as sendable
 *  - every section is omitted when the remote carries no key for it
 *  - codes are never borrowed from sibling remotes — a sibling's state
 *    machine is not this remote's
 *
 * ponytail: family is inferred from the category slug alone; no per-brand
 * metadata. Upgrade path: a `family` column on remote/brand once the DB
 * carries it.
 */
enum class PadFamily {
    /** Fans, heaters, fireplaces: speed + swing + timer + light. */
    CLIMATE,

    /** Purifiers, humidifiers: mode + fan + mist/humidity + timer + light. */
    AIR_TREATMENT,

    /** Soundbars, speakers, receivers, CD/DVD/laserdisc, head units. */
    AUDIO,

    /** Projectors, monitors, displays: d-pad + menu + source + tools. */
    DISPLAY,

    /** LED strips and lighting: colours + effects + brightness. */
    LIGHTING,

    /** Vacuums, dust collectors, window cleaners: cleaning + suction + dock. */
    CLEANING,

    /** Set-top boxes, cable, streaming, consoles, KVM, everything TV-shaped. */
    TV,
    ;

    companion object {
        fun familyFor(categorySlug: String): PadFamily = when {
            categorySlug.startsWith("acs") -> CLIMATE
            categorySlug.startsWith("fans") ||
                categorySlug.startsWith("heaters") ||
                categorySlug.startsWith("fireplaces") -> CLIMATE
            categorySlug.startsWith("air_purifiers") ||
                categorySlug.startsWith("humidifiers") -> AIR_TREATMENT
            categorySlug.startsWith("soundbars") ||
                categorySlug.startsWith("speakers") ||
                categorySlug.startsWith("audio_and_video_receivers") ||
                categorySlug.startsWith("cd_players") ||
                categorySlug.startsWith("minidisc") ||
                categorySlug.startsWith("laserdisc") ||
                categorySlug.startsWith("head_units") -> AUDIO
            categorySlug.startsWith("projectors") ||
                categorySlug.startsWith("monitors") ||
                categorySlug.startsWith("touchscreen_displays") ||
                categorySlug.startsWith("digital_signs") ||
                categorySlug.startsWith("whiteboards") -> DISPLAY
            categorySlug.startsWith("led_lighting") ||
                categorySlug.startsWith("clocks") || categorySlug.startsWith("blinds") -> LIGHTING
            categorySlug.startsWith("vacuum_cleaners") ||
                categorySlug.startsWith("dust_collectors") ||
                categorySlug.startsWith("window_cleaners") -> CLEANING
            else -> TV
        }
    }
}

/** How a row draws. Stepper is the only one that pairs two keys. */
enum class PadRowStyle { STEPPER, CHIPS, D_PAD, KEYPAD }

/**
 * How big a chip row's keys draw.
 *
 * Mi Remote and every other universal remote give the primary action a
 * bigger target than the secondary ones — a fan's Speed row reads as a
 * control, not as a list of links. Text-only chips gave all 47 keys of a
 * projector the same visual weight, which is why the pad looked like a
 * table of contents rather than a remote.
 */
enum class PadKeySize {
    /** Half-width tiles — the pad's main actions (speed, mode, colours). */
    LARGE,
    /** Narrow pills — supporting keys (timers, effects, navigation). */
    SMALL,
    /** Square, one per grid cell — numeric pads and colour swatches. */
    SQUARE,
}

/** One labelled row: the remote's own keys, in the order the pad shows them. */
data class PadRow(
    val labelRes: Int,
    val style: PadRowStyle,
    /** Left key of a STEPPER, or null. */
    val downKey: EffectiveButtons.Resolved? = null,
    /** Right key of a STEPPER, or null. */
    val upKey: EffectiveButtons.Resolved? = null,
    /** Read-out between a stepper's two keys, if any. */
    val readout: String? = null,
    /** Row contents for CHIPS / D_PAD / KEYPAD. */
    val keys: List<EffectiveButtons.Resolved> = emptyList(),
    /** How the keys draw. Only CHIPS reads it. */
    val keySize: PadKeySize = PadKeySize.SMALL,
    /**
     * Overrides the section heading with the remote's own word.
     *
     * A compound mode row is labelled by the mode half it was grouped under
     * ("Heat", "Vent") rather than the generic "Mode" string — the label the
     * DB carries is more specific than anything we can localize, and one
     * generic heading over four groups is how the fan pad ended up with 22
     * identical-looking chips.
     */
    val labelOverride: String? = null,
    /**
     * Text drawn on each chip instead of the raw DB label.
     *
     * Parallel to [keys]. A grouped mode row shows only the duration ("5m")
     * because the heading already says "Heat"; repeating it on every chip
     * doubles the row's width for no information.
     */
    val chipLabels: List<String>? = null,
)

/**
 * Turns "+/−" and "On/Off" key pairs into single controls.
 *
 * A remote labels a rocker as two keys ("Bass+", "Bass−") and a switch as two
 * ("Surround On", "Surround Off"). Rendered as chips that is a wall of
 * near-identical labels with no hint that they are halves of one control.
 * Grouping them by their shared stem gives the pad the shape the remote has:
 * one rocker per feature, one toggle per switch.
 *
 * Anything without a partner is left unclaimed for the later rows — a lone
 * "Bass+" is still a key, just not half a stepper.
 */
private fun pairRows(
    available: List<EffectiveButtons.Resolved>,
    claimed: MutableSet<String>,
): List<PadRow> {
    val rows = mutableListOf<PadRow>()
    // Only keys no earlier rule took. A speed row that already claimed
    // "Speed UP"/"Speed DOWN" must not reappear as a rocker below it.
    val free = available.filter { it.name !in claimed }

    // A key name is only a stem worth pairing when both halves are real
    // separate keys. "Source Up" is one button that raises the source, not
    // half of a rocker — a "Source Down" does not exist to pair with, so the
    // pair never forms and the key stays a chip. Requiring a separator or a
    // sign between the two halves is what makes "Bass Up"/"Bass Down" and
    // "FanPower+"/"FanPower-" pairs while leaving single keys alone.
    fun splitPair(n: String): Pair<String, String>? {
        val t = n.trim()
        val half = listOf("up", "down", "on", "off").firstOrNull { t.endsWith(" $it", true) || t.endsWith("_$it", true) }
            ?: return null
        val stem = t.dropLast(half.length + 1).trim().trimEnd('_')
        return if (stem.isNotBlank()) stem to half.lowercase() else null
    }

    // Rockers exist in two shapes on these remotes: a plus/minus sign
    // ("FanPower+") and a word ("Bass Up"). Match whichever half the label
    // ends with, then look for the opposite half under the same stem.
    fun stemOf(n: String): String {
        splitPair(n)?.let { return it.first }
        val t = n.trim()
        val cut = listOf("+", "−", "-").firstOrNull { t.endsWith(it) && t.length > 1 }
        return (cut?.let { t.dropLast(it.length) } ?: t).trim().trimEnd('_', ' ')
    }

    // These rocks are stereo pairs, so they get the "balance" side of the
    // word, not "level" — a soundbar's Surround rocker swings left/right.
    fun take(stem: String, upWord: String, downWord: String): PadRow? {
        val up = free.firstOrNull { stemOf(it.name).equals(stem, true) && it.name.trim().endsWith(upWord, true) }
        val dn = free.firstOrNull { stemOf(it.name).equals(stem, true) && it.name.trim().endsWith(downWord, true) }
        if (up == null || dn == null || up.name == dn.name) return null
        claimed += up.name
        claimed += dn.name
        return PadRow(
            labelRes = R.string.appliance_more,
            labelOverride = stem,
            style = PadRowStyle.STEPPER,
            downKey = dn,
            upKey = up,
        )
    }

    // A sign rocker has no space between the halves ("Bass+"), a word rocker
    // has a space ("Bass Up"). Try the sign forms first so "Speed UP" is read
    // as a word pair (Up/Down) rather than a sign pair against "Speed U".
    //
    // Navigation words are not rockers. "CURSOR UP"/"CURSOR DOWN" are two arms
    // of a d-pad; pairing them prints a CURSOR stepper above a cross that has
    // no up or down arm left. Leave them for the d-pad rule.
    val navStems = setOf("cursor", "arrow", "direction", "nav", "dpad")
    free.map { stemOf(it.name) }.distinct().forEach { stem ->
        if (stem.isBlank() || stem.length < 3 || stem.lowercase() in navStems) return@forEach
        val row = take(stem, "+", "−") ?: take(stem, "+", "-")
            ?: take(stem, "Up", "Down")
        if (row != null) rows += row
    }
    return rows
}

/**
 * Builds the pad's rows for one remote.
 *
 * Every predicate comes from [ButtonNames] and is matched against the
 * remote's own labels only. Order matters: the first predicate that claims
 * a label wins, so broad ones (power, timer) are tested before narrow ones.
 */
fun sectionsFor(
    family: PadFamily,
    available: List<EffectiveButtons.Resolved>,
): List<PadRow> {
    if (available.isEmpty()) return emptyList()
    val claimed = HashSet<String>()
    val rows = mutableListOf<PadRow>()

    // Claim by predicate; a label can only land in one row.
    fun claim(pred: (String) -> Boolean): List<EffectiveButtons.Resolved> {
        val hits = available.filter { pred(it.name) && claimed.add(it.name) }
        return hits.distinctBy { it.name }
    }

    // Power first — every family leads with it. A bare "Off" is an off key,
    // never the on key (same rule the AC pad uses).
    val powerKeys = claim { ButtonNames.power(it) && !ButtonNames.powerOff(it) }
    val offKeys = claim { ButtonNames.powerOff(it) }
    if (powerKeys.isNotEmpty() || offKeys.isNotEmpty()) {
        // A stepper only when the remote really has both directions; a lone
        // Off key is a plain key, not half a control.
        if (powerKeys.isNotEmpty() && offKeys.isNotEmpty()) {
            rows += PadRow(
                labelRes = R.string.ac_power,
                style = PadRowStyle.STEPPER,
                downKey = offKeys.first(),
                upKey = powerKeys.first(),
            )
        } else {
            rows += PadRow(
                labelRes = R.string.ac_power,
                style = PadRowStyle.CHIPS,
                keys = (powerKeys + offKeys).take(24),
            )
        }
    }

    // Temperature: a real +/- pair only when both keys exist.
    val tUp = claim { ButtonNames.tempUp(it) }
    val tDown = claim { ButtonNames.tempDown(it) }
    if (tUp.isNotEmpty() && tDown.isNotEmpty()) {
        rows += PadRow(
            labelRes = R.string.appliance_temp,
            style = PadRowStyle.STEPPER,
            downKey = tDown.first(),
            upKey = tUp.first(),
        )
    }

    // Mode keys: group compound labels by their mode head ("Heat", "Vent", "Dry",
    // "HeatDry") so a fan with 22 mode+duration keys reads as four short rows
    // instead of one 22-chip wall. A bare mode word (or a non-compound label)
    // keeps the generic "Mode" heading and the raw label, as before.
    val modeKeys = claim { ButtonNames.modeKey(it) }
    if (modeKeys.isNotEmpty()) {
        val groups = linkedMapOf<String, MutableList<EffectiveButtons.Resolved>>()
        for (k in modeKeys) {
            val head = ButtonNames.modeHead(k.name)?.replaceFirstChar { it.uppercase() }
            if (head == null) {
                groups.getOrPut("Mode") { mutableListOf() } += k
            } else {
                groups.getOrPut(head) { mutableListOf() } += k
            }
        }
        for ((head, ks) in groups) {
            val isGrouped = head != "Mode" && ks.all { ButtonNames.compoundTail(it.name) != null }
            rows += PadRow(
                labelRes = R.string.appliance_mode,
                style = PadRowStyle.CHIPS,
                keys = ks.take(24),
                keySize = PadKeySize.LARGE,
                labelOverride = head,
                chipLabels = if (isGrouped) ks.map { ButtonNames.modeTail(it.name) ?: it.name } else null,
            )
        }
    }

    // Paired keys become ONE control, before any family rule can swallow them
    // as loose chips. A soundbar carries six "+/−" feature pairs (Bass,
    // Treble, Centre, Surround, Subwoofer, A/V delay) and four On/Off pairs;
    // as chips that is twelve controls that each look unrelated. The remote
    // has a rocker per feature and a toggle per switch — so does the pad.
    rows += pairRows(available, claimed)

    val speedKeys = claim {
        ButtonNames.speedLevel(it) || ButtonNames.speedUp(it) || ButtonNames.speedDown(it)
    }
    if (speedKeys.isNotEmpty()) rows += PadRow(R.string.appliance_speed, PadRowStyle.CHIPS, keys = speedKeys.take(24), keySize = PadKeySize.LARGE)

    val oscKeys = claim { ButtonNames.oscillate(it) }
    if (oscKeys.isNotEmpty()) rows += PadRow(R.string.appliance_oscillate, PadRowStyle.CHIPS, keys = oscKeys.take(24), keySize = PadKeySize.LARGE)

    val timerKeys = claim { ButtonNames.timerKey(it) }
    if (timerKeys.isNotEmpty()) rows += PadRow(R.string.appliance_timer, PadRowStyle.CHIPS, keys = timerKeys.take(24), keySize = PadKeySize.LARGE)

    val lightKeys = claim { ButtonNames.lightKey(it) }
    if (lightKeys.isNotEmpty()) rows += PadRow(R.string.appliance_light, PadRowStyle.CHIPS, keys = lightKeys.take(24), keySize = PadKeySize.LARGE)

    // Family-specific middle sections.
    when (family) {
        PadFamily.AIR_TREATMENT -> {
            val mist = claim { ButtonNames.humidityKey(it) }
            if (mist.isNotEmpty()) rows += PadRow(R.string.appliance_humidity, PadRowStyle.CHIPS, keys = mist.take(24), keySize = PadKeySize.LARGE)
        }

        PadFamily.AUDIO -> {
            val vol = claim { ButtonNames.volUp(it) }
            val volDn = claim { ButtonNames.volDown(it) }
            val mute = claim { ButtonNames.mute(it) }
            if (vol.isNotEmpty() && volDn.isNotEmpty()) {
                rows += PadRow(R.string.appliance_volume, PadRowStyle.STEPPER, downKey = volDn.first(), upKey = vol.first())
            }
            if (mute.isNotEmpty()) rows += PadRow(R.string.appliance_mute, PadRowStyle.CHIPS, keys = mute.take(24), keySize = PadKeySize.LARGE)
            val src = claim { ButtonNames.audioSourceKey(it) }
                        if (src.isNotEmpty()) rows += PadRow(R.string.appliance_input, PadRowStyle.CHIPS, keys = src.take(24), keySize = PadKeySize.LARGE)
            val sound = claim { ButtonNames.audioModeKey(it) }
            if (sound.isNotEmpty()) rows += PadRow(R.string.appliance_sound_modes, PadRowStyle.CHIPS, keys = sound.take(24), keySize = PadKeySize.LARGE)
            val tone = claim { ButtonNames.bassTrebleKey(it) }
            if (tone.isNotEmpty()) rows += PadRow(R.string.appliance_sound_modes, PadRowStyle.CHIPS, keys = tone.take(24), keySize = PadKeySize.LARGE)
            val play = claim { ButtonNames.transportKey(it) }
            if (play.isNotEmpty()) rows += PadRow(R.string.appliance_playback, PadRowStyle.CHIPS, keys = play.take(24), keySize = PadKeySize.LARGE)
        }

        PadFamily.DISPLAY -> {
            val dpad = listOfNotNull(
                claim { ButtonNames.up(it) }.firstOrNull(),
                claim { ButtonNames.left(it) }.firstOrNull(),
                claim { ButtonNames.ok(it) }.firstOrNull(),
                claim { ButtonNames.right(it) }.firstOrNull(),
                claim { ButtonNames.down(it) }.firstOrNull(),
            )
            if (dpad.size >= 3) rows += PadRow(R.string.appliance_navigation, PadRowStyle.D_PAD, keys = dpad)
            val nav = claim {
                ButtonNames.home(it) || ButtonNames.menu(it) || ButtonNames.back(it) || ButtonNames.guide(it)
            }
            if (nav.isNotEmpty()) rows += PadRow(
                if (dpad.isEmpty()) R.string.appliance_navigation else R.string.appliance_menu,
                PadRowStyle.CHIPS, keys = nav.take(24), keySize = PadKeySize.LARGE,
            )
            val src = claim { ButtonNames.source(it) }
                        if (src.isNotEmpty()) rows += PadRow(R.string.appliance_input, PadRowStyle.CHIPS, keys = src.take(24), keySize = PadKeySize.LARGE)
            val vol = claim { ButtonNames.volUp(it) }
            val volDn = claim { ButtonNames.volDown(it) }
            val mute = claim { ButtonNames.mute(it) }
            if (vol.isNotEmpty() && volDn.isNotEmpty()) {
                rows += PadRow(R.string.appliance_volume, PadRowStyle.STEPPER, downKey = volDn.first(), upKey = vol.first())
            }
            if (mute.isNotEmpty()) rows += PadRow(R.string.appliance_mute, PadRowStyle.CHIPS, keys = mute.take(24), keySize = PadKeySize.LARGE)
            val tools = claim { ButtonNames.projectorKey(it) }
            if (tools.isNotEmpty()) rows += PadRow(R.string.appliance_effects, PadRowStyle.CHIPS, keys = tools.take(24), keySize = PadKeySize.LARGE)
        }

        PadFamily.LIGHTING -> {
            val colors = claim { ButtonNames.colorKey(it) }
            if (colors.isNotEmpty()) rows += PadRow(R.string.appliance_colors, PadRowStyle.CHIPS, keys = colors.take(24), keySize = PadKeySize.LARGE)
            val bright = claim { ButtonNames.brightnessKey(it) }
            if (bright.isNotEmpty()) rows += PadRow(R.string.appliance_brightness, PadRowStyle.CHIPS, keys = bright.take(24), keySize = PadKeySize.LARGE)
            val fx = claim { ButtonNames.effectKey(it) }
            if (fx.isNotEmpty()) rows += PadRow(R.string.appliance_effects, PadRowStyle.CHIPS, keys = fx.take(24), keySize = PadKeySize.LARGE)
        }

        PadFamily.CLEANING -> {
            val start = claim { ButtonNames.startStopKey(it) }
            if (start.isNotEmpty()) rows += PadRow(R.string.ac_power, PadRowStyle.CHIPS, keys = start.take(24), keySize = PadKeySize.LARGE)
            val clean = claim { ButtonNames.cleaningKey(it) }
            if (clean.isNotEmpty()) rows += PadRow(R.string.appliance_cleaning, PadRowStyle.CHIPS, keys = clean.take(24), keySize = PadKeySize.LARGE)
            val dock = claim { ButtonNames.dockKey(it) }
            if (dock.isNotEmpty()) rows += PadRow(R.string.appliance_cleaning, PadRowStyle.CHIPS, keys = dock.take(24), keySize = PadKeySize.LARGE)
        }

        PadFamily.CLIMATE -> Unit
        PadFamily.TV -> Unit
    }

    // Keypad, then leftovers. A remote's odd keys must stay reachable — but
    // they arrive as several separate `claim` results that all used to render
    // as their own "More keys" row, so a projector showed the same heading
    // three times. Collect them into one row so the heading appears once.
    // A keypad only when the remote really is a numeric pad. Colour keys on a
    // light strip are named B1..B4/G1..G4, which `digit()` also accepts — that
    // is not a keypad, so require most of 0-9 to be present. Otherwise the
    // digit-ish labels stay unclaimed and fall through to the leftovers row.
    val numeric = available.mapNotNull { it.name.lastOrNull { c -> c.isDigit() } }.toSet()
    if (numeric.size >= 8) {
        val digits = claim { ButtonNames.digit(it) }
        rows += PadRow(R.string.appliance_keypad, PadRowStyle.KEYPAD, keys = digits.take(12))
    }
    val rest = claim { true }
    if (rest.isNotEmpty()) rows += PadRow(R.string.appliance_more, PadRowStyle.CHIPS, keys = rest.take(24))

    // Two rules can land on the same heading — a vacuum has a bare "Mode" key
    // and mode presets, a projector has a d-pad and menu keys that both call
    // themselves "navigation". Printed twice it reads as a bug. Fold any
    // consecutive pair that shares a heading into one row.
    return rows.fold(mutableListOf<PadRow>()) { acc, row ->
        val prev = acc.lastOrNull()
        if (prev != null && prev.style == PadRowStyle.CHIPS && row.style == PadRowStyle.CHIPS &&
            prev.labelRes == row.labelRes && prev.labelOverride == row.labelOverride
        ) {
            acc[acc.lastIndex] = prev.copy(keys = (prev.keys + row.keys).distinctBy { it.name }.take(24))
        } else acc += row
        acc
    }
}

