package org.commcare.cases.model

import org.javarosa.core.model.utils.PreloadUtils
import org.javarosa.core.services.storage.IMetaData
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.ExtWrapMapPoly
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.PrototypeFactory

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.model.utils.PlatformDate

/**
 * NOTE: All new fields should be added to the case class using the "data" class,
 * as it demonstrated by the "userid" field. This prevents problems with datatype
 * representation across versions.
 *
 * @author Clayton Sims
 */
open class Case : Persistable, IMetaData {

    @JvmField
    protected var typeId: String? = null

    @JvmField
    protected var id: String? = null

    @JvmField
    protected var name: String? = null

    @JvmField
    protected var closed: Boolean = false

    @JvmField
    protected var dateOpened: PlatformDate? = null

    @JvmField
    protected var recordId: Int = 0

    @JvmField
    protected var data: HashMap<String, Any> = LinkedHashMap()

    @JvmField
    protected var indices: ArrayList<CaseIndex> = ArrayList()

    /**
     * NOTE: This constructor is for serialization only.
     */
    constructor() {
        dateOpened = PlatformDate()
    }

    constructor(name: String?, typeId: String?) {
        setID(-1)
        this.name = name
        this.typeId = typeId
        dateOpened = PlatformDate()
        setLastModified(dateOpened!!)
    }

    fun getTypeId(): String? = typeId

    fun setTypeId(typeId: String?) {
        this.typeId = typeId
    }

    fun getName(): String? = name

    fun setName(name: String?) {
        this.name = name
    }

    fun isClosed(): Boolean = closed

    fun setClosed(closed: Boolean) {
        this.closed = closed
    }

    override fun getID(): Int = recordId

    override fun setID(id: Int) {
        this.recordId = id
    }

    fun getUserId(): String? = data[USER_ID_KEY] as? String

    fun setUserId(id: String?) {
        if (id != null) {
            data[USER_ID_KEY] = id
        }
    }

    fun getExternalId(): String? = data[EXTERNAL_ID_KEY] as? String

    fun setExternalId(id: String?) {
        if (id != null) {
            data[EXTERNAL_ID_KEY] = id
        }
    }

    fun getCategory(): String? {
        var category = data[CATEGORY_KEY] as? String
        if (category == null) {
            category = data[PATIENT_TYPE_KEY] as? String
        }
        return category
    }

    fun setCategory(id: String?) {
        if (id != null) {
            data[CATEGORY_KEY] = id
        }
    }

    fun getState(): String? {
        var state = data[STATE_KEY] as? String
        if (state == null) {
            state = data[CURRENT_STATUS_KEY] as? String
        }
        return state
    }

    fun setState(id: String?) {
        if (id != null) {
            data[STATE_KEY] = id
        }
    }

    fun setCaseId(id: String?) {
        this.id = id
    }

    fun getCaseId(): String? = id

    fun getDateOpened(): PlatformDate? = dateOpened

