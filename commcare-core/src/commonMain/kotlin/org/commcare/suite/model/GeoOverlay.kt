package org.commcare.suite.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Defines a polygon region to be displayed on a map
 */
class GeoOverlay : Externalizable {
    var coordinates: DisplayUnit? = null
        private set
    var label: DisplayUnit? = null
        private set

    /**
     * Serialization Only
     */
    constructor()

    constructor(label: DisplayUnit?, coordinates: DisplayUnit?) {
        this.label = label
        this.coordinates = coordinates
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        coordinates = SerializationHelpers.readNullableExternalizable(`in`, pf) { DisplayUnit() }
        label = SerializationHelpers.readNullableExternalizable(`in`, pf) { DisplayUnit() }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeNullable(out, coordinates)
        SerializationHelpers.writeNullable(out, label)
    }
}
