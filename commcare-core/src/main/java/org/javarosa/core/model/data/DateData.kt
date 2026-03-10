package org.javarosa.core.model.data

import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.model.utils.PlatformDate

/**
 * A response to a question requesting a Date Value
 *
 * @author Drew Roos
 */
class DateData : IAnswerData {
    var d: PlatformDate? = null
    private var mInit: Boolean = false

    /**
     * Empty Constructor, necessary for dynamic construction during deserialization.
     * Shouldn't be used otherwise.
     */
    constructor()

    constructor(d: PlatformDate) {
        setValue(d)
    }

    private fun init() {
        if (!mInit) {
            d = DateUtils.roundDate(d!!)
            mInit = true
        }
    }

    override fun clone(): IAnswerData {
        init()
        return DateData(PlatformDate(d!!.time))
    }

    override fun setValue(o: Any?) {
        // Should not ever be possible to set this to a null value
        if (o == null) {
            throw NullPointerException("Attempt to set an IAnswerData class to null.")
        }
        d = o as PlatformDate
        mInit = false
    }

    override fun getValue(): Any {
        init()
        return PlatformDate(d!!.time)
    }

    override fun getDisplayText(): String {
        init()
        return DateUtils.formatDate(d, DateUtils.FORMAT_HUMAN_READABLE_SHORT)
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        setValue(ExtUtil.readDate(`in`))
        init()
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        init()
        ExtUtil.writeDate(out, d!!)
    }

    override fun uncast(): UncastData {
        init()
        return UncastData(DateUtils.formatDate(d, DateUtils.FORMAT_ISO8601))
    }

    override fun cast(data: UncastData): DateData {
        val ret = DateUtils.parseDate(data.value!!)
        if (ret != null) {
            return DateData(ret)
        }

        throw IllegalArgumentException("Invalid cast of data [" + data.value + "] to type Date")
    }
}