    fun setDateOpened(dateOpened: PlatformDate?) {
        this.dateOpened = dateOpened
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        typeId = ExtUtil.readString(`in`)
        id = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        name = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        closed = ExtUtil.readBool(`in`)
        dateOpened = ExtUtil.read(`in`, ExtWrapNullable(PlatformDate::class.java), pf) as PlatformDate?
        recordId = ExtUtil.readInt(`in`)
        @Suppress("UNCHECKED_CAST")
        indices = ExtUtil.read(`in`, ExtWrapList(CaseIndex::class.java), pf) as ArrayList<CaseIndex>
        @Suppress("UNCHECKED_CAST")
        data = ExtUtil.read(`in`, ExtWrapMapPoly(String::class.java, true), pf) as HashMap<String, Any>
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeString(out, typeId ?: "")
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(id))
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(name))
        ExtUtil.writeBool(out, closed)
        ExtUtil.write(out, ExtWrapNullable(dateOpened))
        ExtUtil.writeNumeric(out, recordId.toLong())
        ExtUtil.write(out, ExtWrapList(indices))
        ExtUtil.write(out, ExtWrapMapPoly(data))
    }

    fun setProperty(key: String, value: Any?) {
        if (value != null) {
            this.data[key] = value
        }
    }

    fun getProperty(key: String?): Any? {
        if ("case-id" == key) {
            return id
        }
        return data[key]
    }

    fun getPropertyString(key: String): String {
        val o = this.getProperty(key)
        return if (o is String) {
            o
        } else {
            // This is not good, but it's also the uniform matching that's used in the
            // xml transform, essentially.
            PreloadUtils.wrapIndeterminedObject(o)!!.uncast().getString() ?: ""
        }
    }

    fun getProperties(): HashMap<String, Any> = data

    fun getRestorableType(): String = "case"

    override fun getMetaData(fieldName: String): Any {
        if (fieldName == INDEX_CASE_ID) {
            return id ?: ""
        } else if (fieldName == "case-type") {
            return typeId ?: ""
        } else if (fieldName == INDEX_CASE_STATUS) {
            return if (closed) "closed" else "open"
        } else if (fieldName.startsWith(INDEX_CASE_INDEX_PRE)) {
            val indexName = fieldName.substring(fieldName.lastIndexOf('-') + 1, fieldName.length)
            for (index in this.getIndices()) {
                if (index.getName() == indexName) {
                    return index.getTarget() ?: ""
                }
            }
            return ""
        } else if (fieldName == INDEX_OWNER_ID) {
            return getUserId() ?: ""
        } else if (fieldName == INDEX_EXTERNAL_ID) {
            return getExternalId() ?: ""
        } else if (fieldName == INDEX_CATEGORY) {
            return getCategory() ?: ""
        } else if (fieldName == INDEX_STATE) {
            return getState() ?: ""
        } else {
            throw IllegalArgumentException("No metadata field $fieldName in the case storage system")
        }
    }

    override fun getMetaDataFields(): Array<String> {
        return arrayOf(
            INDEX_CASE_ID, INDEX_CASE_TYPE, INDEX_CASE_STATUS,
            INDEX_OWNER_ID, INDEX_EXTERNAL_ID, INDEX_CATEGORY, INDEX_STATE
        )
    }

    /**
     * Deprecated, use setIndex(CaseIndex) in the future.
     */
    fun setIndex(indexName: String?, caseType: String?, indexValue: String?) {
        setIndex(CaseIndex(indexName, caseType, indexValue))
    }

    /**
     * Sets the provided index in this case. If a case index already existed with
     * the same name, it will be replaced.
     *
     * Returns true if an index was replaced, false if an index was not
     */
    fun setIndex(index: CaseIndex): Boolean {
        var indexReplaced = false
        // remove existing indices at this name
        for (i in this.indices) {
            if (i.getName() == index.getName()) {
                this.indices.remove(i)
                indexReplaced = true
                break
            }
        }
        this.indices.add(index)
        return indexReplaced
    }

    fun getIndices(): ArrayList<CaseIndex> = indices

    fun updateAttachment(attachmentName: String, reference: String?) {
        if (reference != null) {
            data[ATTACHMENT_PREFIX + attachmentName] = reference
        }
    }

    fun getAttachmentSource(attachmentName: String): String? {
        return data[ATTACHMENT_PREFIX + attachmentName] as? String
    }

    // this is so terrible it hurts. We'll be redoing this
    fun getAttachments(): ArrayList<String> {
        val attachments = ArrayList<String>()
        val en = data.keys.iterator()
        while (en.hasNext()) {
            val entryName = en.next() as String
            if (entryName.startsWith(ATTACHMENT_PREFIX)) {
                attachments.add(entryName.substring(ATTACHMENT_PREFIX.length))
            }
        }
        return attachments
    }

    fun removeAttachment(attachmentName: String) {
        data.remove(ATTACHMENT_PREFIX + attachmentName)
    }

    // ugh, adding stuff to case models sucks. Need to code up a transition scheme in android so we
    // can stop having shitty models.

    fun setLastModified(lastModified: PlatformDate) {
        data[LAST_MODIFIED] = lastModified
    }

    fun getLastModified(): PlatformDate? {
        if (!data.containsKey(LAST_MODIFIED)) {
            return getDateOpened()
        }
        return data[LAST_MODIFIED] as? PlatformDate
    }

    /**
     * Removes any potential indices with the provided index name.
     *
     * If the index doesn't currently exist, nothing is changed.
     *
     * @param indexName The name of a case index that should be removed.
     */
    fun removeIndex(indexName: String?): Boolean {
        var toRemove: CaseIndex? = null

        for (index in indices) {
            if (index.mName == indexName) {
                toRemove = index
                break
            }
        }

        if (toRemove != null) {
            indices.remove(toRemove)
            return true
        }
        return false
    }

    companion object {
        const val USER_ID_KEY = "userid"
        const val EXTERNAL_ID_KEY = "external_id"
        const val CATEGORY_KEY = "category"
        const val STATE_KEY = "state"

        // Allowed as alias for preferred property category
        const val PATIENT_TYPE_KEY = "patient_type"
        // Allowed as alias for preferred property state
        const val CURRENT_STATUS_KEY = "current_status"

        const val STORAGE_KEY = "CASE"

        const val INDEX_CASE_ID = "case-id"
        const val INDEX_CASE_TYPE = "case-type"
        const val INDEX_CASE_STATUS = "case-status"
        const val INDEX_CASE_INDEX_PRE = "case-in-"
        const val INDEX_OWNER_ID = "owner-id"
        const val INDEX_EXTERNAL_ID = "external-id"
        const val INDEX_CATEGORY = "category"
        const val INDEX_STATE = "state"

        private const val ATTACHMENT_PREFIX = "attachmentdata"
        private const val LAST_MODIFIED = "last_modified"
    }
}
