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
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.xmlpull.v1.XmlPullParserException
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Vector

/**
 * Used for any resources which:
 * 1) Are going to be stored in memory
 * 2) Possibly have derived resources
 *
 * @author ctsims
 */
abstract class CacheInstaller<T : Persistable> : ResourceInstaller<CommCarePlatform> {

    @JvmField
    protected var cacheStorage: IStorageUtilityIndexed<T>? = null

    @JvmField
    protected var cacheLocation: Int = 0

    protected abstract fun getCacheKey(): String

    @Suppress("UNCHECKED_CAST")
    protected open fun storage(platform: CommCarePlatform): IStorageUtilityIndexed<T> {
        if (cacheStorage == null) {
            cacheStorage = platform.getStorageManager()!!.getStorage(getCacheKey()) as IStorageUtilityIndexed<T>
        }
        return cacheStorage!!
    }

    @Throws(UnresolvedResourceException::class, UnfullfilledRequirementsException::class)
    abstract override fun install(
        r: Resource, location: ResourceLocation,
        ref: Reference, table: ResourceTable,
        platform: CommCarePlatform, upgrade: Boolean,
        resourceInstallContext: ResourceInstallContext
    ): Boolean

    @Throws(
        IOException::class,
        InvalidReferenceException::class,
        InvalidStructureException::class,
        XmlPullParserException::class,
        UnfullfilledRequirementsException::class
    )
    override fun initialize(platform: CommCarePlatform, isUpgrade: Boolean): Boolean {
        return false
    }

    override fun requiresRuntimeInitialization(): Boolean {
        return false
    }

    @Throws(UnresolvedResourceException::class)
    override fun upgrade(r: Resource, platform: CommCarePlatform): Boolean {
        //Don't need to do anything, since the resource is in the RMS already.
        throw UnresolvedResourceException(r, "Attempt to upgrade installed resource suite")
    }

    override fun uninstall(r: Resource, platform: CommCarePlatform): Boolean {
        try {
            storage(platform).remove(cacheLocation)
        } catch (e: IllegalArgumentException) {
            //Already gone! Shouldn't need to fail.
        }
        return true
    }

    override fun unstage(r: Resource, newStatus: Int, platform: CommCarePlatform): Boolean {
        //By default, shouldn't need to move anything.
        return true
    }

    override fun revert(r: Resource, table: ResourceTable, platform: CommCarePlatform): Boolean {
        //By default, shouldn't need to move anything.
        return true
    }

    override fun rollback(r: Resource, platform: CommCarePlatform): Int {
        //This does nothing, since we don't do any upgrades/unstages
        return Resource.getCleanFlag(r.getStatus())
    }

    override fun cleanup() {
        if (cacheStorage != null) {
            cacheStorage!!.close()
        }
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        cacheLocation = ExtUtil.readInt(`in`)
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeNumeric(out, cacheLocation.toLong())
    }

    override fun verifyInstallation(
        r: Resource, problemList: Vector<MissingMediaException>,
        platform: CommCarePlatform
    ): Boolean {
        return false
    }
}
