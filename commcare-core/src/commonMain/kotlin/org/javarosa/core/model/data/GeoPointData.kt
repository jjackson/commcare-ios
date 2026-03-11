package org.javarosa.core.model.data

import org.javarosa.core.util.DataUtil
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * A response to a question requesting an GeoPoint Value.
 *
 * @author Yaw Anokwa
 */
class GeoPointData : IAnswerData {

    // latitude, longitude, and potentially altitude and accuracy data
    private val gp = DoubleArray(4)
    private var len = 2

    /**
     * Empty Constructor, necessary for dynamic construction during
     * deserialization. Shouldn't be used otherwise.
     */
    constructor()

    constructor(gp: DoubleArray) {
        fillArray(gp)
    }

    /**
     * Copy data in argument array into local geopoint array.
     *
     * @param gp double array of max size 4 representing geopoints
     */
    private fun fillArray(gp: DoubleArray) {
        len = gp.size
        for (i in 0 until len) {
            if (i < 2) {
                // don't round lat & lng decimal values
                this.gp[i] = gp[i]
            } else {
                // accuracy & altitude should have their decimal values rounded
                this.gp[i] = roundDecimalUp(gp[i], MAX_DECIMAL_ACCURACY)
            }
        }
    }

    override fun clone(): IAnswerData {
        return GeoPointData(gp)
    }

    override fun getDisplayText(): String {
        var s = ""
        for (i in 0 until len) {
            s += gp[i].toString() + " "
        }
        return s.trim()
    }

    override fun getValue(): DoubleArray {
        return gp
    }

    fun getLatitude(): Double = gp[0]

    fun getLongitude(): Double = gp[1]

    fun getAltitude(): Double = gp[2]

    fun getAccuracy(): Double = gp[3]

    override fun setValue(o: Any?) {
        if (o == null) {
            throw NullPointerException("Attempt to set an IAnswerData class to null.")
        }
        fillArray(o as DoubleArray)
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        len = SerializationHelpers.readNumeric(`in`).toInt()
        for (i in 0 until len) {
            gp[i] = SerializationHelpers.readDecimal(`in`)
        }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeNumeric(out, len.toLong())
        for (i in 0 until len) {
            SerializationHelpers.writeDecimal(out, gp[i])
        }
    }

    override fun uncast(): UncastData {
        return UncastData(getDisplayText())
    }

    override fun cast(data: UncastData): GeoPointData {
        val ret = DoubleArray(4)

        val choices = DataUtil.splitOnSpaces(data.value!!)
        if (choices.size < 2) {
            throw IllegalArgumentException("Fewer than two coordinates provided")
        }

        var i = 0
        for (s in choices) {
            val d = s.toDouble()
            ret[i] = d
            ++i
        }
        return GeoPointData(ret)
    }

    companion object {
        // accuracy and altitude data points stored will contain this many decimal
        // points:
        private const val MAX_DECIMAL_ACCURACY = 2

        /**
         * Jenky (but J2ME-compatible) decimal rounding (up) of doubles.
         *
         * Subject to normal double imprecisions and will encounter numerical
         * overflow problems if x * (10^numberofDecimals) is greater than
         * Double.MAX_VALUE or less than Double.MIN_VALUE.
         *
         * @param x                double to be rounded up
         * @param numberOfDecimals number of decimals that should present in result
         */
        private fun roundDecimalUp(x: Double, numberOfDecimals: Int): Double {
            val factor = "1e$numberOfDecimals".toDouble().toInt()
            return kotlin.math.floor(x * factor) / factor
        }
    }
}
