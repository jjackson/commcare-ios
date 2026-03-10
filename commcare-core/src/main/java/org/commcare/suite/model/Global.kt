package org.commcare.suite.model

import org.javarosa.core.util.ArrayUtilities
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Vector

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

    constructor(geoOverlays: Vector<GeoOverlay>) {
        @Suppress("UNCHECKED_CAST")
        this.geoOverlays = ArrayUtilities.copyIntoArray(geoOverlays, arrayOfNulls<GeoOverlay>(geoOverlays.size) as Array<GeoOverlay>)
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        val theGeoOverlays = ExtUtil.read(`in`, ExtWrapList(GeoOverlay::class.java), pf) as Vector<GeoOverlay>
        geoOverlays = arrayOfNulls<GeoOverlay>(theGeoOverlays.size) as Array<GeoOverlay>
        ArrayUtilities.copyIntoArray(theGeoOverlays, geoOverlays)
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.write(out, ExtWrapList(ArrayUtilities.toVector(geoOverlays)))
    }

    fun getGeoOverlays(): Array<GeoOverlay> = geoOverlays
}
