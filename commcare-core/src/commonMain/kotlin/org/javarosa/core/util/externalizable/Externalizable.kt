package org.javarosa.core.util.externalizable

/**
 * Gives objects control over serialization. A replacement for the interfaces
 * `Externalizable` and `Serializable`, which are
 * missing in CLDC.
 *
 * @author Matthias Nuessler (m.nuessler@gmail.com)
 */
interface Externalizable {

    /**
     * Read the object from stream.
     */
    @Throws(PlatformIOException::class, DeserializationException::class)
    fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory)

    /**
     * Write the object to stream.
     */
    @Throws(PlatformIOException::class)
    fun writeExternal(out: PlatformDataOutputStream)
}
