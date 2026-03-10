package org.commcare.suite.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Defines a polygon region to be displayed on a map
 */
class GeoOverlay : Externalizable {
    private var coordinates: DisplayUnit? = null
    private var label: DisplayUnit? = null

    /**
     * Serialization Only
     */
    constructor()

    constructor(label: DisplayUnit?, coordinates: DisplayUnit?) {
        this.label = label
        this.coordinates = coordinates
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        coordinates = ExtUtil.read(`in`, ExtWrapNullable(DisplayUnit::class.java), pf) as DisplayUnit?
        label = ExtUtil.read(`in`, ExtWrapNullable(DisplayUnit::class.java), pf) as DisplayUnit?
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.write(out, ExtWrapNullable(coordinates))
        ExtUtil.write(out, ExtWrapNullable(label))
    }

    fun getCoordinates(): DisplayUnit? = coordinates

    fun getLabel(): DisplayUnit? = label
}
