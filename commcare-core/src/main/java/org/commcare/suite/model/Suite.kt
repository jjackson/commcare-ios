package org.commcare.suite.model

import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Suites are containers for a set of actions,
 * detail definitions, and UI information. A suite
 * generally contains a set of form entry actions
 * related to the same case ID, sometimes including
 * referrals.
 *
 * @author ctsims
 */
class Suite : Persistable {
    private var version: Int = 0
    private var recordId: Int = -1

    /** Detail id -> Detail Object  */
    private var details: MutableMap<String, Detail>? = null

    /** Entry id (also the same for menus) -> Entry Object  */
    private var entries: MutableMap<String, Entry>? = null
    private val idToMenus: HashMap<String, MutableList<Menu>> = HashMap()
    private val rootToMenus: HashMap<String, MutableList<Menu>> = HashMap()

    private var menus: ArrayList<Menu>? = null
    private var endpoints: MutableMap<String, Endpoint>? = null

    constructor()

    constructor(
        version: Int, details: HashMap<String, Detail>?,
        entries: HashMap<String, Entry>?, menus: ArrayList<Menu>?, endpoints: HashMap<String, Endpoint>?
    ) {
        this.version = version
        this.details = details
        this.entries = entries
        this.menus = menus
        this.endpoints = endpoints
        buildIdToMenus()
    }

    private fun buildIdToMenus() {
        for (menu in menus!!) {
            var menusWithId = idToMenus[menu.getId()]
            if (menusWithId == null) {
                menusWithId = ArrayList()
                idToMenus[menu.getId()!!] = menusWithId
            }
            menusWithId.add(menu)

            var menusWithRoot = rootToMenus[menu.getRoot()]
            if (menusWithRoot == null) {
                menusWithRoot = ArrayList()
                rootToMenus[menu.getRoot()!!] = menusWithRoot
            }
            menusWithRoot.add(menu)
        }
    }

    override fun getID(): Int = recordId

    override fun setID(ID: Int) {
        recordId = ID
    }

    /**
     * @return The menus which define how to access the actions
     * which are available in this suite.
     */
    fun getMenus(): ArrayList<Menu> = menus!!

    fun getMenusWithId(id: String?): List<Menu>? = idToMenus[id]

    fun getMenusWithRoot(root: String?): List<Menu> {
        return if (rootToMenus.containsKey(root)) rootToMenus[root]!! else ArrayList()
    }

    /**
     * WOAH! UNSAFE! Copy, maybe? But this is _wicked_ dangerous.
     *
     * @return The set of entry actions which are defined by this
     * suite, indexed by their id (which is present in the menu
     * definitions).
     */
    fun getEntries(): MutableMap<String, Entry> = entries!!

    fun getEntry(id: String?): Entry? = entries!![id]

    fun getEndpoint(id: String?): Endpoint? = endpoints!![id]

    fun getEndpoints(): MutableMap<String, Endpoint> = endpoints!!

    /**
     * @param id The String ID of a detail definition
     * @return A Detail definition associated with the provided
     * id.
     */
    fun getDetail(id: String?): Detail? = details!![id]

    @Suppress("UNCHECKED_CAST")
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        this.recordId = SerializationHelpers.readInt(`in`)
        this.version = SerializationHelpers.readInt(`in`)
        this.details = SerializationHelpers.readStringExtMap(`in`, pf) { Detail() }
        this.entries = SerializationHelpers.readStringMapPoly(`in`, pf) as MutableMap<String, Entry>
        this.menus = SerializationHelpers.readList(`in`, pf) { Menu() }
        this.endpoints = SerializationHelpers.readStringExtMap(`in`, pf) { Endpoint() }
        buildIdToMenus()
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeNumeric(out, recordId.toLong())
        SerializationHelpers.writeNumeric(out, version.toLong())
        SerializationHelpers.writeMap(out, details!!)
        SerializationHelpers.writeMapPoly(out, entries!!)
        SerializationHelpers.writeList(out, menus!!)
        SerializationHelpers.writeMap(out, endpoints!!)
    }

    companion object {
        const val STORAGE_KEY = "SUITE"
    }
}
