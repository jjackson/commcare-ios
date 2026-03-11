package org.commcare.resources.model

import org.commcare.resources.ResourceInstallContext
import org.commcare.resources.model.installers.ProfileInstaller
import org.commcare.util.CommCarePlatform
import org.commcare.util.LogTypes
import org.javarosa.core.reference.InvalidReferenceException
import org.javarosa.core.reference.Reference
import org.javarosa.core.reference.ReferenceManager
import org.javarosa.core.services.Logger
import org.javarosa.core.services.storage.IStorageIterator
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.util.SizeBoundUniqueVector
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import java.io.FileNotFoundException
import kotlin.jvm.JvmStatic

/**
 * A Resource Table maintains a set of Resource Records,
 * resolves dependencies between records, and provides hooks
 * for maintenance, updating, and initializing resources.
 *
 * @author ctsims
 */
open class ResourceTable {

    // TODO: We have too many vectors here. It's lazy and incorrect. Everything
    // should be using iterators, not VECTORS;

    private var storage: IStorageUtilityIndexed<*>? = null
    private var factory: InstallerFactory? = null

    private var stateListener: TableStateListener? = null
    private var cancellationChecker: InstallCancelled? = null
    private var installStatsLogger: InstallStatsLogger? = null

    // Tracks whether a compound resource has been added, requiring
    // recalculation of how many uninstalled resources there are.  Where
    // 'compound resources' are those that contain references to more
    // resources, such as profile and suite resources.
    private var isResourceProgressStale = false
    // Cache for profile and suite 'parent' resources which are used in
    // references resolution

    private val compoundResourceCache = HashMap<String, Resource>()
    private var mMissingResources = SizeBoundUniqueVector<Resource>(0)

    constructor()

    protected constructor(
        storage: IStorageUtilityIndexed<*>,
        factory: InstallerFactory
    ) {
        this.storage = storage
        this.factory = factory
    }

    fun isEmpty(): Boolean {
        return storage!!.getNumRecords() <= 0
    }

    fun getTableReadiness(): Int {
        // TODO: this is very hard to fully specify without doing assertions
        // when preparing a table about appropriate states

        var isFullyInstalled = true
        var isEmpty = true
        var unstaged = false
        var upgrade = false
        var dirty = false

        val it = storage!!.iterate()
        while (it.hasMore()) {
            val r = it.nextRecord() as Resource
            if (r.getStatus() != Resource.RESOURCE_STATUS_INSTALLED) {
                isFullyInstalled = false
            }
            if (r.getStatus() != Resource.RESOURCE_STATUS_UNINITIALIZED) {
                isEmpty = false
            }

            if (r.getStatus() == Resource.RESOURCE_STATUS_UNSTAGED) {
                unstaged = true
            }
            if (r.getStatus() == Resource.RESOURCE_STATUS_UPGRADE) {
                upgrade = true
            }
            if (r.isDirty()) {
                dirty = true
            }
        }

        if (dirty) {
            return RESOURCE_TABLE_UNCOMMITED
        }
        if (isEmpty) {
            return RESOURCE_TABLE_EMPTY
        }
        if (isFullyInstalled) {
            return RESOURCE_TABLE_INSTALLED
        }
        if (unstaged) {
            return RESOURCE_TABLE_UNSTAGED
        }
        if (upgrade) {
            return RESOURCE_TABLE_UPGRADE
        }

        return RESOURCE_TABLE_PARTIAL
    }

    fun getInstallers(): InstallerFactory {
        return factory!!
    }

    fun addResource(
        resource: Resource, initializer: ResourceInstaller<*>,
        parentId: String?, status: Int
    ) {
        resource.setInstaller(initializer)
        resource.setParentId(parentId)
        addResource(resource, status)
    }

    fun addResource(
        resource: Resource, initializer: ResourceInstaller<*>,
        parentId: String?
    ) {
        addResource(resource, initializer, parentId, Resource.RESOURCE_STATUS_UNINITIALIZED)
    }

    fun addResource(resource: Resource, status: Int) {
        if (resourceDoesntExist(resource)) {
            addResourceInner(resource, status)
        }
    }

    protected open fun resourceDoesntExist(resource: Resource): Boolean {
        return storage!!.getIDsForValue(Resource.META_INDEX_RESOURCE_ID, resource.getResourceId()).size == 0
    }

    private fun addResourceInner(resource: Resource, status: Int) {
        resource.setStatus(status)
        if (resource.getID() != -1) {
            // Assume that we're going cross-table, so we need a new
            // RecordId.
            resource.setID(-1)

            // Check to make sure that there's no existing GUID for
            // this record.
            if (getResourceWithGuid(resource.getRecordGuid()) != null) {
                throw RuntimeException("This resource record already exists.")
            }
        }

        commit(resource)
    }

    fun getResourcesForParent(parent: String): ArrayList<Resource> {
        val v = ArrayList<Resource>()
        val en = storage!!.getIDsForValue(Resource.META_INDEX_PARENT_GUID, parent).iterator()
        while (en.hasNext()) {
            val r = storage!!.read(en.next() as Int) as Resource
            v.add(r)
        }
        return v
    }

    fun getResourceWithId(id: String): Resource? {
        return try {
            storage!!.getRecordForValue(Resource.META_INDEX_RESOURCE_ID, id) as Resource
        } catch (nsee: NoSuchElementException) {
            null
        }
    }

