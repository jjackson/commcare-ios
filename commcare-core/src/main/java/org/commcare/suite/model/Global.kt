package org.commcare.suite.model

import org.javarosa.core.util.ArrayUtilities
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Defines top level UI logic for a case-select or case-detail view,
 * Part of `Detail` model
 */
class Global : Externalizable {
    private lateinit var geoOverlays: Array<GeoOverlay>

    /**
     * Serialization Only
     */
    constructor()

    constructor(geoOverlays: ArrayList<GeoOverlay>) {
        @Suppress("UNCHECKED_CAST")
        this.geoOverlays = ArrayUtilities.copyIntoArray(geoOverlays, arrayOfNulls<GeoOverlay>(geoOverlays.size) as Array<GeoOverlay>)
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        val theGeoOverlays = ExtUtil.read(`in`, ExtWrapList(GeoOverlay::class.java), pf) as ArrayList<GeoOverlay>
        geoOverlays = arrayOfNulls<GeoOverlay>(theGeoOverlays.size) as Array<GeoOverlay>
        ArrayUtilities.copyIntoArray(theGeoOverlays, geoOverlays)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.write(out, ExtWrapList(ArrayUtilities.toVector(geoOverlays)))
    }

    fun getGeoOverlays(): Array<GeoOverlay> = geoOverlays
}
