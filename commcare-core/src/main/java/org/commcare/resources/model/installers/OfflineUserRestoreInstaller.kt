package org.commcare.resources.model.installers

import org.commcare.resources.ResourceInstallContext
import org.commcare.resources.model.Resource
import org.commcare.resources.model.ResourceLocation
import org.commcare.resources.model.ResourceTable
import org.commcare.resources.model.UnresolvedResourceException
import org.commcare.suite.model.OfflineUserRestore
import org.commcare.util.CommCarePlatform
import org.javarosa.core.reference.InvalidReferenceException
import org.javarosa.core.reference.Reference
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.xmlpull.v1.XmlPullParserException
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Install user restore xml file present in app for use in offline logins.
 * Used for providing a demo user restore.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
class OfflineUserRestoreInstaller : CacheInstaller<OfflineUserRestore>() {

    override fun getCacheKey(): String {
        return OfflineUserRestore.STORAGE_KEY
    }

    @Throws(
        PlatformIOException::class,
        InvalidReferenceException::class,
        InvalidStructureException::class,
        XmlPullParserException::class,
        UnfullfilledRequirementsException::class
    )
    override fun initialize(platform: CommCarePlatform, isUpgrade: Boolean): Boolean {
        platform.registerDemoUserRestore(storage(platform).read(cacheLocation))
        return true
    }

    override fun requiresRuntimeInitialization(): Boolean {
        return true
    }

    @Throws(UnresolvedResourceException::class, UnfullfilledRequirementsException::class)
    override fun install(
        r: Resource, location: ResourceLocation,
        ref: Reference, table: ResourceTable,
        platform: CommCarePlatform, upgrade: Boolean,
        resourceInstallContext: ResourceInstallContext
    ): Boolean {
        try {
            val offlineUserRestore = OfflineUserRestore.buildInMemoryUserRestore(ref.getStream())
            storage(platform).write(offlineUserRestore)
            if (upgrade) {
                table.commit(r, Resource.RESOURCE_STATUS_INSTALLED)
            } else {
                table.commit(r, Resource.RESOURCE_STATUS_UPGRADE)
            }
            cacheLocation = offlineUserRestore.getID()
        } catch (e: PlatformIOException) {
            throw UnresolvedResourceException(r, e.message)
        } catch (e: XmlPullParserException) {
            throw UnresolvedResourceException(r, e.message)
        } catch (e: InvalidStructureException) {
            throw UnresolvedResourceException(r, e.message)
        } catch (e: InvalidReferenceException) {
            throw UnresolvedResourceException(r, e.message)
        }
        return true
    }
}
