package org.javarosa.core.util.externalizable


class ExtWrapIntEncodingUniform : ExtWrapIntEncoding {

    /* serialization */

    constructor(l: Long) {
        `val` = l
    }

    /* deserialization */

    constructor()

    override fun clone(`val`: Any?): ExternalizableWrapper {
        return ExtWrapIntEncodingUniform(numericToLong(`val`!!))
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        var l: Long = 0
        var b: Byte
        var firstByte = true

        do {
            b = `in`.readByte()

            if (firstByte) {
                firstByte = false
                l = if (((b.toInt() shr 6) and 0x01) == 0) 0L else -1L // set initial sign
            }

            l = (l shl 7) or (b.toLong() and 0x7f)
        } while (((b.toInt() shr 7) and 0x01) == 1)

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
        val l = `val` as Long

        var sig = -1
        var k: Long
        do {
            sig++
            k = l shr (sig * 7)
        } while (k < (-1 shl 6) || k > (1 shl 6) - 1) // [-64,63] -- the range we can fit into one byte

        for (i in sig downTo 0) {
            val chunk = ((l shr (i * 7)) and 0x7f).toByte()
            out.writeByte((if (i > 0) 0x80 else 0x00) or chunk.toInt())
        }
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun metaReadExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        // do nothing
    }

    @Throws(PlatformIOException::class)
    override fun metaWriteExternal(out: PlatformDataOutputStream) {
        // do nothing
    }
}
