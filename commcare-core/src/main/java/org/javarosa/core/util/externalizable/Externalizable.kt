package org.javarosa.core.util.externalizable

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

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
    fun readExternal(`in`: DataInputStream, pf: PrototypeFactory)

    /**
     * Write the object to stream.
     */
    @Throws(PlatformIOException::class)
    fun writeExternal(out: DataOutputStream)
}
