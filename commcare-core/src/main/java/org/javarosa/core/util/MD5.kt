package org.javarosa.core.util

/**
 * Fast implementation of RSA's MD5 hash generator in Java JDK Beta-2 or higher.
 *
 * Originally written by Santeri Paavolainen, Helsinki Finland 1996.
 * (c) Santeri Paavolainen, Helsinki Finland 1996
 * Many changes Copyright (c) 2002 - 2005 Timothy W Macinta
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Library General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this library; if not, write to the Free Software Foundation, Inc.,
 * 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 * See http://www.twmacinta.com/myjava/fast_md5.php for more information on this
 * file and the related files.
 *
 * This was originally a rather straight re-implementation of the reference
 * implementation given in RFC1321 by RSA. It passes the MD5 test suite as
 * defined in RFC1321.
 *
 * Many optimizations made by Timothy W Macinta. Reduced time to checksum a test
 * file in Java alone to roughly half the time taken compared with
 * java.security.MessageDigest (within an interpreter). Also added an optional
 * native method to reduce the time even further. See
 * http://www.twmacinta.com/myjava/fast_md5.php for further information on the
 * time improvements achieved.
 *
 * Some bug fixes also made by Timothy W Macinta.
 *
 * @author Santeri Paavolainen <sjpaavol@cc.helsinki.fi>
 * @author Timothy W Macinta (twm@alum.mit.edu) (optimizations and bug fixes)
 */
class MD5(data: ByteArray) {
    private val state: MD5State = MD5State()
    private var finals: MD5State? = null

    init {
        update(data)
    }

    private fun decode(buffer: ByteArray, shift: Int, out: IntArray) {
        out[0] = (buffer[shift].toInt() and 0xff) or ((buffer[shift + 1].toInt() and 0xff) shl 8) or
                ((buffer[shift + 2].toInt() and 0xff) shl 16) or (buffer[shift + 3].toInt() shl 24)
        out[1] = (buffer[shift + 4].toInt() and 0xff) or ((buffer[shift + 5].toInt() and 0xff) shl 8) or
                ((buffer[shift + 6].toInt() and 0xff) shl 16) or (buffer[shift + 7].toInt() shl 24)
        out[2] = (buffer[shift + 8].toInt() and 0xff) or ((buffer[shift + 9].toInt() and 0xff) shl 8) or
                ((buffer[shift + 10].toInt() and 0xff) shl 16) or (buffer[shift + 11].toInt() shl 24)
        out[3] = (buffer[shift + 12].toInt() and 0xff) or ((buffer[shift + 13].toInt() and 0xff) shl 8) or
                ((buffer[shift + 14].toInt() and 0xff) shl 16) or (buffer[shift + 15].toInt() shl 24)
        out[4] = (buffer[shift + 16].toInt() and 0xff) or ((buffer[shift + 17].toInt() and 0xff) shl 8) or
                ((buffer[shift + 18].toInt() and 0xff) shl 16) or (buffer[shift + 19].toInt() shl 24)
        out[5] = (buffer[shift + 20].toInt() and 0xff) or ((buffer[shift + 21].toInt() and 0xff) shl 8) or
                ((buffer[shift + 22].toInt() and 0xff) shl 16) or (buffer[shift + 23].toInt() shl 24)
        out[6] = (buffer[shift + 24].toInt() and 0xff) or ((buffer[shift + 25].toInt() and 0xff) shl 8) or
                ((buffer[shift + 26].toInt() and 0xff) shl 16) or (buffer[shift + 27].toInt() shl 24)
        out[7] = (buffer[shift + 28].toInt() and 0xff) or ((buffer[shift + 29].toInt() and 0xff) shl 8) or
                ((buffer[shift + 30].toInt() and 0xff) shl 16) or (buffer[shift + 31].toInt() shl 24)
        out[8] = (buffer[shift + 32].toInt() and 0xff) or ((buffer[shift + 33].toInt() and 0xff) shl 8) or
                ((buffer[shift + 34].toInt() and 0xff) shl 16) or (buffer[shift + 35].toInt() shl 24)
        out[9] = (buffer[shift + 36].toInt() and 0xff) or ((buffer[shift + 37].toInt() and 0xff) shl 8) or
                ((buffer[shift + 38].toInt() and 0xff) shl 16) or (buffer[shift + 39].toInt() shl 24)
        out[10] = (buffer[shift + 40].toInt() and 0xff) or ((buffer[shift + 41].toInt() and 0xff) shl 8) or
                ((buffer[shift + 42].toInt() and 0xff) shl 16) or (buffer[shift + 43].toInt() shl 24)
        out[11] = (buffer[shift + 44].toInt() and 0xff) or ((buffer[shift + 45].toInt() and 0xff) shl 8) or
                ((buffer[shift + 46].toInt() and 0xff) shl 16) or (buffer[shift + 47].toInt() shl 24)
        out[12] = (buffer[shift + 48].toInt() and 0xff) or ((buffer[shift + 49].toInt() and 0xff) shl 8) or
                ((buffer[shift + 50].toInt() and 0xff) shl 16) or (buffer[shift + 51].toInt() shl 24)
        out[13] = (buffer[shift + 52].toInt() and 0xff) or ((buffer[shift + 53].toInt() and 0xff) shl 8) or
                ((buffer[shift + 54].toInt() and 0xff) shl 16) or (buffer[shift + 55].toInt() shl 24)
        out[14] = (buffer[shift + 56].toInt() and 0xff) or ((buffer[shift + 57].toInt() and 0xff) shl 8) or
                ((buffer[shift + 58].toInt() and 0xff) shl 16) or (buffer[shift + 59].toInt() shl 24)
        out[15] = (buffer[shift + 60].toInt() and 0xff) or ((buffer[shift + 61].toInt() and 0xff) shl 8) or
                ((buffer[shift + 62].toInt() and 0xff) shl 16) or (buffer[shift + 63].toInt() shl 24)
    }

