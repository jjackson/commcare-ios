package org.commcare.modern.database

import org.commcare.modern.models.EncryptedModel
import org.commcare.modern.util.Pair
import org.javarosa.core.services.storage.IMetaData
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.externalizable.Externalizable

import org.javarosa.core.model.utils.PlatformDate
import java.util.HashMap
import java.util.HashSet

/**
 * Setup of platform-agnostic DB helper functions IE for generating SQL
 * statements, args, content values, etc.
 *
 * @author wspride
 */
object DatabaseHelper {

    @JvmField
    val ID_COL = "commcare_sql_id"
    @JvmField
    val DATA_COL = "commcare_sql_record"
    @JvmField
    val FILE_COL = "commcare_sql_file"
    @JvmField
    val AES_COL = "commcare_sql_aes"

    @JvmStatic
    fun createWhere(fieldNames: Array<String>, values: Array<Any>): Pair<String?, Array<String>?> {
        return createWhere(fieldNames, values, null)
    }

    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun createWhere(fieldNames: Array<String>, values: Array<Any>,
                    p: Persistable?): Pair<String?, Array<String>?> {
        return createWhere(fieldNames, values, null, p)
    }

    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun createWhere(fieldNames: Array<String>, values: Array<Any>,
                    em: EncryptedModel?, p: Persistable?): Pair<String?, Array<String>?> {
        return createWhere(fieldNames, values, emptyArray(), emptyArray(), em, p)
    }

    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun createWhere(fieldNames: Array<String>, values: Array<Any>,
                    inverseFieldNames: Array<String>, inverseValues: Array<Any>,
                    p: Persistable?): Pair<String?, Array<String>?> {
        return createWhere(fieldNames, values, inverseFieldNames, inverseValues, null, p)
    }

    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun createWhere(fieldNames: Array<String>, values: Array<Any>,
                    inverseFieldNames: Array<String>, inverseValues: Array<Any>,
                    em: EncryptedModel?, p: Persistable?): Pair<String?, Array<String>?> {
        // null selection
        if (fieldNames.isEmpty() && inverseFieldNames.isEmpty()) {
            return Pair(null, null)
        }

        var fields: HashSet<String>? = null
        if (p is IMetaData) {
            val thefields = (p as IMetaData).getMetaDataFields()
            fields = HashSet()
            for (s in thefields) {
                fields.add(TableBuilder.scrubName(s))
            }
        }

        if (em is IMetaData) {
            val thefields = (em as IMetaData).getMetaDataFields()
            fields = HashSet()
            for (s in thefields) {
                fields.add(TableBuilder.scrubName(s))
            }
        }

        val stringBuilder = StringBuilder()
        val arguments = ArrayList<String>()
        var set = false
        for (i in fieldNames.indices) {
            val columnName = TableBuilder.scrubName(fieldNames[i])
            if (fields != null) {
                if (!fields.contains(columnName)) {
                    continue
                }
            }

            if (set) {
                stringBuilder.append(" AND ")
            }

            stringBuilder.append(columnName)
            stringBuilder.append("=?")

            arguments.add(values[i].toString())

            set = true
        }
        for (i in inverseFieldNames.indices) {
            val columnName = TableBuilder.scrubName(inverseFieldNames[i])
            if (fields != null) {
                if (!fields.contains(columnName)) {
                    continue
                }
            }
            if (set) {
                stringBuilder.append(" AND ")
            }

            stringBuilder.append(columnName)
            stringBuilder.append("!=?")

            arguments.add(inverseValues[i].toString())

            set = true
        }
        // we couldn't match any of the fields to our columns
        if (!set) {
            throw IllegalArgumentException("Unable to match provided fields with columns.")
        }

        val retArray = arguments.toTypedArray()

        return Pair(stringBuilder.toString(), retArray)
    }

    @JvmStatic
    fun getMetaFieldsAndValues(e: Externalizable): HashMap<String, Any> {
        val values = getNonDataMetaEntries(e)
        addDataToValues(values, e)
        return values
    }

    private fun addDataToValues(values: HashMap<String, Any>,
                                e: Externalizable) {
        val blob = TableBuilder.toBlob(e)
        values[DATA_COL] = blob
    }

    @JvmStatic
    fun getNonDataMetaEntries(e: Externalizable): HashMap<String, Any> {
        val values = HashMap<String, Any>()

        if (e is IMetaData) {
            for (key in (e as IMetaData).getMetaDataFields()) {
                val o = e.getMetaData(key) ?: continue
                val scrubbedKey = TableBuilder.scrubName(key)
                if (o is PlatformDate) {
                    // store date as seconds since epoch
                    values[scrubbedKey] = o.time
                } else {
                    values[scrubbedKey] = o.toString()
                }
            }
        }
        return values
    }

    @JvmStatic
    fun getTableCreateString(storageKey: String, p: Persistable): String {
        val tableBuilder = TableBuilder(storageKey)
        tableBuilder.addData(p)
        return tableBuilder.getTableCreateString()
    }

    @JvmStatic
    fun getTableInsertData(storageKey: String,
                           p: Persistable): Pair<String, List<Any?>> {
        val tableBuilder = TableBuilder(storageKey)
        tableBuilder.addData(p)
        return tableBuilder.getTableInsertData(p)
    }
}
