package org.javarosa.core.log

import org.javarosa.core.model.utils.PlatformDate
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.PrototypeFactory

open class LogEntry : Externalizable {

    var time: PlatformDate? = null
        protected set
    var type: String? = null
        protected set
    var message: String? = null
        protected set

    constructor()

    constructor(type: String?, message: String?, time: PlatformDate?) {
        this.time = time
        this.type = type
        this.message = message
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        time = ExtUtil.readDate(`in`)
        type = ExtUtil.readString(`in`)
        message = ExtUtil.readString(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeDate(out, time ?: PlatformDate())
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(type))
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(message))
    }

    companion object {
        const val STORAGE_KEY = "LOG"
    }
}
