package org.commcare.resources.model

import org.javarosa.core.services.storage.IMetaData
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.PropertyUtils
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.core.util.externalizable.emptyIfNull
import org.javarosa.core.util.externalizable.nullIfEmpty
import org.commcare.util.CommCarePlatform
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Resources are records which resolve the location of data
 * definitions (Suites, Xforms, Images, etc), and keep track
 * of their status in the local environment. A Resource model
 * knows where certain resources definitions can be found, what
 * abstract resource those definitions are, a unique status about
 * whether that resource is installed or locally available,
 * and what installer it uses.
 *
 * Resources are immutable and should be treated as such. The
 * abstract definition of a resource model is actually inside
 * of the Resource Table, and changes should be committed to
 * the table in order to change the resource.
 *
 * As resources are installed into the local environment, their
 * status is updated to reflect that progress. The possible status
 * enumerations are:
 *
 * - RESOURCE_STATUS_UNINITIALIZED - The resource has not yet been
 *   evaluated by the the resource table.
 * - RESOURCE_STATUS_LOCAL - The resource definition is locally present
 *   and ready to be read and installed
 * - RESOURCE_STATUS_INSTALLED - This resource is present locally and has
 *   been installed. It is ready to be used.
 * - RESOURCE_STATUS_UPGRADE - This resource definition has been read, and
 *   the resource is present locally and ready to install, but a previous
 *   version of it must be uninstalled first so its place can be taken.
 * - RESOURCE_STATUS_DELETE - This resource is no longer needed and should
 *   be uninstalled and its record removed.
 *
 * @author ctsims
 */
open class Resource : Persistable, IMetaData {

    companion object {
        const val META_INDEX_RESOURCE_ID = "ID"
        const val META_INDEX_RESOURCE_GUID = "RGUID"
        const val META_INDEX_PARENT_GUID = "PGUID"
        const val META_INDEX_VERSION = "VERSION"
        const val META_INDEX_LAZY = "LAZY"

        const val LAZY_VAL_TRUE = "true"
        const val LAZY_VAL_FALSE = "false"

        const val RESOURCE_AUTHORITY_LOCAL = 0
        const val RESOURCE_AUTHORITY_REMOTE = 1
        const val RESOURCE_AUTHORITY_CACHE = 2
        const val RESOURCE_AUTHORITY_RELATIVE = 4
        const val RESOURCE_AUTHORITY_TEMPORARY = 8

        // Completely Unprocessed
        const val RESOURCE_STATUS_UNINITIALIZED = 0

        // Resource is in the local environment and ready to install
        const val RESOURCE_STATUS_LOCAL = 1

        // Installed and ready to use
        const val RESOURCE_STATUS_INSTALLED = 4

        // Resource is ready to replace an existing installed resource.
        const val RESOURCE_STATUS_UPGRADE = 8

        // Resource is no longer needed in the local environment and can be
        // completely removed
        const val RESOURCE_STATUS_DELETE = 16

        // Resource has been "unstaged" (won't necessarily work as an app
        // resource), but can be reverted to installed atomically.
        const val RESOURCE_STATUS_UNSTAGED = 17

        // Resource is transitioning from installed to unstaged, and can be in any
        // interstitial state.
        const val RESOURCE_STATUS_INSTALL_TO_UNSTAGE = 18

        // Resource is transitioning from unstaged to being installed
        const val RESOURCE_STATUS_UNSTAGE_TO_INSTALL = 19

        // Resource is transitioning from being upgraded to being installed
        const val RESOURCE_STATUS_UPGRADE_TO_INSTALL = 20

        // Resource is transitioning from being installed to being upgraded
        const val RESOURCE_STATUS_INSTALL_TO_UPGRADE = 21

        const val RESOURCE_VERSION_UNKNOWN = -2

        @JvmStatic
        fun getCleanFlag(dirtyFlag: Int): Int {
            // We actually will just push it forward by default, since this method
            // is used by things that can only be in the right state
            if (dirtyFlag == RESOURCE_STATUS_INSTALL_TO_UNSTAGE) {
                return RESOURCE_STATUS_UNSTAGED
            } else if (dirtyFlag == RESOURCE_STATUS_INSTALL_TO_UPGRADE) {
                return RESOURCE_STATUS_UPGRADE
            } else if (dirtyFlag == RESOURCE_STATUS_UNSTAGE_TO_INSTALL) {
                return RESOURCE_STATUS_INSTALLED
            } else if (dirtyFlag == RESOURCE_STATUS_UPGRADE_TO_INSTALL) {
                return RESOURCE_STATUS_INSTALLED
            }
            return -1
        }
    }

