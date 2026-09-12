package com.erfanbagheri.controlix.ir.protocols

/**
 * Batch-2 protocol encoders. Timings verified against flipperzero-firmware
 * C sources (infrared_protocol_*_i.h) and unit test vectors.
 */

/**
 * Kaseikyo (Panasonic/JVC). 37 kHz carrier.
 * Frame: 3456 mark (8×U), 1728 space (4×U), then 48 data bits LSB-first.
 * Bit timings: mark = 432 µs (1×U), bit-1 space = 1296 µs (3×U), bit-0 space = 432 µs (1×U).
 *
 * 48-bit layout (from encoder source):
 *   data[0] = vendor_id & 0xFF
 *   data[1] = vendor_id >> 8
 *   data[2] = vendor_parity_nibble | (genre1 << 4)
 *   data[3] = genre2 | (command_lo << 4)
 *   data[4] = (id << 6) | command_hi
 *   data[5] = data[2] ^ data[3] ^ data[4]
 *
 * Flipper's Kaseikyo address packs: id<<24 | vendor_id<<8 | genre1<<4 | genre2
 * and command packs: data=command_hi<<4|command_lo (10 bits total).
 */
object Kaseikyo {

    const val CARRIER_HZ = 37000
    private const val U = 432
    private const val LEADER_MARK = 8 * U   // 3456
    private const val LEADER_SPACE = 4 * U  // 1728

    fun encode(address: Int, command: Int): IntArray {
        // Unpack Flipper's composite address/command into raw frame bytes
        val id = (address shr 24) and 0x03
        val vendorId = (address shr 8) and 0xFFFF
        val genre1 = (address shr 4) and 0x0F
        val genre2 = address and 0x0F
        val cmdLo = command and 0x0F
        val cmdHi = (command shr 4) and 0x3F

        val data = ByteArray(6)
        data[0] = (vendorId and 0xFF).toByte()
        data[1] = ((vendorId shr 8) and 0xFF).toByte()
        val vp = ((data[0].toInt() xor data[1].toInt()) and 0xFF)
        val vpNib = ((vp and 0x0F) xor (vp shr 4)) and 0x0F
        data[2] = ((vpNib or (genre1 shl 4)) and 0xFF).toByte()
        data[3] = ((genre2 or (cmdLo shl 4)) and 0xFF).toByte()
        data[4] = (((id shl 6) or cmdHi) and 0xFF).toByte()
        data[5] = ((data[2].toInt() xor data[3].toInt() xor data[4].toInt()) and 0xFF).toByte()

        val out = ArrayList<Int>(100)
        out += LEADER_MARK; out += LEADER_SPACE
        // 48 bits LSB-first, pulse-distance
        for (b in data) {
            var v = b.toInt() and 0xFF
            repeat(8) {
                out += U
                out += if (v and 1 == 1) 3 * U else U
                v = v ushr 1
            }
        }
        out += U // trailing mark (stop bit)
        return out.toIntArray()
    }
}

/**
 * RCA. 56 kHz carrier (note: some IR recs use 56 kHz, Flipper's common header
 * defines RCA using INFRARED_COMMON_CARRIER_FREQUENCY — on Flipper that is
 * the system-level 38 kHz default, but real RCA devices broadcast at 56 kHz.
 * We follow the Flipper database which stores 56000 Hz for RCA codes.)
 *
 * 24-bit frame, pulse-distance width encoding:
 *   bit-1 space = 2000 µs, bit-0 space = 1000 µs
 *   mark (both) = 500 µs
 * Layout: [address_lo(4)] [command(8)] [address_hi_inv(4)] [command_inv(8)], LSB first.
 */
object Rca {

    const val CARRIER_HZ = 56000

    fun encode(address: Int, command: Int): IntArray {
        val a = address and 0x0F
        val aInv = (a.inv()) and 0x0F
        val c = command and 0xFF
        val cInv = (c.inv()) and 0xFF
        val out = ArrayList<Int>(52)
        out += 4000; out += 4000 // preamble
        sendBits(out, a, 4)
        sendBits(out, c, 8)
        sendBits(out, aInv, 4)
        sendBits(out, cInv, 8)
        out += 500 // trailing mark
        return out.toIntArray()
    }

