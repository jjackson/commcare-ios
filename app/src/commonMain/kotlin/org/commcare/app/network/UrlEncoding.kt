package org.commcare.app.network

/**
 * Percent-encode a string for use in application/x-www-form-urlencoded bodies.
 * Unreserved characters (A-Z, a-z, 0-9, '-', '_', '.', '~') are not encoded.
 * Spaces are encoded as '+'. Everything else is percent-encoded.
 */
fun formUrlEncode(value: String): String {
    val sb = StringBuilder(value.length)
    for (byte in value.encodeToByteArray()) {
        val b = byte.toInt() and 0xFF
        when {
            b == 0x20 -> sb.append('+')
            b in 0x30..0x39 || b in 0x41..0x5A || b in 0x61..0x7A ||
                b == 0x2D || b == 0x5F || b == 0x2E || b == 0x7E -> sb.append(b.toChar())
            else -> {
                sb.append('%')
                sb.append(HEX_DIGITS[b shr 4])
                sb.append(HEX_DIGITS[b and 0x0F])
            }
        }
    }
    return sb.toString()
}

private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()
