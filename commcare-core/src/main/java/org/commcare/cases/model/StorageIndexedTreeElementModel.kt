package org.commcare.cases.model
import org.javarosa.core.util.externalizable.JvmExtUtil

import org.commcare.cases.instance.FixtureIndexSchema
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.services.storage.IMetaData
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.PrototypeFactory

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * A DB model for storing TreeElements such that particular attributes and
 * elements are indexed and queryable using the DB.
 *
 * Indexed attributes/elements get their own table columns, and the rest of
 * the TreeElement is stored as a serialized blob.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
open class StorageIndexedTreeElementModel : Persistable, IMetaData {

    private var metaDataFields: Array<String>? = null
    private var indices: ArrayList<String>? = null
    private var root: TreeElement? = null

    @JvmField
    protected var recordId: Int = -1

    @JvmField
    protected var entityId: String? = null

    @Suppress("unused")
    constructor() {
        // for serialization
    }

    constructor(indices: Set<String>, root: TreeElement?) {
        this.indices = ArrayList(indices)
        this.root = root
        metaDataFields = buildMetadataFields(this.indices!!)
    }

    /**
     * @return The list of elements from this model which are indexed, this list will be in the input
     * format, which generally can be interpreted as a treereference step into the model which
     * will reference the metadata field in the virtual instance, IE: "@attributename"
     */
    fun getIndexedTreeReferenceSteps(): ArrayList<String>? = indices

    override fun getMetaDataFields(): Array<String> = metaDataFields ?: emptyArray()

    override fun getMetaData(fieldName: String): Any {
        val currentRoot = root
        if (fieldName.startsWith(ATTR_COL_PREFIX)) {
            return currentRoot?.getAttributeValue(
                null,
                getElementOrAttributeFromSqlColumnName(fieldName).substring(ATTR_PREFIX_LENGTH)
            ) ?: ""
        } else if (fieldName.startsWith(ELEM_COL_PREFIX)) {
            // NOTE PLM: The usage of getChild of '0' below assumes indexes
            // are only made over entries with multiplicity 0
            val child = currentRoot?.getChild(getElementOrAttributeFromSqlColumnName(fieldName), 0)
            if (child == null) {
                return ""
            }
            val value = child.getValue()
            return value?.uncast()?.getString() ?: ""
        }
        throw IllegalArgumentException("No metadata field $fieldName in the indexed fixture storage table.")
    }

    override fun setID(id: Int) {
        this.recordId = id
    }

    override fun getID(): Int = recordId

    fun getEntityId(): String? = entityId

    fun getRoot(): TreeElement? = root

    fun getIndexColumnNames(): Set<String> {
        val indexColumnNames = HashSet<String>()
        val currentIndices = this.indices
        if (currentIndices != null) {
            for (index in currentIndices) {
                indexColumnNames.add(FixtureIndexSchema.escapeIndex(index))
            }
        }
        return indexColumnNames
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        recordId = ExtUtil.readInt(`in`)
        entityId = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        root = JvmExtUtil.read(`in`, TreeElement::class.java, pf) as TreeElement
        @Suppress("UNCHECKED_CAST")
        indices = ExtUtil.read(`in`, ExtWrapList(String::class.java), pf) as ArrayList<String>
        metaDataFields = buildMetadataFields(indices!!)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeNumeric(out, recordId.toLong())
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(entityId))
        ExtUtil.write(out, root!!)
        ExtUtil.write(out, ExtWrapList(indices!!))
    }

    companion object {
        private const val STORAGE_KEY_PREFIX = "IND_FIX_"
        private val DASH_ESCAPE = "${'$'}${'$'}"
        private const val ATTR_PREFIX_LENGTH = 1  // "@".length
        private val ATTR_COL_PREFIX = "_${'$'}"
        private const val ELEM_COL_PREFIX = "_0"

        @JvmField
        var sqlColumnToElementCache: HashMap<String, String> = HashMap()

        @JvmField
        var elementToSqlColumn: HashMap<String, String> = HashMap()

        private fun buildMetadataFields(indices: List<String>): Array<String> {
            val escapedIndexList = Array(indices.size) { "" }
            var i = 0
            for (index in indices) {
                escapedIndexList[i++] = getSqlColumnNameFromElementOrAttribute(index)
            }
            return escapedIndexList
        }

        @JvmStatic
        fun getTableName(fixtureName: String): String {
            val cleanedName = fixtureName.replace(":", "_").replace(".", "_").replace("-", "_")
            return STORAGE_KEY_PREFIX + cleanedName
        }

        /**
         * Turns a column name into the corresponding attribute or element for the TreeElement
         */
        @JvmStatic
        fun getElementOrAttributeFromSqlColumnName(col: String): String {
            if (sqlColumnToElementCache.containsKey(col)) {
                return sqlColumnToElementCache[col]!!
            }
            val input = col

            var result = col.replace(DASH_ESCAPE, "-")
            if (result.startsWith(ATTR_COL_PREFIX)) {
                result = "@" + result.substring(ATTR_COL_PREFIX.length)
            } else if (result.startsWith(ELEM_COL_PREFIX)) {
                result = result.substring(ELEM_COL_PREFIX.length)
            } else {
                throw RuntimeException("Unable to process index of '$result' metadata entry")
            }

            sqlColumnToElementCache[input] = result
            return result
        }

        /**
         * Turns an attribute or element from the TreeElement into a valid SQL column name
         */
        @JvmStatic
        fun getSqlColumnNameFromElementOrAttribute(entry: String): String {
            if (elementToSqlColumn.containsKey(entry)) {
                return elementToSqlColumn[entry]!!
            }

            val input = entry

            var result = entry.replace("-", DASH_ESCAPE)
            if (result.startsWith("@")) {
                result = ATTR_COL_PREFIX + result.substring(ATTR_PREFIX_LENGTH)
            } else {
                result = ELEM_COL_PREFIX + result
            }
            elementToSqlColumn[input] = result
            return result
        }
    }
}
