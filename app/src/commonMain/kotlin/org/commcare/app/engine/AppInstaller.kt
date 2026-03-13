package org.commcare.app.engine

import org.commcare.app.storage.InMemoryStorage
import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.resources.ResourceInstallContext
import org.commcare.resources.ResourceManager
import org.commcare.resources.model.InstallerFactory
import org.commcare.resources.model.InstallRequestSource
import org.commcare.resources.model.Resource
import org.commcare.resources.model.ResourceTable
import org.commcare.util.CommCarePlatform

/**
 * Handles CommCare app installation from a profile URL.
 * Downloads profile, suites, forms, and initializes the CommCarePlatform.
 */
class AppInstaller(
    private val sandbox: SqlDelightUserSandbox
) {

    /**
     * Install a CommCare app from its profile URL.
     * @param profileUrl URL to the app's profile.ccpr (e.g., from HQ app settings)
     * @param onProgress Progress callback (0.0 to 1.0 with status message)
     * @return Initialized CommCarePlatform ready for use
     */
    fun install(
        profileUrl: String,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): CommCarePlatform {
        onProgress(0.1f, "Creating platform...")
        val platform = CommCarePlatform(2, 53, 0)

        onProgress(0.2f, "Setting up resource tables...")
        val globalStorage = InMemoryStorage<Resource>(Resource::class, { Resource() })
        val upgradeStorage = InMemoryStorage<Resource>(Resource::class, { Resource() })
        val tempStorage = InMemoryStorage<Resource>(Resource::class, { Resource() })

        val installerFactory = InstallerFactory()

        val globalTable = ResourceTable.RetrieveTable(globalStorage, installerFactory)
        val upgradeTable = ResourceTable.RetrieveTable(upgradeStorage, installerFactory)
        val tempTable = ResourceTable.RetrieveTable(tempStorage, installerFactory)

        onProgress(0.3f, "Downloading app profile...")

        ResourceManager.installAppResources(
            platform,
            profileUrl,
            globalTable,
            true,
            Resource.RESOURCE_AUTHORITY_REMOTE,
            ResourceInstallContext(InstallRequestSource.INSTALL)
        )

        onProgress(0.8f, "Initializing platform...")
        platform.initialize(globalTable, false)

        onProgress(1.0f, "Installation complete")
        return platform
    }

    /**
     * Create a minimal CommCarePlatform without network access.
     * Useful for development/testing when no profile URL is available.
     */
    fun createMinimalPlatform(): CommCarePlatform {
        return CommCarePlatform(2, 53, 0)
    }
}
