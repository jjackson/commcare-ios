package org.commcare.suite.model

import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.ExtWrapMap
import org.javarosa.core.util.externalizable.ExtWrapMapPoly
import org.javarosa.core.util.externalizable.PrototypeFactory

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import java.util.ArrayList
import java.util.HashMap

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
    private var details: HashMap<String, Detail>? = null

    /** Entry id (also the same for menus) -> Entry Object  */
    private var entries: HashMap<String, Entry>? = null
    private val idToMenus: HashMap<String, MutableList<Menu>> = HashMap()
    private val rootToMenus: HashMap<String, MutableList<Menu>> = HashMap()

    private var menus: ArrayList<Menu>? = null
    private var endpoints: HashMap<String, Endpoint>? = null

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
    fun getEntries(): HashMap<String, Entry> = entries!!

    fun getEntry(id: String?): Entry? = entries!![id]

    fun getEndpoint(id: String?): Endpoint? = endpoints!![id]

    fun getEndpoints(): HashMap<String, Endpoint> = endpoints!!

    /**
     * @param id The String ID of a detail definition
     * @return A Detail definition associated with the provided
     * id.
     */
    fun getDetail(id: String?): Detail? = details!![id]

    @Suppress("UNCHECKED_CAST")
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        this.recordId = ExtUtil.readInt(`in`)
        this.version = ExtUtil.readInt(`in`)
        this.details = ExtUtil.read(`in`, ExtWrapMap(String::class.java, Detail::class.java), pf) as HashMap<String, Detail>
        this.entries = ExtUtil.read(`in`, ExtWrapMapPoly(String::class.java, true), pf) as HashMap<String, Entry>
        this.menus = ExtUtil.read(`in`, ExtWrapList(Menu::class.java), pf) as ArrayList<Menu>
        this.endpoints = ExtUtil.read(`in`, ExtWrapMap(String::class.java, Endpoint::class.java), pf) as HashMap<String, Endpoint>
        buildIdToMenus()
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeNumeric(out, recordId.toLong())
        ExtUtil.writeNumeric(out, version.toLong())
        ExtUtil.write(out, ExtWrapMap(details!!))
        ExtUtil.write(out, ExtWrapMapPoly(entries!!))
        ExtUtil.write(out, ExtWrapList(menus!!))
        ExtUtil.write(out, ExtWrapMap(endpoints!!))
    }

    companion object {
        const val STORAGE_KEY = "SUITE"
    }
}