    fun getResourceWithGuid(guid: String): Resource? {
        return try {
            storage!!.getRecordForValue(Resource.META_INDEX_RESOURCE_GUID, guid) as Resource
        } catch (nsee: NoSuchElementException) {
            null
        }
    }

    fun getResource(rowId: Int): Resource? {
        return try {
            storage!!.read(rowId) as Resource
        } catch (nsee: NoSuchElementException) {
            null
        }
    }

    private fun getParentResource(resource: Resource): Resource? {
        val parentId = resource.getParentId()
        if (parentId != null && "" != parentId) {
            if (compoundResourceCache.containsKey(parentId)) {
                return compoundResourceCache[parentId]
            } else {
                return try {
                    val parent =
                        storage!!.getRecordForValue(Resource.META_INDEX_RESOURCE_GUID, parentId) as Resource
                    compoundResourceCache[parentId] = parent
                    parent
                } catch (nsee: NoSuchElementException) {
                    null
                }
            }
        }
        return null
    }

    /**
     * Get the all the resources in this table's storage.
     */
    private fun getResources(): ArrayList<Resource> {
        val v = ArrayList<Resource>()
        val it = storage!!.iterate()
        while (it.hasMore()) {
            val r = it.nextRecord() as Resource
            v.add(r)
        }
        return v
    }

    /**
     * Get the all the resources in this table's storage.
     */
    private fun getResourceStack(): ArrayDeque<Resource> {
        val v = ArrayDeque<Resource>()
        val it = storage!!.iterate()
        while (it.hasMore()) {
            val r = it.nextRecord() as Resource
            v.addLast(r)
        }
        return v
    }

    /**
     * Get the resources in this table's storage that have a given status.
     */
    private fun getResourceStackWithStatus(status: Int): ArrayDeque<Resource> {
        val v = ArrayDeque<Resource>()
        val it = storage!!.iterate()
        while (it.hasMore()) {
            val r = it.nextRecord() as Resource
            if (r.getStatus() == status) {
                v.addLast(r)
            }
        }
        return v
    }

    /**
     * Get stored resources that are unready for installation, that is, not of
     * installed, upgrade, or pending status.
     *
     * Resources that are:
     * - installed don't need anything
     * - marked as ready for upgrade are ready
     * - marked as pending aren't capable of installation yet
     *
     * @return ArrayDeque of resource records that aren't ready for installation
     */
    private fun getUnreadyResources(): ArrayDeque<Resource> {
        val v = ArrayDeque<Resource>()
        val it = storage!!.iterate()
        while (it.hasMore()) {
            val r = it.nextRecord() as Resource
            if (r.getStatus() != Resource.RESOURCE_STATUS_INSTALLED &&
                r.getStatus() != Resource.RESOURCE_STATUS_UPGRADE
            ) {
                v.addLast(r)
            }
        }
        return v
    }

    /**
     * Are all the resources ready to be installed or have already been
     * installed?
     */
    fun isReady(): Boolean {
        return getUnreadyResources().isEmpty()
    }

    @Throws(UnresolvedResourceException::class)
    fun commitCompoundResource(r: Resource, status: Int, version: Int) {
        if (r.getVersion() == Resource.RESOURCE_VERSION_UNKNOWN) {
            // Try to update the version.
            r.setVersionIfUnknown(version)
        } else {
            // Otherwise, someone screwed up
            Logger.log(LogTypes.TYPE_RESOURCES, "committing a resource with a known version.")
        }
        commitCompoundResource(r, status)
    }

    /**
     * Add a 'compound' resource, which has references to other resources.
     *
     * @param r profile, suite, media suite, or other 'compound' resource
     */
    fun commitCompoundResource(r: Resource, status: Int) {
        compoundResourceCache[r.getResourceId()] = r
        isResourceProgressStale = true
        commit(r, status)
    }

    fun commit(r: Resource, status: Int) {
        r.setStatus(status)
        commit(r)
    }

    fun commit(r: Resource) {
        storage!!.write(r)
    }

    /**
     * Rolls back uncommitted resources from dirty states
     */
    fun rollbackCommits(platform: CommCarePlatform) {
        val s = this.getResourceStack()
        while (!s.isEmpty()) {
            val r = s.removeLast()
            if (r.isDirty()) {
                this.commit(r, r.getInstaller().rollback(r, platform))
            }
        }
    }

