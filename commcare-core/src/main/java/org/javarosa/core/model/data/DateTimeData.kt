package org.javarosa.core.model.data

import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.Date

/**
 * A response to a question requesting a DateTime Value
 *
 * @author Clayton Sims
 */
class DateTimeData : IAnswerData {
    var d: Date? = null

    /**
     * Empty Constructor, necessary for dynamic construction during deserialization.
     * Shouldn't be used otherwise.
     */
    constructor()

    constructor(d: Date) {
        setValue(d)
    }

    override fun clone(): IAnswerData {
        return DateTimeData(Date(d!!.time))
    }

    override fun setValue(o: Any?) {
        // Should not ever be possible to set this to a null value
        if (o == null) {
            throw NullPointerException("Attempt to set an IAnswerData class to null.")
        }
        d = Date((o as Date).time)
    }

    override fun getValue(): Any {
        return Date(d!!.time)
    }

    override fun getDisplayText(): String {
        return DateUtils.formatDateTime(d, DateUtils.FORMAT_HUMAN_READABLE_SHORT)
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        setValue(ExtUtil.readDate(`in`))
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
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
