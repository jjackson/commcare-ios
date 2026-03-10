package org.commcare.suite.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream

import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Model for defining a CommCare app dependencies on other Android apps
 */
class AndroidPackageDependency : Externalizable {
    private var id: String? = null

    /**
     * Serialization Only!!!
     */
    constructor()

    constructor(id: String?) {
        this.id = id
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        id = ExtUtil.readString(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeString(out, id!!)
    }

    fun getId(): String? = id

    override fun toString(): String {
        return "AndroidPackageDependency{id='$id'}"
    }
}