    /**
     * Install a resource by looping through its locations stopping at first
     * successful install.
     *
     * @param r        Resource to install
     * @param invalid  out-of-date locations to be avoided during resource
     *                 installation
     * @param upgrade  Has an older version of the resource been installed?
     * @param platform The CommCare platform (specific profile and version) to
     *                 prepare against
     * @param master   Backup resource table to look-up resources not found in
     *                 the current table
     * @throws UnresolvedResourceException Raised when no definitions for
     *                                     resource 'r' can't be found
     */
    @Throws(
        UnresolvedResourceException::class,
        UnfullfilledRequirementsException::class,
        InstallCancelledException::class
    )
    private fun findResourceLocationAndInstall(
        r: Resource,
        invalid: ArrayList<Reference>,
        upgrade: Boolean,
        platform: CommCarePlatform,
        master: ResourceTable?,
        resourceInstallContext: ResourceInstallContext
    ) {
        // TODO: Possibly check if resource status is local and proceeding to
        // skip this huge (although in reality like one step) chunk

        var unreliableSourceException: UnreliableSourceException? = null
        var invalidResourceException: InvalidResourceException? = null
        var unresolvedResourceException: UnresolvedResourceException? = null

        var handled = false

        for (location in r.getLocations()) {
            if (handled) {
                break
            }

            if (location.isRelative()) {
                for (ref in gatherLocationsRefs(location, r, this, master)) {
                    if (!(location.getAuthority() == Resource.RESOURCE_AUTHORITY_LOCAL && invalid.contains(ref))) {
                        try {
                            handled = installResource(
                                r, location, ref, this,
                                platform, upgrade, resourceInstallContext
                            )
                        } catch (e: InvalidResourceException) {
                            invalidResourceException = e
                        } catch (use: UnreliableSourceException) {
                            unreliableSourceException = use
                        } catch (ure: UnresolvedResourceException) {
                            unresolvedResourceException = ure
                        }
                        if (handled) {
                            recordSuccess(r)
                            break
                        }
                    }
                }
            } else {
                try {
                    handled = installResource(
                        r, location,
                        ReferenceManager.instance().DeriveReference(location.getLocation()),
                        this, platform, upgrade, resourceInstallContext
                    )
                    if (handled) {
                        recordSuccess(r)
                        break
                    }
                } catch (e: InvalidResourceException) {
                    invalidResourceException = e
                } catch (ire: InvalidReferenceException) {
                    ire.printStackTrace()
                    // Continue until no resources can be found.
                } catch (use: UnreliableSourceException) {
                    unreliableSourceException = use
                } catch (ure: UnresolvedResourceException) {
                    unresolvedResourceException = ure
                }
            }
        }

        if (!handled) {
            if (invalidResourceException != null) {
                throw invalidResourceException
            } else if (unresolvedResourceException != null) {
                throw unresolvedResourceException
            } else if (unreliableSourceException == null) {
                // no particular failure to point our finger at.
                throw UnresolvedResourceException(
                    r,
                    "No external or local definition could be found for resource " + r.getDescriptor()
                            + " with id " + r.getResourceId()
                )
            } else {
                // Expose the lossy failure rather than the generic one
                throw unreliableSourceException
            }
        }
    }

    /**
     * Makes all of this table's resources available.
     *
     * @param master   The global resource to prepare against. Used to
     *                 establish whether resources need to be fetched remotely
     * @param platform The platform (version and profile) to prepare against
     * @throws UnresolvedResourceException       If a resource could not be
     *                                           identified and is required
     * @throws UnfullfilledRequirementsException If some resources are
     *                                           incompatible with the current
     *                                           version of CommCare
     */
    @Throws(
        UnresolvedResourceException::class,
        UnfullfilledRequirementsException::class,
        InstallCancelledException::class
    )
    fun prepareResources(
        master: ResourceTable?,
        platform: CommCarePlatform,
        resourceInstallContext: ResourceInstallContext
    ) {
        var masterResourceMap: HashMap<String, Resource>? = null
        if (master != null) {
            // avoid hitting storage in loops by front-loading resource
            // acquisition from master table
            masterResourceMap = getResourceMap(master)
        }
        var unreadyResources = getUnreadyResources()

        // install all unready resources.
        while (!unreadyResources.isEmpty()) {
            for (r in unreadyResources) {
                prepareResource(master, platform, r, masterResourceMap, resourceInstallContext)
            }
            // Installing resources may have exposed more unready resources
            // that need installing.
            unreadyResources = getUnreadyResources()
        }
    }

    /**
     * Makes all resources available until toInitialize is encountered.
     *
     * @param master       The global resource to prepare against. Used to
     *                     establish whether resources need to be fetched remotely
     * @param platform     The platform (version and profile) to prepare against
     * @param toInitialize The ID of a single resource after which the table
     *                     preparation can stop.
     * @throws UnresolvedResourceException       Required resource couldn't be
     *                                           identified
     * @throws UnfullfilledRequirementsException resource(s) incompatible with
     *                                           current CommCare version
     */
    @Throws(
        UnresolvedResourceException::class,
        UnfullfilledRequirementsException::class,
        InstallCancelledException::class
    )
    fun prepareResourcesUpTo(
        master: ResourceTable,
        platform: CommCarePlatform,
        toInitialize: String,
        resourceInstallContext: ResourceInstallContext
    ) {
        var unreadyResources = getUnreadyResources()

        // install unready resources, until toInitialize has been installed.
        while (isResourceUninitialized(toInitialize) && !unreadyResources.isEmpty()) {
            for (r in unreadyResources) {
                prepareResource(master, platform, r, null, resourceInstallContext)
            }
            // Installing resources may have exposed more unready resources
            // that need installing.
            unreadyResources = getUnreadyResources()
        }
    }

