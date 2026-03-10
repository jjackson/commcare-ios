package org.commcare.suite.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

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
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        id = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        value = ExtUtil.readString(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(id))
        ExtUtil.writeString(out, value)
    }
}
