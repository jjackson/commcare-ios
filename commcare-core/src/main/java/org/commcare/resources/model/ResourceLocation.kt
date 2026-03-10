package org.commcare.resources.model

import org.javarosa.core.reference.ReferenceManager
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream

/**
 * A resource location is a simple model containing a possible
 * location for a resource's definition.
 *
 * Resource locations provide a URI (possibly a relative URI)
 * along with an authority for location.
 *
 * @author ctsims
 */
class ResourceLocation : Externalizable {
    private var authority: Int = 0
    private var location: String = ""
    private var relative: Boolean = false

    /**
     * For serialization only
     */
    constructor()

    /**
     * @param authority The enumerated value defining the authority
     *                  associated with this location.
     * @param location  A URI (possibly relative) defining the location
     *                  of a resource's definition.
     */
    constructor(authority: Int, location: String) {
        this.authority = authority
        this.location = location
        this.relative = ReferenceManager.isRelative(location)
    }

    /**
     * @return The enumerated value defining the authority associated
     * with this location.
     */
    fun getAuthority(): Int {
        return authority
    }

    /**
     * @return A URI (possibly relative) defining the location
     * of a resource's definition.
     */
    fun getLocation(): String {
        return location
    }

    /**
     * @return Whether or not this location is a relative.
     */
    fun isRelative(): Boolean {
        return relative
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        this.authority = ExtUtil.readInt(`in`)
        this.location = ExtUtil.readString(`in`)
        this.relative = ReferenceManager.isRelative(location)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeNumeric(out, authority.toLong())
        ExtUtil.writeString(out, location)
        this.relative = ReferenceManager.isRelative(location)
    }
}