    /**
     * @param master            The global resource to prepare against. Used to
     *                          establish whether resources need to be fetched
     *                          remotely
     * @param masterResourceMap Map from resource id to resources for master
     */
    @Throws(
        UnresolvedResourceException::class,
        UnfullfilledRequirementsException::class,
        InstallCancelledException::class
    )
    private fun prepareResource(
        master: ResourceTable?, platform: CommCarePlatform,
        r: Resource, masterResourceMap: HashMap<String, Resource>?,
        resourceInstallContext: ResourceInstallContext
    ) {
        var upgrade = false

        var invalid = ArrayList<Reference>()

        if (master != null) {
            val peer: Resource?
            // obtain resource peer by looking up the current resource
            // in the master table
            if (masterResourceMap == null) {
                peer = master.getResourceWithId(r.getResourceId())
            } else {
                peer = masterResourceMap[r.getResourceId()]
            }
            if (peer != null) {
                if (!r.isNewer(peer)) {
                    // This resource doesn't need to be updated, copy
                    // the existing resource into this table
                    peer.mimick(r)
                    commit(peer, Resource.RESOURCE_STATUS_INSTALLED)

                    if (stateListener != null) {
                        // copying a resource over shouldn't add anymore
                        // resources to be processed
                        stateListener!!.simpleResourceAdded()
                    }
                    return
                }

                // resource is newer than master version, so invalidate
                // old local resource locations.
                upgrade = true
                invalid = gatherResourcesLocalRefs(peer, master)
            }
        }

        findResourceLocationAndInstall(r, invalid, upgrade, platform, master, resourceInstallContext)

        if (stateListener != null) {
            if (isResourceProgressStale) {
                // a compound resource was added, recalculate total resource count
                isResourceProgressStale = false
                stateListener!!.compoundResourceAdded(this)
            } else {
                stateListener!!.simpleResourceAdded()
            }
        }
    }

    /**
     * Force a recomputation of table stage progress; useful for resuming upgrades
     */
    fun setResourceProgressStale() {
        isResourceProgressStale = true
    }

    private fun isResourceUninitialized(resourceId: String): Boolean {
        val res = this.getResourceWithId(resourceId)
        return (res == null ||
                res.getStatus() == Resource.RESOURCE_STATUS_UNINITIALIZED)
    }

    /**
     * Call the resource's installer, handling the logic around attempting
     * retries.
     *
     * @return Did the resource install successfully?
     */
    @Throws(
        UnresolvedResourceException::class,
        UnfullfilledRequirementsException::class,
        InstallCancelledException::class
    )
    private fun installResource(
        r: Resource, location: ResourceLocation,
        ref: Reference, table: ResourceTable,
        platform: CommCarePlatform, upgrade: Boolean, resourceInstallContext: ResourceInstallContext
    ): Boolean {
        var aFailure: UnreliableSourceException? = null

        for (i in 0 until NUMBER_OF_LOSSY_RETRIES + 1) {
            abortIfInstallCancelled(r)
            try {
                return r.getInstaller().install(r, location, ref, table, platform, upgrade, resourceInstallContext)
            } catch (use: UnreliableSourceException) {
                recordFailure(r, use)
                aFailure = use
            }
        }

        Logger.log(
            LogTypes.TYPE_RESOURCES, "Install attempt unsuccessful from: " +
                    ref.getURI() + "|" + aFailure!!.message
        )
        throw aFailure
    }

    @Throws(InstallCancelledException::class)
    private fun abortIfInstallCancelled(r: Resource) {
        if (cancellationChecker != null && cancellationChecker!!.wasInstallCancelled()) {
            val installException =
                InstallCancelledException("Installation/upgrade was cancelled while processing " + r.getResourceId())
            recordFailure(r, installException)
            throw installException
        }
    }

    private fun recordFailure(resource: Resource, e: Exception) {
        if (installStatsLogger != null) {
            installStatsLogger!!.recordResourceInstallFailure(resource.getResourceId(), e)
        }
    }

    private fun recordSuccess(resource: Resource) {
        if (installStatsLogger != null) {
            installStatsLogger!!.recordResourceInstallSuccess(resource.getResourceId())
        }
    }

    /**
     * Prepare this table to be replaced by the incoming table, and incoming
     * table to replace it.
     *
     * All conflicting resources from this table will be unstaged so as to not
     * conflict with the incoming resources. Once the incoming table is fully
     * installed, this table's resources can then be fully removed where
     * relevant.
     *
     * @param incoming Table for which resource upgrades are applied
     */
    @Throws(UnresolvedResourceException::class)
    fun upgradeTable(incoming: ResourceTable, platform: CommCarePlatform) {
        if (!incoming.isReady()) {
            throw RuntimeException("Incoming table is not ready to be upgraded")
        }

        // Everything incoming should be marked either ready or upgrade.
        // Upgrade elements should result in their counterpart in this table
        // being unstaged (which can be reverted).
        val resourceMap = getResourceMap(this)
        val it = incoming.storage!!.iterate()
        while (it.hasMore()) {
            val r = it.nextRecord() as Resource
            val peer = resourceMap[r.getResourceId()]
            if (peer == null) {
                // no corresponding resource in this table; use incoming
                addResource(r, Resource.RESOURCE_STATUS_INSTALLED)
            } else {
                if (r.isNewer(peer)) {
                    // Mark as being ready to transition
                    commit(peer, Resource.RESOURCE_STATUS_INSTALL_TO_UNSTAGE)

                    if (!peer.getInstaller().unstage(peer, Resource.RESOURCE_STATUS_UNSTAGED, platform)) {
                        // TODO: revert this resource table!
                        throw UnresolvedResourceException(
                            peer,
                            "Couldn't make room for new resource " +
                                    r.getResourceId() + ", upgrade aborted"
                        )
                    } else {
                        // done
                        commit(peer, Resource.RESOURCE_STATUS_UNSTAGED)
                    }

                    if (r.getStatus() == Resource.RESOURCE_STATUS_UPGRADE) {
                        incoming.commit(r, Resource.RESOURCE_STATUS_UPGRADE_TO_INSTALL)
                        if (r.getInstaller().upgrade(r, platform)) {
                            incoming.commit(r, Resource.RESOURCE_STATUS_INSTALLED)
                        } else {
                            Logger.log(
                                LogTypes.TYPE_RESOURCES,
                                "Failed to upgrade resource: " + r.getDescriptor()
                            )
                            // REVERT!
                            throw RuntimeException("Failed to upgrade resource " + r.getDescriptor())
                        }
                    }
                }
                // TODO Should anything happen if peer.getVersion() ==
                // r.getVersion()?  Consider children, IDs and the fact
                // resource locations could change
            }
        }
    }