    private fun sendBits(out: ArrayList<Int>, value: Int, count: Int) {
        var v = value
        repeat(count) {
            out += 500
            out += if (v and 1 == 1) 2000 else 1000
            v = v ushr 1
        }
    }
}

/**
 * Pioneer. 40 kHz carrier, 33-bit pulse-distance frame (Flipper encodes
 * databit_len[0] = 33: four payload bytes + a constant 0 bit + stop mark).
* Leader: 8500 mark, 4225 space.
* 500/1500 for bit-1, 500/500 for bit-0.
 * Layout: address(8) | ~address(8) | command(8) | ~command(8) | 0, then stop mark.
*/
object Pioneer {

    const val CARRIER_HZ = 40000

    fun encode(address: Int, command: Int): IntArray {
        val a = address and 0xFF
        val c = command and 0xFF
        val out = ArrayList<Int>(70)
        out += 8500; out += 4225
        sendByte(out, a)
        sendByte(out, a.inv())
        sendByte(out, c)
        sendByte(out, c.inv())
        // 33rd bit = 0
        out += 500; out += 500
        // stop mark (frame always ends on a mark)
        out += 500
        return out.toIntArray()
    }

    private fun sendByte(out: ArrayList<Int>, value: Int) {
        var v = value and 0xFF
        repeat(8) {
            out += 500
            out += if (v and 1 == 1) 1500 else 500
            v = v ushr 1
        }
    }
}

/**
 * NEC42: 42-bit NEC-variant frame, same 38 kHz carrier and 560/1690 bit timings as NEC.
 * Layout: address(13) | ~address(13) | command(8) | ~command(8), all LSB-first.
 * 13 bits of inverse address and 6 low bits of command pack into 32-bit word 1,
 * remaining 2 high bits of command + 8 bits of command inverse in word 2.
 */
object Nec42 {

    const val CARRIER_HZ = 38000
    private const val BIT_MARK = 560
    private const val ONE_SPACE = 1690

    /** NEC42: 13-bit address + inverse, then 8-bit command + inverse. */
    fun encode(address: Int, command: Int): IntArray {
        val out = ArrayList<Int>(88)
        out += 9000; out += 4500
        sendBits(out, address and 0x1FFF, 13)
        sendBits(out, (address.inv()) and 0x1FFF, 13)
        sendBits(out, command and 0xFF, 8)
        sendBits(out, (command.inv()) and 0xFF, 8)
        out += BIT_MARK
        return out.toIntArray()
    }

    /**
     * NEC42ext: 26-bit address + 16-bit command, no inverses, still 42 bits.
     * Per encoder source: word1 = address | (command & 0x3F) << 26,
     * word2 = (command & 0xFFC0) >> 6.
     */
    fun encodeExt(address: Int, command: Int): IntArray {
        val out = ArrayList<Int>(88)
        out += 9000; out += 4500
        sendBits(out, address and 0x3FFFFFF, 26)
        sendBits(out, command and 0xFFFF, 16)
        out += BIT_MARK
        return out.toIntArray()
    }

    private fun sendBits(out: ArrayList<Int>, value: Int, count: Int) {
        var v = value
        repeat(count) {
            out += BIT_MARK
            out += if (v and 1 == 1) ONE_SPACE else 560
            v = v ushr 1
        }
    }
}

/**
 * JVC. 38 kHz, 16-bit pulse-distance frame.
 * Preamble 8400/4200, bit mark 525, 0-space 525 / 1-space 1575, LSB first,
 * trailing 525 mark (iodn JvcProtocolEncoder + Flipper infrared_protocol_jvc_i.h
 * agree on every constant).
 */
object Jvc {

    const val CARRIER_HZ = 38000

    fun encode(address: Int, command: Int): IntArray {
        val out = ArrayList<Int>(38)
        out += 8400; out += 4200
        sendByte(out, address and 0xFF)
        sendByte(out, command and 0xFF)
        out += 525
        return out.toIntArray()
    }

    private fun sendByte(out: ArrayList<Int>, value: Int) {
        var v = value and 0xFF
        repeat(8) {
            out += 525
            out += if (v and 1 == 1) 1575 else 525
            v = v ushr 1
        }
    }
}
