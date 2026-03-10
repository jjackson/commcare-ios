package org.commcare.util

import org.commcare.resources.model.ResourceInitializationException
import org.commcare.resources.model.ResourceTable
import org.commcare.suite.model.Detail
import org.commcare.suite.model.Endpoint
import org.commcare.suite.model.EntityDatum
import org.commcare.suite.model.Entry
import org.commcare.suite.model.FormEntry
import org.commcare.suite.model.Menu
import org.commcare.suite.model.OfflineUserRestore
import org.commcare.suite.model.Profile
import org.commcare.suite.model.SessionDatum
import org.commcare.suite.model.Suite
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.services.PropertyManager
import org.javarosa.core.services.properties.Property
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.services.storage.StorageManager

/**
 * TODO: This isn't really a great candidate for a
 * singleton interfaces. It should almost certainly be
 * a more broad code-based installer/registration
 * process or something.
 *
 * Also: It shares a lot of similarities with the
 * Context app object in j2me. Maybe we should roll
 * some of that in.
 *
 * @author ctsims
 */
open class CommCarePlatform {

    companion object {
        // TODO: We should make this unique using the parser to invalidate this ID or something
        const val APP_PROFILE_RESOURCE_ID = "commcare-application-profile"
    }

    private var profile: Int = -1
    private var cachedProfile: Profile? = null

    private var offlineUserRestore: OfflineUserRestore? = null

    private var storageManager: StorageManager? = null
    private var propertyManager: PropertyManager? = null

    val majorVersion: Int
    val minorVersion: Int
    val minimalVersion: Int
    private val installedSuites: ArrayList<Suite>

    constructor(majorVersion: Int, minorVersion: Int, minimalVersion: Int, storageManager: StorageManager) :
            this(majorVersion, minorVersion, minimalVersion) {
        this.storageManager = storageManager
        storageManager.registerStorage(PropertyManager.STORAGE_KEY, Property::class.java)
        this.propertyManager = PropertyManager(storageManager.getStorage(PropertyManager.STORAGE_KEY))
    }

    constructor(majorVersion: Int, minorVersion: Int, minimalVersion: Int) {
        this.majorVersion = majorVersion
        this.minorVersion = minorVersion
        this.minimalVersion = minimalVersion
        installedSuites = ArrayList()
    }

    fun getCurrentProfile(): Profile {
        if (cachedProfile == null) {
            cachedProfile = storageManager!!.getStorage(Profile.STORAGE_KEY).read(profile) as Profile
        }
        return cachedProfile!!
    }

    fun getInstalledSuites(): ArrayList<Suite> {
        if (!installedSuites.isEmpty()) {
            return installedSuites
        }
        val utility = storageManager!!.getStorage(Suite.STORAGE_KEY)
        val iterator = utility.iterate()
        while (iterator.hasMore()) {
            installedSuites.add(utility.read(iterator.nextID()) as Suite)
        }
        return installedSuites
    }

    fun getDetail(detailId: String): Detail? {
        for (s in getInstalledSuites()) {
            val d = s.getDetail(detailId)
            if (d != null) {
                return d
            }
        }
        return null
    }

    fun getEntry(entryId: String): Entry? {
        for (s in getInstalledSuites()) {
            val e = s.getEntry(entryId)
            if (e != null) {
                return e
            }
        }
        return null
    }

    fun getEndpoint(endpointId: String): Endpoint? {
        for (s in getInstalledSuites()) {
            val endpoint = s.getEndpoint(endpointId)
            if (endpoint != null) {
                return endpoint
            }
        }
        return null
    }

    fun getAllEndpoints(): HashMap<String, Endpoint> {
        val allEndpoints = HashMap<String, Endpoint>()
        for (s in getInstalledSuites()) {
            allEndpoints.putAll(s.getEndpoints())
        }
        return allEndpoints
    }

    fun setProfile(p: Profile) {
        this.profile = p.getID()
        this.cachedProfile = p
    }

    fun registerSuite(s: Suite) {
        installedSuites.add(s)
    }

    /**
     * Register installed resources in the table with this CommCare instance
     *
     * @param global Table with fully-installed resources
     */
    @Throws(ResourceInitializationException::class)
    fun initialize(global: ResourceTable, isUpgrade: Boolean) {
        global.initializeResources(this, isUpgrade)
    }

    fun clearAppState() {
        // Clear out any app state
        profile = -1
    }

    fun getCommandToEntryMap(): HashMap<String, Entry> {
        val installed = getInstalledSuites()
        val merged = HashMap<String, Entry>()

        for (s in installed) {
            val entriesInSuite = s.getEntries()
            val en = entriesInSuite.keys.iterator()
            while (en.hasNext()) {
                val commandId = en.next() as String
                merged.put(commandId, entriesInSuite.get(commandId)!!)
            }
        }
        return merged
    }

    /**
     * Given a form entry object, return the module's id that contains it.
     *
     * @param formEntry Get the module's id that contains this Entry
     * @return The ID of the module that contains the provided entry. Null if
     * the entry can't be found in the installed suites.
     */
    fun getModuleNameForEntry(formEntry: FormEntry): String? {
        val installed = getInstalledSuites()

        for (suite in installed) {
            val e = suite.getEntries().iterator()
            while (e.hasNext()) {
                val suiteEntry = e.next() as FormEntry
                if (suiteEntry.getCommandId() == formEntry.getCommandId()) {
                    return suite.getMenus().first().getId()
                }
            }
        }
        return null
    }

    fun getMenuDisplayStyle(menuId: String): String? {
        val installed = getInstalledSuites()
        var commonDisplayStyle: String? = null
        for (s in installed) {
            val menusWithId = s.getMenusWithId(menuId)
            if (menusWithId != null) {
                for (m in menusWithId) {
                    if (m.getStyle() != null) {
                        if (commonDisplayStyle != null && m.getStyle() != commonDisplayStyle) {
                            return null
                        }
                        commonDisplayStyle = m.getStyle()
                    }
                }
            }
        }
        return commonDisplayStyle
    }

    /**
     * Loops through complete set of detail config and checks whether
     * any of them are cache enabled
     *
     * @return true if entity caching is enabled for any of the detail configs in app, false otherwise
     */
    fun isEntityCachingEnabled(): Boolean {
        val commandMap = getCommandToEntryMap()
        for (command in commandMap.keys) {
            val entry = commandMap[command] ?: continue
            for (sessionDatum in entry.getSessionDataReqs() ?: continue) {
                if (sessionDatum is EntityDatum) {
                    val shortDetailId = sessionDatum.getShortDetail()
                    if (shortDetailId != null) {
                        val detail = getDetail(shortDetailId)
                        if (detail != null && detail.isCacheEnabled) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    fun getDemoUserRestore(): OfflineUserRestore? {
        return offlineUserRestore
    }

    fun registerDemoUserRestore(offlineUserRestore: OfflineUserRestore) {
        this.offlineUserRestore = offlineUserRestore
    }

    @Suppress("UNCHECKED_CAST")
    fun getFixtureStorage(): IStorageUtilityIndexed<FormInstance> {
        storageManager!!.registerStorage("fixture", FormInstance::class.java)
        return storageManager!!.getStorage("fixture") as IStorageUtilityIndexed<FormInstance>
    }

    fun getPropertyManager(): PropertyManager? {
        return propertyManager
    }

    fun getStorageManager(): StorageManager? {
        return storageManager
    }
}
