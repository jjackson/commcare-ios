package org.javarosa.core.util

import kotlin.random.Random

object PropertyUtils {

    /**
     * Generate an RFC 1422 Version 4 UUID.
     *
     * @return a uuid
     */
    @JvmStatic
    fun genUUID(): String {
        return randHex(8) + "-" + randHex(4) + "-4" + randHex(3) + "-" +
                Integer.toString(8 + MathUtils.getRand().nextInt(4), 16) + randHex(3) + "-" + randHex(12)
    }

    /**
     * Create a globally unique identifier string in no particular format
     * with len characters of randomness.
     *
     * @param len The length of the string identifier requested.
     * @return A string containing len characters of random data.
     */
    @JvmStatic
    fun genGUID(len: Int): String {
        val guid = StringBuilder()
        for (i in 0 until len) { // 25 == 128 bits of entropy
            guid.append(Integer.toString(MathUtils.getRand().nextInt(36), 36))
        }
        return guid.toString().uppercase()
    }

    private fun randHex(len: Int): String {
        val ret = StringBuilder()
        val r: Random = MathUtils.getRand()
        for (i in 0 until len) {
            ret.append(Integer.toString(r.nextInt(16), 16))
        }
        return ret.toString()
    }

    @JvmStatic
    fun trim(guid: String, len: Int): String {
        return guid.substring(0, minOf(len, guid.length))
    }
}
