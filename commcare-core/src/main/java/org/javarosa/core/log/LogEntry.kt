package org.javarosa.core.log

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.model.utils.PlatformDate

/**
 * @author Clayton Sims
 */
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
        time = ExtUtil.readDate(`in`)
        type = ExtUtil.readString(`in`)
        message = ExtUtil.readString(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeDate(out, time!!)
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(type))
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(message))
    }

    companion object {
        const val STORAGE_KEY: String = "LOG"
    }
}
