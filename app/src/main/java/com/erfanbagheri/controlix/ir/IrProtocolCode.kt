package com.erfanbagheri.controlix.ir

import com.erfanbagheri.controlix.ir.protocols.Aiwa
import com.erfanbagheri.controlix.ir.protocols.DenonK
import com.erfanbagheri.controlix.ir.protocols.DishPlayer
import com.erfanbagheri.controlix.ir.protocols.Gi4dtv
import com.erfanbagheri.controlix.ir.protocols.Jerrold
import com.erfanbagheri.controlix.ir.protocols.Lumagen
import com.erfanbagheri.controlix.ir.protocols.Samsung20
import com.erfanbagheri.controlix.ir.protocols.TeacK
import com.erfanbagheri.controlix.ir.protocols.Jvc
import com.erfanbagheri.controlix.ir.protocols.Kaseikyo
import com.erfanbagheri.controlix.ir.protocols.Nec
import com.erfanbagheri.controlix.ir.protocols.Nec42
import com.erfanbagheri.controlix.ir.protocols.NecExt
import com.erfanbagheri.controlix.ir.protocols.Pioneer
import com.erfanbagheri.controlix.ir.protocols.Rc5
import com.erfanbagheri.controlix.ir.protocols.Rc5x
import com.erfanbagheri.controlix.ir.protocols.Rc6
import com.erfanbagheri.controlix.ir.protocols.Rca
import com.erfanbagheri.controlix.ir.protocols.Samsung32
import com.erfanbagheri.controlix.ir.protocols.SharpDenon
import com.erfanbagheri.controlix.ir.protocols.Sirc
import com.erfanbagheri.controlix.ir.protocols.Xmp
import com.erfanbagheri.controlix.ir.protocols.Bose
import com.erfanbagheri.controlix.ir.protocols.Gxb
import com.erfanbagheri.controlix.ir.protocols.Logitech
import com.erfanbagheri.controlix.ir.protocols.PaceMss

/**
 * Bridges the public file-format spelling of a protocol ("NEC", "SIRC15",
 * "Kaseikyo", ...) onto the app's existing encoders in
 * `com.erfanbagheri.controlix.ir.protocols`. One source of truth for
 * carrier + pattern, so imported codes land on the same wire format as
 * codes read from the bundled database.
 */
internal object IrProtocolCode {

    /** Carrier + pattern for a parsed (protocol, address, command) triple. */
    fun encode(protocol: String, address: Int, command: Int): Pair<Int, IntArray>? {
        val key = protocol.uppercase().trim().replace(Regex("[^A-Z0-9]"), "")
        return when (key) {
            "NEC" -> Nec.CARRIER_HZ to Nec.encode(address and 0xFF, command and 0xFF)
            "NECEXT" -> NecExt.CARRIER_HZ to NecExt.encode(
                address and 0xFF, (address shr 8) and 0xFF, command and 0xFF
            )
            "NEC42" -> Nec42.CARRIER_HZ to Nec42.encode(address and 0x1FFF, command and 0xFF)
            "NECEXTENDED" -> Nec42.CARRIER_HZ to Nec42.encodeExt(
                address and 0x3FFFFFF, command and 0xFFFF
            )
            "SAMSUNG", "SAMSUNG32" ->
                Samsung32.CARRIER_HZ to Samsung32.encode(address and 0xFF, command and 0xFF)
            "SIRC", "SIRC12" ->
                Sirc.CARRIER_HZ to Sirc.encode12(command and 0x7F, address and 0x1F)
            "SIRC15" -> Sirc.CARRIER_HZ to Sirc.encode15(command and 0x7F, address and 0xFF)
            "SIRC20" -> Sirc.CARRIER_HZ to Sirc.encode20(command and 0x7F, address and 0x1FFF)
            "RC5" -> Rc5.CARRIER_HZ to Rc5.encode(address and 0x1F, command and 0x3F)
            "RC5X", "RC5EXT" -> Rc5x.CARRIER_HZ to Rc5x.encode(address and 0x1F, command and 0x7F)
            "RC6" -> Rc6.CARRIER_HZ to Rc6.encode(address and 0xFF, command and 0xFF)
            "KASEIKYO" -> Kaseikyo.CARRIER_HZ to Kaseikyo.encode(address, command and 0x3FF)
            "RCA" -> Rca.CARRIER_HZ to Rca.encode(address and 0xF, command and 0xFF)
            "PIONEER" -> Pioneer.CARRIER_HZ to Pioneer.encode(address and 0xFF, command and 0xFF)
            "JVC" -> Jvc.CARRIER_HZ to Jvc.encode(address and 0xFF, command and 0xFF)
            "AIWA" -> Aiwa.CARRIER_HZ to Aiwa.encode(
                address and 0xFF, (address shr 8) and 0x1F, command and 0xFF
            )
            "SHARP" ->
                SharpDenon.CARRIER_HZ to SharpDenon.encodeSharp(address and 0x1F, command and 0xFF)
            "DENON" ->
                SharpDenon.CARRIER_HZ to SharpDenon.encodeDenon(address and 0x1F, command and 0xFF)
            "DENONK" -> DenonK.CARRIER_HZ to DenonK.encode(address, command)
            "JERROLD" -> Jerrold.CARRIER_HZ to Jerrold.encode(command)
            "GI4DTV" -> Gi4dtv.CARRIER_HZ to Gi4dtv.encode(address and 0xFF, command and 0xFF)
            "LUMAGEN" -> Lumagen.CARRIER_HZ to Lumagen.encode(address and 0xF, command and 0x7F)
            "SAMSUNG20" -> Samsung20.CARRIER_HZ to Samsung20.encode(address and 0xFFF, command and 0xFF)
            "TEACK" -> TeacK.CARRIER_HZ to TeacK.encode(address and 0xFFF, command and 0xFF)
            "DISHPLAYER", "DISHPLAYERNETWORK" ->
                DishPlayer.CARRIER_HZ to DishPlayer.encode(address and 0x3FF, command and 0x3F)
            "XMP", "XMP1", "XMP2" ->
                Xmp.CARRIER_HZ to Xmp.encode(address and 0xFFFF, command and 0x3FF)
            "BOSE" -> Bose.CARRIER_HZ to Bose.encode(command and 0xFF)
            "PACEMSS" -> PaceMss.CARRIER_HZ to PaceMss.encode(0, address and 0x1, command and 0xFF)
            "GXB" -> Gxb.CARRIER_HZ to Gxb.encode(address and 0xF, command and 0xFF)
            "LOGITECH" -> Logitech.CARRIER_HZ to Logitech.encode(address and 0xF, command and 0xFF)
            else -> null
        }
    }

    /**
     * Flipper stores address/command as up to four little-endian bytes
     * (`07 00 00 00`); a lone hex word (`012345`) is read as one value.
     * Returns null when any token is not hex.
     */
    fun leBytes(tokens: String): Int? {
        val parts = tokens.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.size > 4) return null
        if (parts.size == 1) return parts[0].removePrefix("0x").toIntOrNull(16)
        var out = 0
        for ((i, part) in parts.withIndex()) {
            val clean = part.removePrefix("0x").removePrefix("0X")
            if (clean.length > 2) return null
            val value = clean.toIntOrNull(16) ?: return null
            out = out or ((value and 0xFF) shl (i * 8))
        }
        return out
    }
}
