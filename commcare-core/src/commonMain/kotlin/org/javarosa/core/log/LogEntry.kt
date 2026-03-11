package org.javarosa.core.log

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.core.model.utils.PlatformDate
import kotlin.jvm.JvmField

open class LogEntry : Externalizable {

    @JvmField
    protected var time: PlatformDate? = null

    @JvmField
    protected var type: String? = null

    @JvmField
    protected var message: String? = null

    constructor() {
        // for externalization
    }

    constructor(type: String?, message: String?, time: PlatformDate?) {
        this.time = time
        this.type = type
        this.message = message
    }

    fun getTime(): PlatformDate? = time
    fun getType(): String? = type
    fun getMessage(): String? = message

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        time = PlatformDate(`in`.readLong())
        type = SerializationHelpers.readString(`in`)
        message = SerializationHelpers.readString(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        out.writeLong(time!!.getTime())
        SerializationHelpers.writeString(out, type ?: "")
        SerializationHelpers.writeString(out, message ?: "")
    }

    companion object {
        const val STORAGE_KEY: String = "LOG"
    }
}