    private fun transform(state: MD5State, buffer: ByteArray, shift: Int, decodeBuf: IntArray) {
        var a = state.state[0]
        var b = state.state[1]
        var c = state.state[2]
        var d = state.state[3]
        val x = decodeBuf

        decode(buffer, shift, decodeBuf)

        /* Round 1 */
        a += ((b and c) or (b.inv() and d)) + x[0] + 0xd76aa478.toInt() /* 1 */
        a = ((a shl 7) or (a ushr 25)) + b
        d += ((a and b) or (a.inv() and c)) + x[1] + 0xe8c7b756.toInt() /* 2 */
        d = ((d shl 12) or (d ushr 20)) + a
        c += ((d and a) or (d.inv() and b)) + x[2] + 0x242070db /* 3 */
        c = ((c shl 17) or (c ushr 15)) + d
        b += ((c and d) or (c.inv() and a)) + x[3] + 0xc1bdceee.toInt() /* 4 */
        b = ((b shl 22) or (b ushr 10)) + c

        a += ((b and c) or (b.inv() and d)) + x[4] + 0xf57c0faf.toInt() /* 5 */
        a = ((a shl 7) or (a ushr 25)) + b
        d += ((a and b) or (a.inv() and c)) + x[5] + 0x4787c62a /* 6 */
        d = ((d shl 12) or (d ushr 20)) + a
        c += ((d and a) or (d.inv() and b)) + x[6] + 0xa8304613.toInt() /* 7 */
        c = ((c shl 17) or (c ushr 15)) + d
        b += ((c and d) or (c.inv() and a)) + x[7] + 0xfd469501.toInt() /* 8 */
        b = ((b shl 22) or (b ushr 10)) + c

        a += ((b and c) or (b.inv() and d)) + x[8] + 0x698098d8 /* 9 */
        a = ((a shl 7) or (a ushr 25)) + b
        d += ((a and b) or (a.inv() and c)) + x[9] + 0x8b44f7af.toInt() /* 10 */
        d = ((d shl 12) or (d ushr 20)) + a
        c += ((d and a) or (d.inv() and b)) + x[10] + 0xffff5bb1.toInt() /* 11 */
        c = ((c shl 17) or (c ushr 15)) + d
        b += ((c and d) or (c.inv() and a)) + x[11] + 0x895cd7be.toInt() /* 12 */
        b = ((b shl 22) or (b ushr 10)) + c

        a += ((b and c) or (b.inv() and d)) + x[12] + 0x6b901122 /* 13 */
        a = ((a shl 7) or (a ushr 25)) + b
        d += ((a and b) or (a.inv() and c)) + x[13] + 0xfd987193.toInt() /* 14 */
        d = ((d shl 12) or (d ushr 20)) + a
        c += ((d and a) or (d.inv() and b)) + x[14] + 0xa679438e.toInt() /* 15 */
        c = ((c shl 17) or (c ushr 15)) + d
        b += ((c and d) or (c.inv() and a)) + x[15] + 0x49b40821 /* 16 */
        b = ((b shl 22) or (b ushr 10)) + c

        /* Round 2 */
        a += ((b and d) or (c and d.inv())) + x[1] + 0xf61e2562.toInt() /* 17 */
        a = ((a shl 5) or (a ushr 27)) + b
        d += ((a and c) or (b and c.inv())) + x[6] + 0xc040b340.toInt() /* 18 */
        d = ((d shl 9) or (d ushr 23)) + a
        c += ((d and b) or (a and b.inv())) + x[11] + 0x265e5a51 /* 19 */
        c = ((c shl 14) or (c ushr 18)) + d
        b += ((c and a) or (d and a.inv())) + x[0] + 0xe9b6c7aa.toInt() /* 20 */
        b = ((b shl 20) or (b ushr 12)) + c

        a += ((b and d) or (c and d.inv())) + x[5] + 0xd62f105d.toInt() /* 21 */
        a = ((a shl 5) or (a ushr 27)) + b
        d += ((a and c) or (b and c.inv())) + x[10] + 0x02441453 /* 22 */
        d = ((d shl 9) or (d ushr 23)) + a
        c += ((d and b) or (a and b.inv())) + x[15] + 0xd8a1e681.toInt() /* 23 */
        c = ((c shl 14) or (c ushr 18)) + d
        b += ((c and a) or (d and a.inv())) + x[4] + 0xe7d3fbc8.toInt() /* 24 */
        b = ((b shl 20) or (b ushr 12)) + c

        a += ((b and d) or (c and d.inv())) + x[9] + 0x21e1cde6 /* 25 */
        a = ((a shl 5) or (a ushr 27)) + b
        d += ((a and c) or (b and c.inv())) + x[14] + 0xc33707d6.toInt() /* 26 */
        d = ((d shl 9) or (d ushr 23)) + a
        c += ((d and b) or (a and b.inv())) + x[3] + 0xf4d50d87.toInt() /* 27 */
        c = ((c shl 14) or (c ushr 18)) + d
        b += ((c and a) or (d and a.inv())) + x[8] + 0x455a14ed /* 28 */
        b = ((b shl 20) or (b ushr 12)) + c

        a += ((b and d) or (c and d.inv())) + x[13] + 0xa9e3e905.toInt() /* 29 */
        a = ((a shl 5) or (a ushr 27)) + b
        d += ((a and c) or (b and c.inv())) + x[2] + 0xfcefa3f8.toInt() /* 30 */
        d = ((d shl 9) or (d ushr 23)) + a
        c += ((d and b) or (a and b.inv())) + x[7] + 0x676f02d9 /* 31 */
        c = ((c shl 14) or (c ushr 18)) + d
        b += ((c and a) or (d and a.inv())) + x[12] + 0x8d2a4c8a.toInt() /* 32 */
        b = ((b shl 20) or (b ushr 12)) + c

        /* Round 3 */
        a += (b xor c xor d) + x[5] + 0xfffa3942.toInt() /* 33 */
        a = ((a shl 4) or (a ushr 28)) + b
        d += (a xor b xor c) + x[8] + 0x8771f681.toInt() /* 34 */
        d = ((d shl 11) or (d ushr 21)) + a
        c += (d xor a xor b) + x[11] + 0x6d9d6122 /* 35 */
        c = ((c shl 16) or (c ushr 16)) + d
        b += (c xor d xor a) + x[14] + 0xfde5380c.toInt() /* 36 */
        b = ((b shl 23) or (b ushr 9)) + c

        a += (b xor c xor d) + x[1] + 0xa4beea44.toInt() /* 37 */
        a = ((a shl 4) or (a ushr 28)) + b
        d += (a xor b xor c) + x[4] + 0x4bdecfa9 /* 38 */
        d = ((d shl 11) or (d ushr 21)) + a
        c += (d xor a xor b) + x[7] + 0xf6bb4b60.toInt() /* 39 */
        c = ((c shl 16) or (c ushr 16)) + d
        b += (c xor d xor a) + x[10] + 0xbebfbc70.toInt() /* 40 */
        b = ((b shl 23) or (b ushr 9)) + c

        a += (b xor c xor d) + x[13] + 0x289b7ec6 /* 41 */
        a = ((a shl 4) or (a ushr 28)) + b
        d += (a xor b xor c) + x[0] + 0xeaa127fa.toInt() /* 42 */
        d = ((d shl 11) or (d ushr 21)) + a
        c += (d xor a xor b) + x[3] + 0xd4ef3085.toInt() /* 43 */
        c = ((c shl 16) or (c ushr 16)) + d
        b += (c xor d xor a) + x[6] + 0x04881d05 /* 44 */
        b = ((b shl 23) or (b ushr 9)) + c

        a += (b xor c xor d) + x[9] + 0xd9d4d039.toInt() /* 33 */
        a = ((a shl 4) or (a ushr 28)) + b
        d += (a xor b xor c) + x[12] + 0xe6db99e5.toInt() /* 34 */
        d = ((d shl 11) or (d ushr 21)) + a
        c += (d xor a xor b) + x[15] + 0x1fa27cf8 /* 35 */
        c = ((c shl 16) or (c ushr 16)) + d
        b += (c xor d xor a) + x[2] + 0xc4ac5665.toInt() /* 36 */
        b = ((b shl 23) or (b ushr 9)) + c

        /* Round 4 */
        a += (c xor (b or d.inv())) + x[0] + 0xf4292244.toInt() /* 49 */
        a = ((a shl 6) or (a ushr 26)) + b
        d += (b xor (a or c.inv())) + x[7] + 0x432aff97 /* 50 */
        d = ((d shl 10) or (d ushr 22)) + a
        c += (a xor (d or b.inv())) + x[14] + 0xab9423a7.toInt() /* 51 */
        c = ((c shl 15) or (c ushr 17)) + d
        b += (d xor (c or a.inv())) + x[5] + 0xfc93a039.toInt() /* 52 */
        b = ((b shl 21) or (b ushr 11)) + c

        a += (c xor (b or d.inv())) + x[12] + 0x655b59c3 /* 53 */
        a = ((a shl 6) or (a ushr 26)) + b
        d += (b xor (a or c.inv())) + x[3] + 0x8f0ccc92.toInt() /* 54 */
        d = ((d shl 10) or (d ushr 22)) + a
        c += (a xor (d or b.inv())) + x[10] + 0xffeff47d.toInt() /* 55 */
        c = ((c shl 15) or (c ushr 17)) + d
        b += (d xor (c or a.inv())) + x[1] + 0x85845dd1.toInt() /* 56 */
        b = ((b shl 21) or (b ushr 11)) + c

        a += (c xor (b or d.inv())) + x[8] + 0x6fa87e4f /* 57 */
        a = ((a shl 6) or (a ushr 26)) + b
        d += (b xor (a or c.inv())) + x[15] + 0xfe2ce6e0.toInt() /* 58 */
        d = ((d shl 10) or (d ushr 22)) + a
        c += (a xor (d or b.inv())) + x[6] + 0xa3014314.toInt() /* 59 */
        c = ((c shl 15) or (c ushr 17)) + d
        b += (d xor (c or a.inv())) + x[13] + 0x4e0811a1 /* 60 */
        b = ((b shl 21) or (b ushr 11)) + c

        a += (c xor (b or d.inv())) + x[4] + 0xf7537e82.toInt() /* 61 */
        a = ((a shl 6) or (a ushr 26)) + b
        d += (b xor (a or c.inv())) + x[11] + 0xbd3af235.toInt() /* 62 */
        d = ((d shl 10) or (d ushr 22)) + a
        c += (a xor (d or b.inv())) + x[2] + 0x2ad7d2bb /* 63 */
        c = ((c shl 15) or (c ushr 17)) + d
        b += (d xor (c or a.inv())) + x[9] + 0xeb86d391.toInt() /* 64 */
        b = ((b shl 21) or (b ushr 11)) + c

        state.state[0] += a
        state.state[1] += b
        state.state[2] += c
        state.state[3] += d
    }

