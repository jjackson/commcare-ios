package org.javarosa.core.model.data

import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.model.utils.PlatformDate

/**
 * A response to a question requesting a DateTime Value
 *
 * @author Clayton Sims
 */
class DateTimeData : IAnswerData {
    var d: PlatformDate? = null

    /**
     * Empty Constructor, necessary for dynamic construction during deserialization.
     * Shouldn't be used otherwise.
     */
    constructor()

    constructor(d: PlatformDate) {
        setValue(d)
    }

    override fun clone(): IAnswerData {
        return DateTimeData(PlatformDate(d!!.getTime()))
    }

    override fun setValue(o: Any?) {
        // Should not ever be possible to set this to a null value
        if (o == null) {
            throw NullPointerException("Attempt to set an IAnswerData class to null.")
        }
        d = PlatformDate((o as PlatformDate).getTime())
    }

    override fun getValue(): Any {
        return PlatformDate(d!!.getTime())
    }

    override fun getDisplayText(): String {
        return DateUtils.formatDateTime(d, DateUtils.FORMAT_HUMAN_READABLE_SHORT)
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        setValue(ExtUtil.readDate(`in`))
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeDate(out, d!!)
    }

    override fun uncast(): UncastData {
        return UncastData(DateUtils.formatDateTime(d, DateUtils.FORMAT_ISO8601))
    }

    override fun cast(data: UncastData): DateTimeData {
        val ret = DateUtils.parseDateTime(data.value!!)
        if (ret != null) {
            return DateTimeData(ret)
        }

        throw IllegalArgumentException("Invalid cast of data [" + data.value + "] to type DateTime")
    }
}
