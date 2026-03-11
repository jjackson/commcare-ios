package org.javarosa.core.util.externalizable


class ExtWrapIntEncodingSmall : ExtWrapIntEncoding {

    /* max magnitude of negative number encodable in one byte; allowed range [0,254]
     * increasing this steals from the max positive range
     * ex.: BIAS = 0   -> [0,254] will fit in one byte; all other values will overflow
     *      BIAS = 30  -> [-30,224]
     *      BIAS = 254 -> [-254,0]
     */
    private var bias: Int

    /* serialization */

    
    constructor(l: Long, bias: Int = DEFAULT_BIAS) {
        `val` = l
        this.bias = bias
    }

    /* deserialization */

    // need the garbage param or else it conflicts with (long) constructor
    
    constructor(bias: Int = DEFAULT_BIAS) {
        this.bias = bias
    }

    override fun clone(`val`: Any?): ExternalizableWrapper {
        return ExtWrapIntEncodingSmall(numericToLong(`val`!!), bias)
    }

    @Throws(PlatformIOException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        val b = `in`.readByte()
        val l: Long

        if (b == 0xff.toByte()) {
            l = `in`.readInt().toLong()
        } else {
            l = ((if (b < 0) b + 256 else b.toInt()) - bias).toLong()
        }

        `val` = l
    }

    /**
     * serialize a numeric value, only using as many bytes as needed. splits up the value into
     * chunks of 7 bits, using as many chunks as needed to unambiguously represent the value. each
     * chunk is serialized as a single byte, where the most-significant bit is set to 1 to indicate
     * there are more bytes to follow, or 0 to indicate the last byte
     */
    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        val n = numericToInt(`val` as Long)

        if (n >= -bias && n < 255 - bias) {
            val adjusted = n + bias
            out.writeByte(if (adjusted >= 128) adjusted - 256 else adjusted)
        } else {
            out.writeByte(0xff)
            out.writeInt(n)
        }
    }

    @Throws(PlatformIOException::class)
    override fun metaReadExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        bias = `in`.readByte().toInt() and 0xFF
    }

    @Throws(PlatformIOException::class)
    override fun metaWriteExternal(out: PlatformDataOutputStream) {
        out.writeByte(bias)
    }

    companion object {
        const val DEFAULT_BIAS: Int = 1
    }
}
