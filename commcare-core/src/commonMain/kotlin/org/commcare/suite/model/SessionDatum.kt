package org.commcare.suite.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.core.util.externalizable.nullIfEmpty
import org.javarosa.core.util.externalizable.emptyIfNull

/**
 * @author ctsims
 */
open class SessionDatum : Externalizable {
    private var value: String? = null
    private var id: String? = null

    /**
     * Used in serialization
     */
    constructor()

    protected constructor(id: String?, value: String?) {
        this.id = id
        this.value = value
    }

    fun getDataId(): String? = id

    fun getValue(): String? = value

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        id = nullIfEmpty(SerializationHelpers.readString(`in`))
        value = SerializationHelpers.readString(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeString(out, emptyIfNull(id))
        SerializationHelpers.writeString(out, emptyIfNull(value))
    }
}
