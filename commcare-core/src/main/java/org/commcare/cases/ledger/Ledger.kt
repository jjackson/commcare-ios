package org.commcare.cases.ledger

import org.javarosa.core.services.storage.IMetaData
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapMap
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * A Ledger is a data model which tracks numeric data organized into
 * different sections with different meanings.
 *
 * @author ctsims
 */
class Ledger : Persistable, IMetaData {

    // NOTE: Right now this is (lazily) implemented assuming that each ledger
    // object tracks _all_ of the sections for an entity, which will likely be a terrible way
    // to do things long-term.

    companion object {
        const val STORAGE_KEY: String = "ledger"
        const val INDEX_ENTITY_ID: String = "entity-id"
    }

    private var entityId: String? = null
    private var recordId: Int = -1
    private var sections: HashMap<String, HashMap<String, Int>> = HashMap()

    constructor()

    constructor(entityId: String) {
        this.entityId = entityId
        this.sections = HashMap()
    }

    /**
     * Get the ID of the linked entity associated with this Ledger record
     */
    fun getEntiyId(): String? {
        return entityId
    }

    /**
     * Retrieve an entry from a specific section of the ledger.
     *
     * If no entry is defined, the ledger will return the value '0'
     *
     * @param sectionId The section containing the entry
     * @param entryId   The Id of the entry to retrieve
     * @return the entry value. '0' if no entry exists.
     */
    fun getEntry(sectionId: String, entryId: String): Int {
        if (!sections.containsKey(sectionId) || !sections[sectionId]!!.containsKey(entryId)) {
            return 0
        }
        return sections[sectionId]!![entryId]!!
    }

    /**
     * @return The list of sections available in this ledger
     */
    fun getSectionList(): Array<String> {
        val sectionList = arrayOfNulls<String>(sections.size)
        var i = 0
        val e = sections.keys.iterator()
        while (e.hasNext()) {
            sectionList[i] = e.next()
            ++i
        }
        @Suppress("UNCHECKED_CAST")
        return sectionList as Array<String>
    }

    /**
     * Retrieves a list of all entries (by ID) defined in a
     * section of the ledger
     *
     * @param sectionId The ID of a section
     * @return The IDs of all entries defined in the provided section
     */
    fun getListOfEntries(sectionId: String): Array<String> {
        val entries = sections[sectionId]!!
        val entryList = arrayOfNulls<String>(entries.size)
        var i = 0
        val e = entries.keys.iterator()
        while (e.hasNext()) {
            entryList[i] = e.next()
            ++i
        }
        @Suppress("UNCHECKED_CAST")
        return entryList as Array<String>
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        recordId = ExtUtil.readInt(`in`)
        entityId = ExtUtil.readString(`in`)
        @Suppress("UNCHECKED_CAST")
        sections = ExtUtil.read(`in`, ExtWrapMap(String::class.java, ExtWrapMap(String::class.java, Int::class.javaObjectType)), pf) as HashMap<String, HashMap<String, Int>>
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeNumeric(out, recordId.toLong())
        ExtUtil.writeString(out, entityId ?: "")
        ExtUtil.write(out, ExtWrapMap(sections, ExtWrapMap(String::class.java, Int::class.javaObjectType)))
    }

    override fun setID(ID: Int) {
        recordId = ID
    }

    override fun getID(): Int {
        return recordId
    }

    /**
     * Sets the value of an entry in the specified section of this ledger
     */
    fun setEntry(sectionId: String, entryId: String, quantity: Int) {
        if (!sections.containsKey(sectionId)) {
            sections[sectionId] = HashMap()
        }
        sections[sectionId]!![entryId] = quantity
    }

    override fun getMetaDataFields(): Array<String> {
        return arrayOf(INDEX_ENTITY_ID)
    }

    override fun getMetaData(fieldName: String): Any {
        if (fieldName == INDEX_ENTITY_ID) {
            return entityId!!
        } else {
            throw IllegalArgumentException("No metadata field $fieldName in the ledger storage system")
        }
    }
}
