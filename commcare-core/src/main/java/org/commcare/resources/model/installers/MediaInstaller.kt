package org.commcare.resources.model.installers

import org.commcare.resources.ResourceInstallContext
import org.commcare.resources.model.Resource
import org.commcare.resources.model.ResourceLocation
import org.commcare.resources.model.ResourceTable
import org.commcare.resources.model.UnresolvedResourceException
import org.commcare.util.CommCarePlatform
import org.javarosa.core.reference.Reference

/**
 * TODO: This should possibly just be replaced by a basic file installer along
 * with a reference for the login screen. We'll see.
 *
 * @author ctsims
 */
class MediaInstaller : BasicInstaller() {

    @Throws(UnresolvedResourceException::class)
    override fun install(
        r: Resource, location: ResourceLocation,
        ref: Reference, table: ResourceTable,
        platform: CommCarePlatform, upgrade: Boolean,
        resourceInstallContext: ResourceInstallContext
    ): Boolean {
        val result = super.install(r, location, ref, table, platform, upgrade, resourceInstallContext)
        if (result) {
            table.commit(r, Resource.RESOURCE_STATUS_INSTALLED)
            return true
        }
        return false
    }

    override fun requiresRuntimeInitialization(): Boolean {
        return true
    }
}