    /**
     * Uninstall table by removing unstaged resources and those not present in
     * replacement table
     *
     * This method is the final step in an update, after this table has
     * already been moved to a placeholder table and been evaluated for
     * what resources are no longer necessary.
     *
     * If this table encounters any problems it will not intentionally
     * throw errors, assuming that it's preferable to leave data unremoved
     * rather than breaking the app.
     *
     * @param replacement Reference table; uninstall resources not also present
     *                    in this table
     */
    fun uninstall(replacement: ResourceTable, platform: CommCarePlatform) {
        cleanup()
        val replacementMap = getResourceMap(replacement)
        val it = storage!!.iterate()
        while (it.hasMore()) {
            val r = it.nextRecord() as Resource
            if (replacementMap[r.getResourceId()] == null ||
                r.getStatus() == Resource.RESOURCE_STATUS_UNSTAGED
            ) {
                // No entry in 'replacement' so it's no longer relevant
                // OR resource has been replaced, so flag for deletion
                try {
                    r.getInstaller().uninstall(r, platform)
                } catch (e: Exception) {
                    Logger.log(
                        LogTypes.TYPE_RESOURCES, "Error uninstalling resource " +
                                r.getRecordGuid() + ". " + e.message
                    )
                }
            } else if (r.getStatus() == Resource.RESOURCE_STATUS_DELETE) {
                // NOTE: Shouldn't be a way for this condition to occur, but check anyways...
                try {
                    r.getInstaller().uninstall(r, platform)
                } catch (e: Exception) {
                    Logger.log(
                        LogTypes.TYPE_RESOURCES, "Error uninstalling resource " +
                                r.getRecordGuid() + ". " + e.message
                    )
                }
            }
        }

        storage!!.removeAll()
    }

    /**
     * Called on a table to restage any unstaged resources.
     *
     * @param incoming The table which unstaged this table's resources
     */
    fun repairTable(incoming: ResourceTable?, platform: CommCarePlatform) {
        val s = this.getResourceStackWithStatus(Resource.RESOURCE_STATUS_UNSTAGED)
        while (!s.isEmpty()) {
            val resource = s.removeLast()

            if (incoming != null) {
                // See if there's a competing resource
                val peer = incoming.getResourceWithId(resource.getResourceId())

                // If there is, and it's been installed, unstage it to make room again
                if (peer != null && peer.getStatus() == Resource.RESOURCE_STATUS_INSTALLED) {
                    incoming.commit(peer, Resource.RESOURCE_STATUS_INSTALL_TO_UPGRADE)
                    // TODO: Is there anything we can do about this? Shouldn't it be an exception?
                    if (!peer.getInstaller().unstage(peer, Resource.RESOURCE_STATUS_UPGRADE, platform)) {
                        // TODO: IF there are errors here, signal that the incoming table
                        // should just be wiped out. It's not in acceptable shape
                    } else {
                        incoming.commit(peer, Resource.RESOURCE_STATUS_UPGRADE)
                    }
                }
            }

            // Way should be clear.
            this.commit(resource, Resource.RESOURCE_STATUS_UNSTAGE_TO_INSTALL)
            if (resource.getInstaller().revert(resource, this, platform)) {
                this.commit(resource, Resource.RESOURCE_STATUS_INSTALLED)
            }
        }
    }

    /**
     * Copy all of this table's resource records to the (empty) table provided.
     *
     * @throws IllegalArgumentException If incoming table is not empty
     */
    @Throws(IllegalArgumentException::class)
    fun copyToTable(newTable: ResourceTable) {
        if (!newTable.isEmpty()) {
            throw IllegalArgumentException("Can't copy into a table with data in it!")
        }

        // Copy over all of our resources to the new table
        val it = storage!!.iterate()
        while (it.hasMore()) {
            val r = it.nextRecord() as Resource
            r.setID(-1)
            newTable.commit(r)
        }
    }