    internal var recordId: Int = -1
    internal var version: Int = 0
    internal var status: Int = 0
    @JvmField
    internal var id: String = ""
    internal var locations: ArrayList<ResourceLocation> = ArrayList()
    @Suppress("UNCHECKED_CAST")
    internal var initializer: ResourceInstaller<CommCarePlatform>? = null
    internal var guid: String = ""

    // Not sure if we want this persisted just yet...
    internal var parent: String? = null

    internal var descriptor: String? = null
    private var lazy: String = ""

    /**
     * For serialization only
     */
    constructor()

    /**
     * Creates a resource record identifying where a specific version of a resource
     * can be located.
     *
     * @param version   The version of the resource being defined.
     * @param id        A unique string identifying the abstract resource
     * @param locations A set of locations from which this resource's definition
     *                  can be retrieved. Note that this vector is copied and should not be changed
     *                  after being passed in here.
     */
    constructor(version: Int, id: String, locations: ArrayList<ResourceLocation>, descriptor: String?, lazy: String) {
        this.version = version
        this.id = id
        this.locations = locations
        this.guid = PropertyUtils.genGUID(25)
        this.status = RESOURCE_STATUS_UNINITIALIZED
        this.descriptor = descriptor
        this.lazy = lazy
    }

    constructor(version: Int, id: String, locations: ArrayList<ResourceLocation>, descriptor: String?)
            : this(version, id, locations, descriptor, LAZY_VAL_FALSE)

    /**
     * @return The locations where this resource's definition can be obtained.
     */
    fun getLocations(): ArrayList<ResourceLocation> {
        return locations
    }

    /**
     * @return An enumerated ID identifying the status of this resource on
     * the local device.
     */
    fun getStatus(): Int {
        return status
    }

    /**
     * @return The unique identifier for what resource this record offers the definition of.
     */
    fun getResourceId(): String {
        return id
    }

    /**
     * @return A GUID that the resource table uses to identify this definition.
     */
    fun getRecordGuid(): String {
        return guid
    }

    fun setRecordGuid(guid: String) {
        this.guid = guid
    }

    /**
     * @param parent The GUID of the resource record which has made this resource relevant
     *               for installation. This method should only be called by a resource table committing
     *               this resource record definition.
     */
    internal fun setParentId(parent: String?) {
        this.parent = parent
    }

    /**
     * @return True if this resource's relevance is derived from another resource. False
     * otherwise.
     */
    fun hasParent(): Boolean {
        return !(parent == null || "" == parent)
    }

    /**
     * @return The GUID of the resource record which has made this resource relevant
     * for installation. This method should only be called by a resource table committing
     * this resource record definition.
     */
    fun getParentId(): String? {
        return parent
    }

    /**
     * @return The version of the resource that this record defines.
     */
    fun getVersion(): Int {
        return version
    }

    /**
     * Sets the version of the resource if the current version is RESOURCE_VERSION_UNKNOWN.
     */
    internal fun setVersionIfUnknown(version: Int) {
        if (this.version == RESOURCE_VERSION_UNKNOWN) {
            this.version = version
        }
    }

    /**
     * @param initializer Associates a ResourceInstaller with this resource record. This method
     *                    should only be called by a resource table committing this resource record definition.
     */
    @Suppress("UNCHECKED_CAST")
    fun setInstaller(initializer: ResourceInstaller<*>) {
        this.initializer = initializer as ResourceInstaller<CommCarePlatform>
    }

