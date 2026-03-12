package org.commcare.test.utilities

import org.javarosa.core.api.ClassNameHasher
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtilJvm
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.PrototypeFactory

/**
 * A persistable sandbox provides an environment to handle
 * prototypes for object serialization and deserialization.
 *
 * @author ctsims
 */
class PersistableSandbox {
    private val factory: PrototypeFactory = PrototypeFactory(ClassNameHasher())

    @Suppress("UNCHECKED_CAST")
    fun <T : Externalizable> deserialize(`object`: ByteArray, c: Class<T>): T {
        try {
            return ExtUtilJvm.deserialize(`object`, c, factory) as T
        } catch (e: PlatformIOException) {
            throw wrap("Error deserializing: " + c.name, e)
        } catch (e: DeserializationException) {
            throw wrap("Error deserializing: " + c.name, e)
        }
    }

    companion object {
        @JvmStatic
        fun <T : Externalizable> serialize(t: T): ByteArray {
            try {
                val dos = PlatformDataOutputStream()
                t.writeExternal(dos)
                return dos.toByteArray()
            } catch (e: PlatformIOException) {
                throw wrap("Error serializing: " + t.javaClass.name, e)
            }
        }

        @JvmStatic
        fun wrap(message: String, e: Exception): RuntimeException {
            e.printStackTrace()
            val runtimed = RuntimeException(message)
            runtimed.initCause(e)
            return runtimed
        }
    }
}