    /**
     * String representation of the id, version, and status of all resources in
     * table.
     */
    override fun toString(): String {
        val resourceDetails = StringBuilder()
        var maxLength = 0
        val it = storage!!.iterate()
        while (it.hasMore()) {
            val r = it.nextRecord() as Resource
            val line = "| " + r.getResourceId() + " | " + r.getVersion() +
                    " | " + getStatusString(r.getStatus()) + " |\n"
            resourceDetails.append(line)

            if (line.length > maxLength) {
                maxLength = line.length
            }
        }

        val header = StringBuilder()
        for (i in 0 until maxLength) {
            header.append("-")
        }

        header.append("\n")

        return header.append(resourceDetails.toString()).append(header.toString()).toString()
    }

    /**
     * Destroy this table, but leave any of the files which are installed
     * untouched. This is useful after an upgrade if this is the temp table.
     */
    fun destroy() {
        cleanup()
        storage!!.removeAll()
    }

    // Uninstalls resources with status RESOURCE_STATUS_UPGRADE in the table and wipe the complete table
    fun clearUpgrade(platform: CommCarePlatform) {
        uninstallResourcesForStatus(platform, Resource.RESOURCE_STATUS_UPGRADE)
        storage!!.removeAll()
    }

    // Clears all resources in the table and wipe the complete table
    fun clearAll(platform: CommCarePlatform) {
        uninstallResourcesForStatus(platform, RESOURCE_STATUS_ALL_RESOURCES)
        storage!!.removeAll()
    }

    /**
     * Uninstalls any resources with a given resource status and also try very hard to remove any files installed
     * by it. This is important for rolling back botched upgrades without leaving their files around.
     *
     * @param platform       CommCare platform
     * @param resourceStatus Only resources with this status will get cleared
     */
    private fun uninstallResourcesForStatus(platform: CommCarePlatform, resourceStatus: Int) {
        cleanup()
        val s = this.getResourceStack()
        var count = 0
        while (!s.isEmpty()) {
            val r = s.removeLast()
            if (r.getStatus() == resourceStatus || resourceStatus == RESOURCE_STATUS_ALL_RESOURCES) {
                try {
                    r.getInstaller().uninstall(r, platform)
                    count++
                } catch (e: UnresolvedResourceException) {
                    // already gone!
                }
            }
        }
        if (count > 0) {
            Logger.log(LogTypes.TYPE_RESOURCES, "Uninstalled " + count + " records from table")
        }
    }

    protected open fun cleanup() {
        compoundResourceCache.clear()
        val it = storage!!.iterate()
        while (it.hasMore()) {
            val r = it.nextRecord() as Resource
            r.getInstaller().cleanup()
        }
    }

    /**
     * Register the available resources in this table with the provided
     * CommCare platform.
     */
    @Throws(ResourceInitializationException::class)
    fun initializeResources(platform: CommCarePlatform, isUpgrade: Boolean) {
        val missingResources = SizeBoundUniqueVector<Resource>(storage!!.getNumRecords())
        val lateInit = ArrayList<Resource>()
        val it = storage!!.iterate()
        while (it.hasMore()) {
            val r = it.nextRecord() as Resource
            val i = r.getInstaller()
            if (i.requiresRuntimeInitialization()) {
                if (i is ProfileInstaller) {
                    lateInit.add(r)
                } else {
                    attemptResourceInitialization(platform, isUpgrade, r, missingResources)
                }
            }
        }
        for (r in lateInit) {
            attemptResourceInitialization(platform, isUpgrade, r, missingResources)
        }
        setMissingResources(missingResources)
    }

    @Throws(ResourceInitializationException::class)
    fun attemptResourceInitialization(
        platform: CommCarePlatform, isUpgrade: Boolean,
        r: Resource, missingResources: ArrayList<Resource>
    ) {
        try {
            r.getInstaller().initialize(platform, isUpgrade)
        } catch (e: FileNotFoundException) {
            missingResources.add(r)
        } catch (e: PlatformIOException) {
            throw ResourceInitializationException(r, e)
        } catch (e: InvalidStructureException) {
            throw ResourceInitializationException(r, e)
        } catch (e: InvalidReferenceException) {
            throw ResourceInitializationException(r, e)
        } catch (e: PlatformXmlParserException) {
            throw ResourceInitializationException(r, e)
        } catch (e: UnfullfilledRequirementsException) {
            throw ResourceInitializationException(r, e)
        }

        if (r.getStatus() == Resource.RESOURCE_STATUS_UNINITIALIZED) {
            Logger.log(
                LogTypes.SOFT_ASSERT, "Failed to initialize resource with descriptor " + r.getDescriptor()
                        + " and id " + r.getResourceId()
            )
        }
    }

    /**
     * Gather derived references for the resource's local locations. Relative
     * location references that have a parent are contextualized before being
     * added.
     *
     * @param r resource for which local location references are being gathered
     * @param t table to look-up the resource's parents in
     * @return all local references a resource's potential locations
     */
    private fun gatherResourcesLocalRefs(
        r: Resource,
        t: ResourceTable
    ): ArrayList<Reference> {
        val ret = ArrayList<Reference>()

        for (location in r.getLocations()) {
            if (location.isRelative()) {
                if (r.hasParent()) {
                    val parent = t.getParentResource(r)
                    if (parent != null) {
                        // Get local references for the parent resource's
                        // locations
                        val parentRefs = gatherResourcesLocalRefs(parent, t)
                        for (context in parentRefs) {
                            addDerivedLocation(location, context, ret)
                        }
                    }
                }
            } else if (location.getAuthority() == Resource.RESOURCE_AUTHORITY_LOCAL) {
                addDerivedLocation(location, null, ret)
            }
        }
        return ret
    }