    /**
     * Updates hash with the bytebuffer given (using at maximum length bytes
     * from that buffer)
     *
     * @param stat   Which state is updated
     * @param buffer Array of bytes to be hashed
     * @param offset Offset to buffer array
     * @param length Use at maximum `length` bytes (absolute maximum is
     *               buffer.length)
     */
    private fun update(stat: MD5State, buffer: ByteArray, offset: Int, length: Int) {
        @Suppress("NAME_SHADOWING")
        var length = length
        val i: Int
        val start: Int
        finals = null
        /* Length can be told to be shorter, but not longer */
        if ((length - offset) > buffer.size)
            length = buffer.size - offset

        /* compute number of bytes mod 64 */
        var index = (stat.count and 0x3f).toInt()
        stat.count += length.toLong()

        val partlen = 64 - index

        if (length >= partlen) {
            // update state (using only Java) to reflect input
            val decodeBuf = IntArray(16)
            if (partlen == 64) {
                i = 0
            } else {
                for (j in 0 until partlen)
                    stat.buffer[j + index] = buffer[j + offset]
                transform(stat, stat.buffer, 0, decodeBuf)
                i = partlen
            }
            var ii = i
            while ((ii + 63) < length) {
                transform(stat, buffer, ii + offset, decodeBuf)
                ii += 64
            }
            index = 0
            i.let { /* just need ii as the final value */ }
            // use ii as the position
            start = ii
        } else {
            i = 0
            start = i
        }
        /* buffer remaining input */
        if (start < length) {
            for (j in start until length) {
                stat.buffer[index + j - start] = buffer[j + offset]
            }
        }
    }

