package org.commcare.resources

import org.commcare.resources.model.InstallCancelled
import org.commcare.resources.model.InstallCancelledException
import org.commcare.resources.model.Resource
import org.commcare.resources.model.ResourceInitializationException
import org.commcare.resources.model.ResourceLocation
import org.commcare.resources.model.ResourceTable
import org.commcare.resources.model.TableStateListener
import org.commcare.resources.model.UnresolvedResourceException
import org.commcare.util.CommCarePlatform
import org.commcare.util.LogTypes
import org.javarosa.core.services.Logger
import org.javarosa.xml.util.UnfullfilledRequirementsException
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Resource table install and update logic.
 */
open class ResourceManager(
    @JvmField
    protected val platform: CommCarePlatform,
    private val masterTable: ResourceTable,
    @JvmField
    protected val upgradeTable: ResourceTable,
    @JvmField
    protected val tempTable: ResourceTable
) {
    companion object {
        const val ApplicationDescriptor: String = "Application Descriptor"

        /**
         * Installs resources described by profile reference into the provided
         * resource table. If the resource table is ready or already has a profile,
         * don't do anything.
         *
         * @param profileReference URL to profile file
         * @param global           Add profile ref to this table and install its
         *                         resources
         * @param forceInstall     Should installation be performed regardless of
         *                         version numbers?
         */
        @JvmStatic
        @Throws(
            UnfullfilledRequirementsException::class,
            UnresolvedResourceException::class,
            InstallCancelledException::class
        )
        fun installAppResources(
            platform: CommCarePlatform, profileReference: String,
            global: ResourceTable, forceInstall: Boolean,
            authorityForProfile: Int, resourceInstallContext: ResourceInstallContext
        ) {
            synchronized(platform) {
                if (!global.isReady()) {
                    global.prepareResources(null, platform, resourceInstallContext)
                }

                // First, see if the appropriate profile exists
                val profile = global.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID)

                if (profile == null) {
                    // Create a stub for the profile resource that points to the authority and location
                    // from which we will install it
                    val locations = ArrayList<ResourceLocation>()
                    locations.add(ResourceLocation(authorityForProfile, profileReference))
                    val r = Resource(
                        Resource.RESOURCE_VERSION_UNKNOWN,
                        CommCarePlatform.APP_PROFILE_RESOURCE_ID,
                        locations, ApplicationDescriptor
                    )

                    global.addResource(r, global.getInstallers().getProfileInstaller(forceInstall), "")
                    global.prepareResources(null, platform, resourceInstallContext)
                }
            }
        }

        /**
         * @return Is the table non-empty, marked for upgrade, with all ready
         * resources?
         */
        @JvmStatic
        fun isTableStagedForUpgrade(table: ResourceTable): Boolean {
            return (table.getTableReadiness() == ResourceTable.RESOURCE_TABLE_UPGRADE &&
                    table.isReady() &&
                    !table.isEmpty())
        }

        @JvmStatic
        fun getResourceListFromProfile(master: ResourceTable): ArrayList<Resource> {
            val unresolved = ArrayList<Resource>()
            val resolved = ArrayList<Resource>()
            val r = master.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID) ?: return resolved
            unresolved.add(r)
            while (unresolved.size > 0) {
                val current = unresolved.first()
                unresolved.remove(current)
                resolved.add(current)
                val children = master.getResourcesForParent(current.getRecordGuid())
                for (child in children) {
                    unresolved.add(child)
                }
            }
            return resolved
        }
    }

    /**
     * Loads the profile at the provided reference into the upgrade table.
     *
     * @param clearProgress Clear the 'incoming' table of any partial update
     *                      info.
     */
    @Throws(
        UnfullfilledRequirementsException::class,
        UnresolvedResourceException::class,
        InstallCancelledException::class
    )
    fun stageUpgradeTable(
        profileRef: String, clearProgress: Boolean,
        resourceInstallContext: ResourceInstallContext
    ) {
        synchronized(this.platform) {
            ensureMasterTableValid()

            if (clearProgress) {
                clearUpgrade()
            }

            loadProfileIntoTable(
                upgradeTable, profileRef,
                Resource.RESOURCE_AUTHORITY_REMOTE, resourceInstallContext
            )
        }
    }

    protected open fun ensureMasterTableValid() {
        if (masterTable.getTableReadiness() != ResourceTable.RESOURCE_TABLE_INSTALLED) {
            repair()

            if (masterTable.getTableReadiness() != ResourceTable.RESOURCE_TABLE_INSTALLED) {
                throw IllegalArgumentException("Global resource table was not ready for upgrading")
            }
        }
    }

    @Throws(
        UnfullfilledRequirementsException::class,
        UnresolvedResourceException::class,
        InstallCancelledException::class
    )
    protected open fun loadProfileIntoTable(
        table: ResourceTable,
        profileRef: String,
        authority: Int,
        resourceInstallContext: ResourceInstallContext
    ) {
        val locations = ArrayList<ResourceLocation>()
        locations.add(ResourceLocation(authority, profileRef))

        val r = Resource(
            Resource.RESOURCE_VERSION_UNKNOWN,
            CommCarePlatform.APP_PROFILE_RESOURCE_ID, locations,
            ApplicationDescriptor
        )

        table.addResource(
            r,
            table.getInstallers().getProfileInstaller(false),
            null
        )

        prepareProfileResource(table, resourceInstallContext)
    }

    @Throws(
        UnfullfilledRequirementsException::class,
        UnresolvedResourceException::class,
        InstallCancelledException::class
    )
    private fun prepareProfileResource(
        targetTable: ResourceTable,
        resourceInstallContext: ResourceInstallContext
    ) {
        targetTable.prepareResourcesUpTo(
            masterTable, this.platform,
            CommCarePlatform.APP_PROFILE_RESOURCE_ID, resourceInstallContext
        )
    }

    /**
     * Download resources referenced by upgrade table's profile into the
     * upgrade table itself.
     *
     * @throws InstallCancelledException The user/system has cancelled the
     *                                   installation process
     */
    @Throws(
        UnfullfilledRequirementsException::class,
        UnresolvedResourceException::class,
        IllegalArgumentException::class,
        InstallCancelledException::class
    )
    fun prepareUpgradeResources(resourceInstallContext: ResourceInstallContext) {
        synchronized(platform) {
            ensureMasterTableValid()

            // TODO: Table's acceptable states here may be incomplete
            val upgradeTableState = upgradeTable.getTableReadiness()
            if (upgradeTableState == ResourceTable.RESOURCE_TABLE_UNCOMMITED ||
                upgradeTableState == ResourceTable.RESOURCE_TABLE_UNSTAGED ||
                upgradeTableState == ResourceTable.RESOURCE_TABLE_EMPTY
            ) {
                throw IllegalArgumentException("Upgrade table is not in an appropriate state")
            }

            tempTable.destroy()

            upgradeTable.setResourceProgressStale()
            upgradeTable.prepareResources(masterTable, this.platform, resourceInstallContext)
        }
    }

    /**
     * Install staged upgrade table into the global table.
     */
    @Throws(
        UnresolvedResourceException::class,
        IllegalArgumentException::class,
        ResourceInitializationException::class
    )
    fun upgrade() {
        synchronized(platform) {
            var upgradeSuccess = false
            try {
                Logger.log(LogTypes.TYPE_RESOURCES, "Upgrade table fetched, beginning upgrade")

                // Try to stage the upgrade table to replace the incoming table
                masterTable.upgradeTable(upgradeTable, platform)

                if (upgradeTable.getTableReadiness() != ResourceTable.RESOURCE_TABLE_INSTALLED) {
                    throw RuntimeException("not all incoming resources were installed!!")
                } else {
                    Logger.log(LogTypes.TYPE_RESOURCES, "Global table unstaged, upgrade table ready")
                }

                // We now replace the global resource table with the upgrade table

                Logger.log(LogTypes.TYPE_RESOURCES, "Copying global resources to recovery area")
                try {
                    masterTable.copyToTable(tempTable)
                } catch (e: RuntimeException) {
                    // The _only_ time the recovery table should have data is if we
                    // were in the middle of an install. Since global hasn't been
                    // modified if there is a problem here we want to wipe out the
                    // recovery stub
                    tempTable.destroy()
                    throw e
                }

                Logger.log(LogTypes.TYPE_RESOURCES, "Wiping global")
                // clear the global table to make room (but not the data, just the records)
                masterTable.destroy()

                Logger.log(LogTypes.TYPE_RESOURCES, "Moving update resources")
                upgradeTable.copyToTable(masterTable)

                Logger.log(LogTypes.TYPE_RESOURCES, "Upgrade Succesful!")
                upgradeSuccess = true

                Logger.log(LogTypes.TYPE_RESOURCES, "Wiping redundant update table")
                upgradeTable.destroy()

                Logger.log(LogTypes.TYPE_RESOURCES, "Clearing out old resources")
                tempTable.uninstall(masterTable, platform)
            } finally {
                if (!upgradeSuccess) {
                    repair()
                }

                platform.clearAppState()

                //Is it really possible to verify that we've un-registered
                //everything here? Locale files are registered elsewhere, and we
                //can't guarantee we're the only thing in there, so we can't
                //straight up clear it...
                // NOTE PLM: if the upgrade is successful but crashes before
                // reaching this point, any suite fixture updates won't be
                // applied
                platform.initialize(masterTable, true)
            }
        }
    }

    /**
     * This method is responsible for recovering the state of the application
     * to installed after anything happens during an upgrade. After it is
     * finished, the global resource table should be valid.
     *
     * NOTE: this does not currently repair resources which have been
     * corrupted, merely returns all of the tables to the appropriate states
     */
    private fun repair() {
        // First we need to figure out what state we're in currently. There are
        // a few possibilities

        // TODO: Handle: Upgrade complete (upgrade table empty, all resources
        // pushed to global), recovery table not empty

        // First possibility is needing to restore from the recovery table.
        if (!tempTable.isEmpty()) {
            // If the recovery table isn't empty, we're likely restoring from
            // there. We need to check first whether the global table has the
            // same profile, or the recovery table simply doesn't have one in
            // which case the recovery table didn't get copied correctly.
            val tempProfile =
                tempTable.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID)
            val masterProfile =
                masterTable.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID)
            if (tempProfile == null ||
                (masterProfile != null && masterProfile.getVersion() == tempProfile.getVersion())
            ) {
                Logger.log(
                    LogTypes.TYPE_RESOURCES,
                    "Invalid recovery table detected. Wiping recovery table"
                )
                // This means the recovery table should be empty. Invalid copy.
                tempTable.destroy()
            } else {
                // We need to recover the global resources from the recovery
                // table.
                Logger.log(
                    LogTypes.TYPE_RESOURCES,
                    "Recovering global resources from recovery table"
                )

                masterTable.destroy()
                tempTable.copyToTable(masterTable)

                Logger.log(
                    LogTypes.TYPE_RESOURCES,
                    "Global resources recovered. Wiping recovery table"
                )
                tempTable.destroy()
            }
        }

        // Global and incoming are now in the right places. Ensure we have no
        // uncommitted resources.
        if (masterTable.getTableReadiness() == ResourceTable.RESOURCE_TABLE_UNCOMMITED) {
            masterTable.rollbackCommits(platform)
        }

        if (upgradeTable.getTableReadiness() == ResourceTable.RESOURCE_TABLE_UNCOMMITED) {
            upgradeTable.rollbackCommits(platform)
        }

        // If the global table needed to be recovered from the recovery table,
        // it has. There are now two states: Either the global table is fully
        // installed (no conflicts with the upgrade table) or it has unstaged
        // resources to restage
        if (masterTable.getTableReadiness() == ResourceTable.RESOURCE_TABLE_INSTALLED) {
            Logger.log(
                LogTypes.TYPE_RESOURCES,
                "Global table in fully installed mode. Repair complete"
            )
        } else if (masterTable.getTableReadiness() == ResourceTable.RESOURCE_TABLE_UNSTAGED) {
            Logger.log(
                LogTypes.TYPE_RESOURCES,
                "Global table needs to restage some resources"
            )
            masterTable.repairTable(upgradeTable, platform)
        }
    }

    /**
     * Set listeners and checkers that enable communication between low-level
     * resource installation and top-level app update/installation process.
     *
     * @param tableListener  allows resource table to report its progress to the
     *                       launching process
     * @param cancelCheckker allows resource installers to check if the
     *                       launching process was cancelled
     */
    fun setUpgradeListeners(
        tableListener: TableStateListener,
        cancelCheckker: InstallCancelled
    ) {
        masterTable.setStateListener(tableListener)
        upgradeTable.setStateListener(tableListener)

        upgradeTable.setInstallCancellationChecker(cancelCheckker)
    }

    fun isUpgradeTableStaged(): Boolean {
        return isTableStagedForUpgrade(upgradeTable)
    }

    /**
     * @return True if profile argument points to an app version that isn't
     * any newer than the profile in the upgrade table.
     */
    fun updateNotNewer(currentProfile: Resource): Boolean {
        val newProfile =
            upgradeTable.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID)
        return newProfile != null && !newProfile.isNewer(currentProfile)
    }

    fun getMasterProfile(): Resource? {
        return masterTable.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID)
    }

    fun clearUpgrade() {
        upgradeTable.clearUpgrade(platform)
    }
}