    fun verifyInstallation(problems: ArrayList<MissingMediaException>, platform: CommCarePlatform) {
        val resources = getResources()
        val total = resources.size
        var count = 0
        for (r in resources) {
            r.getInstaller().verifyInstallation(r, problems, platform)
            count++
            if (stateListener != null) {
                stateListener!!.incrementProgress(count, total)
            }
            if (cancellationChecker != null && cancellationChecker!!.wasInstallCancelled()) {
                break
            }
        }
    }

    fun setStateListener(listener: TableStateListener?) {
        this.stateListener = listener
    }

    fun setInstallCancellationChecker(cancellationChecker: InstallCancelled?) {
        this.cancellationChecker = cancellationChecker
    }

    fun setInstallStatsLogger(logger: InstallStatsLogger?) {
        this.installStatsLogger = logger
    }

    @Throws(
        InstallCancelledException::class,
        UnresolvedResourceException::class,
        UnfullfilledRequirementsException::class
    )
    fun recoverResources(
        platform: CommCarePlatform, profileRef: String,
        resourceInstallContext: ResourceInstallContext
    ): Boolean {
        return recoverResources(platform, profileRef, resourceInstallContext, mMissingResources)
    }

    // Downloads and re-installs the missingResources into the table
    @Throws(
        InstallCancelledException::class,
        UnresolvedResourceException::class,
        UnfullfilledRequirementsException::class
    )
    private fun recoverResources(
        platform: CommCarePlatform, profileRef: String,
        resourceInstallContext: ResourceInstallContext, missingResources: ArrayList<Resource>
    ): Boolean {
        var count = 0
        val total = missingResources.size
        for (missingResource in missingResources) {

            recoverResource(missingResource, platform, profileRef, resourceInstallContext)

            count++

            if (stateListener != null) {
                stateListener!!.incrementProgress(count, total)
            }

            if (cancellationChecker != null && cancellationChecker!!.wasInstallCancelled()) {
                InstallCancelledException("Resource recovery was cancelled")
            }
        }
        return true
    }

    // Downloads and re-installs a missing resource
    @Throws(
        InstallCancelledException::class,
        UnresolvedResourceException::class,
        UnfullfilledRequirementsException::class
    )
    fun recoverResource(
        missingResource: Resource, platform: CommCarePlatform,
        profileRef: String, resourceInstallContext: ResourceInstallContext
    ) {
        if (missingResource.id.contentEquals(CommCarePlatform.APP_PROFILE_RESOURCE_ID)) {
            addRemoteLocationIfMissing(missingResource, profileRef)
        }

        findResourceLocationAndInstall(missingResource, ArrayList(), false, platform, null, resourceInstallContext)
    }

    private fun addRemoteLocationIfMissing(resource: Resource, remoteLocation: String) {
        val locations = resource.getLocations()
        var remoteLocationPresent = false
        for (location in locations) {
            if (location.getAuthority() == Resource.RESOURCE_AUTHORITY_REMOTE) {
                remoteLocationPresent = true
            }
        }
        if (!remoteLocationPresent) {
            locations.add(ResourceLocation(Resource.RESOURCE_AUTHORITY_REMOTE, remoteLocation))
        }
    }

    fun setMissingResources(missingResources: SizeBoundUniqueVector<Resource>) {
        mMissingResources = missingResources
    }

    fun getMissingResources(): SizeBoundUniqueVector<Resource> {
        return mMissingResources
    }

    fun getLazyResources(): ArrayList<Resource> {
        @Suppress("UNCHECKED_CAST")
        return storage!!.getRecordsForValues(
            arrayOf(Resource.META_INDEX_LAZY),
            arrayOf(Resource.LAZY_VAL_TRUE)
        ) as ArrayList<Resource>
    }

    fun getLazyResourceIds(): ArrayList<Int> {
        @Suppress("UNCHECKED_CAST")
        return storage!!.getIDsForValue(Resource.META_INDEX_LAZY, Resource.LAZY_VAL_TRUE) as ArrayList<Int>
    }

    fun getAllResourceIds(): List<Int> {
        @Suppress("UNCHECKED_CAST")
        return storage!!.getIDsForValues(arrayOf(), arrayOf()) as List<Int>
    }

