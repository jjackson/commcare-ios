package org.javarosa.core.util.externalizable

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

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
    @Throws(IOException::class, DeserializationException::class)
    fun readExternal(`in`: DataInputStream, pf: PrototypeFactory)

    /**
     * Write the object to stream.
     */
    @Throws(IOException::class)
    fun writeExternal(out: DataOutputStream)
}
