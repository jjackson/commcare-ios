package org.commcare.resources.model.installers

import org.commcare.resources.ResourceInstallContext
import org.commcare.resources.model.MissingMediaException
import org.commcare.resources.model.Resource
import org.commcare.resources.model.ResourceInstaller
import org.commcare.resources.model.ResourceLocation
import org.commcare.resources.model.ResourceTable
import org.commcare.resources.model.UnresolvedResourceException
import org.commcare.util.CommCarePlatform
import org.javarosa.core.reference.InvalidReferenceException
import org.javarosa.core.reference.Reference
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xml.PlatformXmlParserException
import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.Vector

/**
 * TODO: This is... not useful
 *
 * @author ctsims
 */
open class BasicInstaller : ResourceInstaller<CommCarePlatform> {

    @Throws(
        PlatformIOException::class,
        InvalidReferenceException::class,
        InvalidStructureException::class,
        PlatformXmlParserException::class,
        UnfullfilledRequirementsException::class
    )
    override fun initialize(platform: CommCarePlatform, isUpgrade: Boolean): Boolean {
        return true
    }

    override fun requiresRuntimeInitialization(): Boolean {
        return false
    }

    @Throws(UnresolvedResourceException::class)
    override fun install(
        r: Resource, location: ResourceLocation,
        ref: Reference, table: ResourceTable,
        platform: CommCarePlatform, upgrade: Boolean,
        resourceInstallContext: ResourceInstallContext
    ): Boolean {
        //If we have local resource authority, and the file exists, things are golden. We can just use that file.
        if (location.getAuthority() == Resource.RESOURCE_AUTHORITY_LOCAL) {
            try {
                //If the file isn't there, not much we can do about it.
                return ref.doesBinaryExist()
            } catch (e: PlatformIOException) {
                e.printStackTrace()
                return false
            }
        } else if (location.getAuthority() == Resource.RESOURCE_AUTHORITY_REMOTE) {
            //We need to download the resource, and store it locally. Either in the cache
            //(if no resource location is available) or in a local reference if one exists.
            val incoming = try {
                ref.getStream()
            } catch (e: PlatformIOException) {
                e.printStackTrace()
                return false
            }
            if (incoming == null) {
                //if it turns out there isn't actually a remote resource, bail.
                return false
            }
            //TODO: Implement local cache code
            return false
        }
        return false
    }

    @Throws(UnresolvedResourceException::class)
    override fun upgrade(r: Resource, platform: CommCarePlatform): Boolean {
        throw RuntimeException("Basic Installer resources can't be marked as upgradable")
    }

    @Throws(UnresolvedResourceException::class)
    override fun uninstall(r: Resource, platform: CommCarePlatform): Boolean {
        return true
    }

    override fun unstage(r: Resource, newStatus: Int, platform: CommCarePlatform): Boolean {
        return true
    }

    override fun revert(r: Resource, table: ResourceTable, platform: CommCarePlatform): Boolean {
        return true
    }

    override fun rollback(r: Resource, platform: CommCarePlatform): Int {
        throw RuntimeException("Basic Installer resources can't rolled back")
    }

    override fun cleanup() {
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
    }

    override fun verifyInstallation(
        r: Resource, problemList: Vector<MissingMediaException>,
        platform: CommCarePlatform
    ): Boolean {
        //Work by default
        return true
    }
}