    companion object {
        // nothing here
        const val RESOURCE_TABLE_EMPTY = 0
        // this is the table currently being used by the app
        const val RESOURCE_TABLE_INSTALLED = 1
        // in any number of intermediate stages
        const val RESOURCE_TABLE_PARTIAL = 2
        // this is the table constructed in order to do an upgrade --
        // means that it is not ready to upgrade the current table
        const val RESOURCE_TABLE_UPGRADE = 3

        const val RESOURCE_TABLE_UNSTAGED = 4
        const val RESOURCE_TABLE_UNCOMMITED = 5

        private const val NUMBER_OF_LOSSY_RETRIES = 3

        // Constant to denote all resources with any kind of status
        const val RESOURCE_STATUS_ALL_RESOURCES = 10001

        @JvmStatic
        fun RetrieveTable(storage: IStorageUtilityIndexed<*>): ResourceTable {
            return RetrieveTable(storage, InstallerFactory())
        }

        @JvmStatic
        fun RetrieveTable(
            storage: IStorageUtilityIndexed<*>,
            factory: InstallerFactory
        ): ResourceTable {
            return ResourceTable(storage, factory)
        }

        @JvmStatic
        fun getStatusString(status: Int): String {
            return when (status) {
                Resource.RESOURCE_STATUS_UNINITIALIZED -> "Uninitialized"
                Resource.RESOURCE_STATUS_LOCAL -> "Local"
                Resource.RESOURCE_STATUS_INSTALLED -> "Installed"
                Resource.RESOURCE_STATUS_UPGRADE -> "Ready for Upgrade"
                Resource.RESOURCE_STATUS_DELETE -> "Flagged for Deletion"
                Resource.RESOURCE_STATUS_UNSTAGED -> "Unstaged"
                Resource.RESOURCE_STATUS_INSTALL_TO_UNSTAGE -> "Install->Unstage (dirty)"
                Resource.RESOURCE_STATUS_INSTALL_TO_UPGRADE -> "Install->Upgrade (dirty)"
                Resource.RESOURCE_STATUS_UNSTAGE_TO_INSTALL -> "Unstage->Install (dirty)"
                Resource.RESOURCE_STATUS_UPGRADE_TO_INSTALL -> "Upgrade->Install (dirty)"
                else -> "Unknown"
            }
        }

        private fun getResourceMap(table: ResourceTable): HashMap<String, Resource> {
            val resourceMap = HashMap<String, Resource>()
            val it = table.storage!!.iterate()
            while (it.hasMore()) {
                val r = it.nextRecord() as Resource
                resourceMap[r.getResourceId()] = r
            }
            return resourceMap
        }

        /**
         * Gather derived references for a particular (relative) location
         * corresponding to the given resource.  If the  parent isn't found in the
         * current resource table, then look in the master table.
         *
         * @param location Specific location for the given resource
         * @param r        Resource for which local location references are being
         *                 gathered
         * @param t        Table to look-up the resource's parents in
         * @param m        Backup table to look-up the resource's parents in
         * @return All possible (derived) references pointing to a given locations
         */
        private fun gatherLocationsRefs(
            location: ResourceLocation,
            r: Resource,
            t: ResourceTable,
            m: ResourceTable?
        ): ArrayList<Reference> {
            val ret = ArrayList<Reference>()

            if (r.hasParent()) {
                var parent = t.getParentResource(r)

                // If the local table doesn't have the parent ref, try the master
                if (parent == null && m != null) {
                    parent = m.getParentResource(r)
                }

                if (parent != null) {
                    // loop over all local references for the parent
                    val parentRefs = explodeAllReferences(location.getAuthority(), parent, t, m)
                    for (context in parentRefs) {
                        addDerivedLocation(location, context, ret)
                    }
                }
            }
            return ret
        }

        /**
         * Gather derived references for the resource's locations of a given type.
         * Relative location references that have a parent are contextualized
         * before being added. If a parent isn't found in the current resource
         * table, then look in the master table.
         *
         * @param type process locations with authorities of this type
         * @param r    resource for which local location references are being gathered
         * @param t    table to look-up the resource's parents in
         * @param m    backup table to look-up the resource's parents in
         * @return all possible (derived) references pointing to a resource's
         * locations
         */
        private fun explodeAllReferences(
            type: Int,
            r: Resource,
            t: ResourceTable,
            m: ResourceTable?
        ): ArrayList<Reference> {
            val ret = ArrayList<Reference>()

            for (location in r.getLocations()) {
                if (location.getAuthority() == type) {
                    if (location.isRelative()) {
                        if (r.hasParent()) {
                            var parent = t.getParentResource(r)

                            // If the local table doesn't have the parent ref, try
                            // the master
                            if (parent == null && m != null) {
                                parent = m.getParentResource(r)
                            }
                            if (parent != null) {
                                // Get all local references for the parent
                                val parentRefs = explodeAllReferences(type, parent, t, m)
                                for (context in parentRefs) {
                                    addDerivedLocation(location, context, ret)
                                }
                            }
                        }
                    } else {
                        addDerivedLocation(location, null, ret)
                    }
                }
            }
            return ret
        }

        /**
         * Derive a reference from the given location and context; adding it to the
         * vector of references.
         *
         * @param location Contains a reference to a resource.
         * @param context  Provides context for any relative reference accessors.
         *                 Can be null.
         * @param ret      Add derived reference of location to this ArrayList.
         */
        private fun addDerivedLocation(
            location: ResourceLocation,
            context: Reference?,
            ret: ArrayList<Reference>
        ) {
            try {
                val derivedRef: Reference
                if (context == null) {
                    derivedRef =
                        ReferenceManager.instance().DeriveReference(location.getLocation())
                } else {
                    // contextualize the location ref in terms of the multiple refs
                    // pointing to different locations for the parent resource
                    derivedRef =
                        ReferenceManager.instance().DeriveReference(
                            location.getLocation(),
                            context
                        )
                }
                ret.add(derivedRef)
            } catch (e: InvalidReferenceException) {
                e.printStackTrace()
            }
        }
    }
}