    /**
     * Updates hash with given array of bytes
     *
     * @param buffer Array of bytes to use for updating the hash
     */
    fun update(buffer: ByteArray?) {
        if (buffer == null) return
        update(buffer, 0, buffer.size)
    }

    fun update(buffer: ByteArray?, offset: Int, length: Int) {
        if (buffer == null) return
        update(state, buffer, offset, length)
    }

    /**
     * Returns array of bytes (16 bytes) representing hash as of the current
     * state of this object. Note: getting a hash does not invalidate the hash
     * object, it only creates a copy of the real state which is finalized.
     *
     * @return Array of 16 bytes, the hash of all updated bytes
     */
    @Synchronized
    fun doFinal(): ByteArray {
        if (finals == null) {
            val fin = MD5State(state)
            val countInts = intArrayOf((fin.count shl 3).toInt(), (fin.count ushr 29).toInt())
            val bits = encode(countInts, 8)
            val index = (fin.count and 0x3f).toInt()
            val padlen = if (index < 56) (56 - index) else (120 - index)
            update(fin, padding, 0, padlen)
            update(fin, bits, 0, 8)
            /* Update() sets finals to null */
            finals = fin
        }

        return encode(finals!!.state, 16)
    }

    companion object {
        @JvmField
        val length = 16

        private val HEX_CHARS = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
        )

        private val padding = byteArrayOf(
            0x80.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0
        )

        private fun encode(input: IntArray, len: Int): ByteArray {
            val out = ByteArray(len)
            var i = 0
            var j = 0
            while (j < len) {
                out[j] = (input[i] and 0xff).toByte()
                out[j + 1] = ((input[i] ushr 8) and 0xff).toByte()
                out[j + 2] = ((input[i] ushr 16) and 0xff).toByte()
                out[j + 3] = ((input[i] ushr 24) and 0xff).toByte()
                i++
                j += 4
            }
            return out
        }

        /**
         * Returns 32-character hex representation of this objects hash
         *
         * @return String of this object's hash
         */
        @JvmStatic
        fun toHex(hash: ByteArray): String {
            val buf = CharArray(hash.size * 2)
            var x = 0
            for (i in hash.indices) {
                buf[x++] = HEX_CHARS[(hash[i].toInt() ushr 4) and 0xf]
                buf[x++] = HEX_CHARS[hash[i].toInt() and 0xf]
            }
            return String(buf)
        }

        @JvmStatic
        fun hash(data: ByteArray): ByteArray {
            return MD5(data).doFinal()
        }
    }
}
