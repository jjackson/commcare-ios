package org.commcare.resources.model.installers

import org.commcare.resources.ResourceInstallContext
import org.commcare.resources.model.Resource
import org.commcare.resources.model.ResourceLocation
import org.commcare.resources.model.ResourceTable
import org.commcare.resources.model.UnreliableSourceException
import org.commcare.resources.model.UnresolvedResourceException
import org.commcare.suite.model.Profile
import org.commcare.util.CommCarePlatform
import org.commcare.util.LogTypes
import org.commcare.xml.ProfileParser
import org.javarosa.core.reference.InvalidReferenceException
import org.javarosa.core.reference.Reference
import org.javarosa.core.services.Logger
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.SerializationHelpers

/**
 * @author ctsims
 */
class ProfileInstaller : CacheInstaller<Profile> {

    private var forceVersion: Boolean

    @Suppress("unused")
    constructor() {
        forceVersion = false
    }

    constructor(forceVersion: Boolean) {
        this.forceVersion = forceVersion
    }

    companion object {
        private var localTable: HashMap<String, Profile>? = null
    }

    private fun getlocal(): HashMap<String, Profile> {
        if (localTable == null) {
            localTable = HashMap()
        }
        return localTable!!
    }

    @Throws(
        PlatformIOException::class,
        InvalidReferenceException::class,
        InvalidStructureException::class,
        PlatformXmlParserException::class,
        UnfullfilledRequirementsException::class
    )
    override fun initialize(platform: CommCarePlatform, isUpgrade: Boolean): Boolean {
        //Certain properties may not have been able to set during install, so we'll make sure they're
        //set here.
        val p = storage(platform).read(cacheLocation)
        p.initializeProperties(platform, false)

        platform.setProfile(p)
        return true
    }

    override fun requiresRuntimeInitialization(): Boolean {
        return true
    }

    override fun getCacheKey(): String {
        return Profile.STORAGE_KEY
    }

    @Throws(UnresolvedResourceException::class, UnfullfilledRequirementsException::class)
    override fun install(
        r: Resource, location: ResourceLocation,
        ref: Reference, table: ResourceTable,
        platform: CommCarePlatform, upgrade: Boolean,
        resourceInstallContext: ResourceInstallContext
    ): Boolean {
        //Install for the profile installer is a two step process. Step one is to parse the file and read the relevant data.
        //Step two is to actually install the resource if it needs to be (whether or not it should will be handled
        //by the resource table).

        var incoming: java.io.InputStream? = null
        //If we've already got the local copy, and the installer is marked as such, install and roll out.
        try {
            if (getlocal().containsKey(r.getRecordGuid()) && r.getStatus() == Resource.RESOURCE_STATUS_LOCAL) {
                val local = getlocal()[r.getRecordGuid()]
                installInternal(local!!, platform)
                table.commitCompoundResource(r, Resource.RESOURCE_STATUS_UPGRADE)
                localTable!!.remove(r.getRecordGuid())

                for (child in table.getResourcesForParent(r.getRecordGuid())) {
                    table.commitCompoundResource(child, Resource.RESOURCE_STATUS_UNINITIALIZED)
                }
                return true
            }

            //Otherwise we need to get the profile from its location, parse it out, and
            //set the relevant parameters.
            if (location.getAuthority() == Resource.RESOURCE_AUTHORITY_CACHE) {
                //If it's in the cache, we should just get it from there
                return false
            } else {
                val p: Profile
                try {
                    incoming = ref.getStream()
                    val parser = ProfileParser(
                        incoming, platform, table, r.getRecordGuid(),
                        Resource.RESOURCE_STATUS_UNINITIALIZED, forceVersion
                    )
                    if (Resource.RESOURCE_AUTHORITY_REMOTE == location.getAuthority()) {
                        parser.setMaximumAuthority(Resource.RESOURCE_AUTHORITY_REMOTE)
                    }
                    p = parser.parse()
                } catch (e: PlatformIOException) {
                    if (e.message != null) {
                        Logger.log(LogTypes.TYPE_RESOURCES, "IO Exception fetching profile: " + e.message)
                    }
                    val exception = UnreliableSourceException(r, e.message)
                    throw exception
                }

                //If we're upgrading we need to come back and see if the statuses need to change
                if (upgrade) {
                    getlocal()[r.getRecordGuid()] = p
                    table.commitCompoundResource(r, Resource.RESOURCE_STATUS_LOCAL, p.getVersion())
                } else {
                    p.initializeProperties(platform, true)
                    installInternal(p, platform)
                    //TODO: What if this fails? Maybe we should be throwing exceptions...
                    table.commitCompoundResource(r, Resource.RESOURCE_STATUS_INSTALLED, p.getVersion())
                }

                return true
            }
        } catch (e: InvalidStructureException) {
            if (e.message != null) {
                Logger.log(LogTypes.TYPE_RESOURCES, "Invalid profile structure: " + e.message)
            }
            e.printStackTrace()
            return false
        } catch (e: PlatformXmlParserException) {
            if (e.message != null) {
                Logger.log(LogTypes.TYPE_RESOURCES, "XML Parse exception fetching profile: " + e.message)
            }
            return false
        } finally {
            try {
                incoming?.close()
            } catch (e: PlatformIOException) {
            }
        }
    }

    private fun installInternal(profile: Profile, platform: CommCarePlatform) {
        storage(platform).write(profile)
        cacheLocation = profile.getID()
    }

    @Throws(UnresolvedResourceException::class)
    override fun upgrade(r: Resource, platform: CommCarePlatform): Boolean {
        //TODO: Hm... how to do this property setting for reverting?

        val p: Profile = if (getlocal().containsKey(r.getRecordGuid())) {
            getlocal()[r.getRecordGuid()]!!
        } else {
            storage(platform).read(cacheLocation)
        }
        p.initializeProperties(platform, true)
        storage(platform).write(p)
        return true
    }

    override fun unstage(r: Resource, newStatus: Int, platform: CommCarePlatform): Boolean {
        //Nothing to do. Cache location is clear.
        return true
    }

    override fun revert(r: Resource, table: ResourceTable, platform: CommCarePlatform): Boolean {
        //Possibly re-set this profile's default property setters.
        return true
    }

    override fun cleanup() {
        super.cleanup()
        if (localTable != null) {
            localTable!!.clear()
            localTable = null
        }
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        super.readExternal(`in`, pf)
        forceVersion = SerializationHelpers.readBool(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        super.writeExternal(out)
        SerializationHelpers.writeBool(out, forceVersion)
    }
}
