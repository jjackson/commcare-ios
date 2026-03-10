package org.commcare.suite.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Apps use this model to convey the types of credentials it issues
 */
class Credential : Externalizable {
    private var level: String? = null
    private var type: String? = null

    /**
     * Serialization Only!!!
     */
    constructor()

    constructor(level: String?, type: String?) {
        this.level = level
        this.type = type
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        level = ExtUtil.readString(`in`)
        type = ExtUtil.readString(`in`)
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeString(out, level!!)
        ExtUtil.writeString(out, type!!)
    }

    fun getLevel(): String? = level

    fun getType(): String? = type

    override fun toString(): String {
        return "Credential{level='$level', type='$type'}"
    }
}