    /**
     * @return The installer which should be used to install the resource for this record.
     */
    fun getInstaller(): ResourceInstaller<CommCarePlatform> {
        return initializer!!
    }

    /**
     * @param status The current status of this resource. Should only be called by the resource
     *               table.
     */
    fun setStatus(status: Int) {
        this.status = status
    }

    override fun getID(): Int {
        return recordId
    }

    override fun setID(ID: Int) {
        recordId = ID
    }

    fun isLazy(): Boolean {
        return lazy.contentEquals(LAZY_VAL_TRUE)
    }

    /**
     * @param peer A resource record which defines the same resource as this record.
     * @return True if this record defines a newer version of the same resource as
     * peer, or if this resource generally is suspected to obsolete peer (if, for
     * instance this resource's version is yet unknown it will be assumed that it
     * is newer until it is.)
     */
    fun isNewer(peer: Resource): Boolean {
        return version == RESOURCE_VERSION_UNKNOWN ||
                (peer.id == this.id && version > peer.getVersion())
    }

    /**
     * Take on all identifiers from the incoming
     * resouce, so as to replace it in a different table.
     */
    fun mimick(source: Resource) {
        this.guid = source.guid
        this.id = source.id
        this.recordId = source.recordId
        this.descriptor = source.descriptor
        this.parent = source.parent
    }

    fun getDescriptor(): String {
        return descriptor ?: id
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        this.recordId = SerializationHelpers.readInt(`in`)
        this.version = SerializationHelpers.readInt(`in`)
        this.id = SerializationHelpers.readString(`in`)
        this.guid = SerializationHelpers.readString(`in`)
        this.status = SerializationHelpers.readInt(`in`)
        this.parent = nullIfEmpty(SerializationHelpers.readString(`in`))

        locations = SerializationHelpers.readList(`in`, pf) { ResourceLocation() }
        @Suppress("UNCHECKED_CAST")
        this.initializer = SerializationHelpers.readTagged(`in`, pf) as ResourceInstaller<CommCarePlatform>
        this.descriptor = nullIfEmpty(SerializationHelpers.readString(`in`))
        this.lazy = SerializationHelpers.readString(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeNumeric(out, recordId.toLong())
        SerializationHelpers.writeNumeric(out, version.toLong())
        SerializationHelpers.writeString(out, id)
        SerializationHelpers.writeString(out, guid)
        SerializationHelpers.writeNumeric(out, status.toLong())
        SerializationHelpers.writeString(out, emptyIfNull(parent))

        SerializationHelpers.writeList(out, locations)
        SerializationHelpers.writeTagged(out, initializer!!)
        SerializationHelpers.writeString(out, emptyIfNull(descriptor))
        SerializationHelpers.writeString(out, lazy)
    }

    override fun getMetaData(fieldName: String): Any {
        return when (fieldName) {
            META_INDEX_RESOURCE_ID -> id
            META_INDEX_RESOURCE_GUID -> guid
            META_INDEX_PARENT_GUID -> if (parent == null) "" else parent!!
            META_INDEX_VERSION -> version
            META_INDEX_LAZY -> lazy
            else -> throw IllegalArgumentException("No Field w/name $fieldName is relevant for resources")
        }
    }

    override fun getMetaDataFields(): Array<String> {
        return arrayOf(META_INDEX_RESOURCE_ID, META_INDEX_RESOURCE_GUID, META_INDEX_PARENT_GUID, META_INDEX_VERSION, META_INDEX_LAZY)
    }

    fun isDirty(): Boolean {
        return (getStatus() == RESOURCE_STATUS_INSTALL_TO_UNSTAGE ||
                getStatus() == RESOURCE_STATUS_INSTALL_TO_UPGRADE ||
                getStatus() == RESOURCE_STATUS_UNSTAGE_TO_INSTALL ||
                getStatus() == RESOURCE_STATUS_UPGRADE_TO_INSTALL)
    }
}
