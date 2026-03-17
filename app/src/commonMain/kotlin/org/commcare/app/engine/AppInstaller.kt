package org.commcare.app.engine

import org.commcare.app.storage.InMemoryStorage
import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.resources.ResourceInstallContext
import org.commcare.resources.ResourceManager
import org.commcare.resources.model.InstallerFactory
import org.commcare.resources.model.InstallRequestSource
import org.commcare.resources.model.Resource
import org.commcare.resources.model.ResourceTable
import org.commcare.suite.model.OfflineUserRestore
import org.commcare.suite.model.Profile
import org.commcare.suite.model.Suite
import org.commcare.util.CommCarePlatform
import org.javarosa.core.model.FormDef
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.services.storage.IStorageIndexedFactory
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.services.storage.StorageManager
import org.javarosa.core.services.properties.Property
import kotlin.reflect.KClass

/**
 * Handles CommCare app installation from a profile URL.
 * Downloads profile, suites, forms, and initializes the CommCarePlatform.
 */
class AppInstaller(
    private val sandbox: SqlDelightUserSandbox,
    private val installerFactory: InstallerFactory = InstallerFactory()
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
        val storageFactory = InMemoryStorageFactory()
        val storageManager = StorageManager(storageFactory)
        storageManager.registerStorage(FormDef.STORAGE_KEY, FormDef::class)
        storageManager.registerStorage(Profile.STORAGE_KEY, Profile::class)
        storageManager.registerStorage(Suite.STORAGE_KEY, Suite::class)
        storageManager.registerStorage(FormInstance.STORAGE_KEY, FormInstance::class)
        storageManager.registerStorage(OfflineUserRestore.STORAGE_KEY, OfflineUserRestore::class)
        storageManager.registerStorage("fixture", FormInstance::class)

        val platform = CommCarePlatform(2, 53, 0, storageManager)

        onProgress(0.2f, "Setting up resource tables...")
        val globalStorage = InMemoryStorage<Resource>(Resource::class, { Resource() })
        val upgradeStorage = InMemoryStorage<Resource>(Resource::class, { Resource() })
        val tempStorage = InMemoryStorage<Resource>(Resource::class, { Resource() })

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
        val storageFactory = InMemoryStorageFactory()
        val storageManager = StorageManager(storageFactory)
        storageManager.registerStorage(FormDef.STORAGE_KEY, FormDef::class)
        storageManager.registerStorage(Profile.STORAGE_KEY, Profile::class)
        storageManager.registerStorage(Suite.STORAGE_KEY, Suite::class)
        storageManager.registerStorage(FormInstance.STORAGE_KEY, FormInstance::class)
        return CommCarePlatform(2, 53, 0, storageManager)
    }
}

/**
 * Storage factory that creates InMemoryStorage instances for any registered type.
 * Uses hardcoded constructors since Kotlin/Native doesn't support reflection-based instantiation.
 */
private class InMemoryStorageFactory : IStorageIndexedFactory {
    @Suppress("UNCHECKED_CAST")
    override fun newStorage(name: String, type: KClass<*>): IStorageUtilityIndexed<*> {
        val factory = createFactory(type)
        return InMemoryStorage(type, factory)
    }

    private fun createFactory(type: KClass<*>): () -> Persistable {
        return when (type) {
            FormDef::class -> { { FormDef() } }
            FormInstance::class -> { { FormInstance() } }
            Profile::class -> { { Profile() } }
            Suite::class -> { { Suite() } }
            Resource::class -> { { Resource() } }
            OfflineUserRestore::class -> { { OfflineUserRestore() } }
            Property::class -> { { Property() } }
            else -> throw IllegalArgumentException("Unknown storage type: ${type.simpleName}")
        }
    }
}
