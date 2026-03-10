package org.commcare.modern.database

import com.carrotsearch.hppc.IntCollection
import org.commcare.modern.models.MetaField
import org.commcare.modern.util.Pair
import org.javarosa.core.services.storage.IMetaData
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.externalizable.Externalizable

import java.io.ByteArrayOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import java.util.HashMap
import java.util.HashSet

/**
 * Functions for generating CommCare SQL statements based on classes
 *
 * Largely taken from renamed AndroidTableBuilder and moved into api to be used
 * externally.
 *
 * @author ctsims
 * @author wspride
 */
open class TableBuilder {

    private val name: String

    private val cols: ArrayList<String>
    private val rawCols: ArrayList<String>
    val unique: HashSet<String> = HashSet()

    constructor(c: Class<*>, name: String) {
        this.name = name
        cols = ArrayList()
        rawCols = ArrayList()
        this.addData(c)
    }

    constructor(name: String) {
        this.name = name
        cols = ArrayList()
        rawCols = ArrayList()
    }

    constructor(c: Class<*>) : this(c, (c.getAnnotation(Table::class.java) as Table).value)

    fun addData(c: Class<*>) {
        cols.add(DatabaseHelper.ID_COL + " INTEGER PRIMARY KEY")
        rawCols.add(DatabaseHelper.ID_COL)

        for (f in c.declaredFields) {
            if (f.isAnnotationPresent(MetaField::class.java)) {
                val mf = f.getAnnotation(MetaField::class.java)
                addMetaField(mf)
            }
        }

        for (m in c.declaredMethods) {
            if (m.isAnnotationPresent(MetaField::class.java)) {
                val mf = m.getAnnotation(MetaField::class.java)
                addMetaField(mf)
            }
        }

        cols.add(DatabaseHelper.DATA_COL + " BLOB")
        rawCols.add(DatabaseHelper.DATA_COL)
    }

    protected open fun addMetaField(mf: MetaField) {
        val key = mf.value
        val columnName = scrubName(key)
        rawCols.add(columnName)
        var columnDef: String
        columnDef = columnName

        //Modifiers
        if (unique.contains(columnName) || mf.unique) {
            columnDef += " UNIQUE"
        }
        cols.add(columnDef)
    }

    fun addData(p: Persistable) {
        addPersistableIdAndMeta(p)

        cols.add(DatabaseHelper.DATA_COL + " BLOB")
        rawCols.add(DatabaseHelper.DATA_COL)
    }

    private fun addPersistableIdAndMeta(p: Persistable) {
        cols.add(DatabaseHelper.ID_COL + " INTEGER PRIMARY KEY")
        rawCols.add(DatabaseHelper.ID_COL)

        if (p is IMetaData) {
            val keys = (p as IMetaData).getMetaDataFields()
            if (keys != null) {
                for (key: String in keys) {
                    val columnName = scrubName(key)
                    if (!rawCols.contains(columnName)) {
                        rawCols.add(columnName)
                        var columnDef = columnName

                        //Modifiers
                        if (unique.contains(columnName)) {
                            columnDef += " UNIQUE"
                        }
                        cols.add(columnDef)
                    }
                }
            }
        }
    }

    /**
     * Build a table to store provided persistable in the filesystem.  Creates
     * filepath and encrypting key columns, along with normal metadata columns
     * from the persistable
     */
    fun addFileBackedData(p: Persistable) {
        addData(p)

        cols.add(DatabaseHelper.AES_COL + " BLOB")
        rawCols.add(DatabaseHelper.AES_COL)

        cols.add(DatabaseHelper.FILE_COL)
        rawCols.add(DatabaseHelper.FILE_COL)
    }

    fun setUnique(columnName: String) {
        unique.add(scrubName(columnName))
    }

    fun getTableCreateString(): String {
        var built = "CREATE TABLE IF NOT EXISTS ${scrubName(name)} ("
        for (i in cols.indices) {
            built += cols[i]
            if (i < cols.size - 1) {
                built += ", "
            }
        }
        built += ");"
        return built
    }

    fun getTableInsertData(p: Persistable): Pair<String, List<Any?>> {
        val stringBuilder = StringBuilder()

        stringBuilder.append("INSERT INTO ").append(scrubName(name)).append(" (")
        val contentValues = DatabaseHelper.getMetaFieldsAndValues(p)

        val params = ArrayList<Any?>()

        for (i in rawCols.indices) {
            stringBuilder.append(rawCols[i])
            if (i < rawCols.size - 1) {
                stringBuilder.append(", ")
            }
        }

        stringBuilder.append(") VALUES (")

        for (i in rawCols.indices) {
            val currentValue = contentValues[rawCols[i]]
            stringBuilder.append("?")
            params.add(currentValue)
            if (i < rawCols.size - 1) {
                stringBuilder.append(", ")
            }
        }

        stringBuilder.append(");")

        return Pair(stringBuilder.toString(), params)
    }

    companion object {
        private const val MAX_SQL_ARGS = 950

        @JvmStatic
        fun scrubName(input: String): String {
            return input.replace("-", "_").replace(".", "_")
        }

        @JvmStatic
        fun toBlob(externalizable: Externalizable): ByteArray {
            val bos = ByteArrayOutputStream()
            try {
                externalizable.writeExternal(PlatformDataOutputStream(bos))
            } catch (e: PlatformIOException) {
                throw RuntimeException("Failed to serialize externalizable $externalizable for content values wth exception $e")
            }
            return bos.toByteArray()
        }

        /**
         * Given a list of integer params to insert and a maximum number of args, return the
         * String containing (?, ?,...) to be used in the SQL query and the array of args
         * to replace them with
         */
        @JvmStatic
        fun sqlList(input: Collection<*>): List<Pair<String, Array<String>>> {
            return sqlList(input, "?")
        }

        @JvmStatic
        fun sqlList(input: Collection<*>, questionMarkType: String): List<Pair<String, Array<String>>> {
            return sqlList(input, MAX_SQL_ARGS, questionMarkType)
        }

        private fun sqlList(input: Collection<*>, maxArgs: Int, questionMark: String): List<Pair<String, Array<String>>> {
            val ops = ArrayList<Pair<String, Array<String>>>()

            //figure out how many iterations we'll need
            val numIterations = Math.ceil(input.size.toDouble() / maxArgs).toInt()

            val iterator = input.iterator()

            for (currentRound in 0 until numIterations) {
                val startPoint = currentRound * maxArgs
                val lastIndex = Math.min((currentRound + 1) * maxArgs, input.size)
                val stringBuilder = StringBuilder("(")
                for (i in startPoint until lastIndex) {
                    stringBuilder.append(questionMark)
                    stringBuilder.append(",")
                }

                val array = Array(lastIndex - startPoint) { "" }
                var count = 0
                for (i in startPoint until lastIndex) {
                    array[count++] = iterator.next().toString()
                }

                ops.add(Pair(stringBuilder.toString().substring(0,
                    stringBuilder.toString().length - 1) + ")", array))
            }
            return ops
        }

        @JvmStatic
        fun sqlList(input: IntCollection): List<Pair<String, Array<String>>> {
            return sqlList(input, MAX_SQL_ARGS)
        }

        /**
         * Given a list of integer params to insert and a maximum number of args, return the
         * String containing (?, ?,...) to be used in the SQL query and the array of args
         * to replace them with
         */
        private fun sqlList(input: IntCollection, maxArgs: Int): List<Pair<String, Array<String>>> {
            val ops = ArrayList<Pair<String, Array<String>>>()

            //figure out how many iterations we'll need
            val numIterations = Math.ceil(input.size().toDouble() / maxArgs).toInt()

            val iterator = input.iterator()

            for (currentRound in 0 until numIterations) {
                val startPoint = currentRound * maxArgs
                val lastIndex = Math.min((currentRound + 1) * maxArgs, input.size())
                val stringBuilder = StringBuilder("(")
                for (i in startPoint until lastIndex) {
                    stringBuilder.append("?,")
                }

                val array = Array(lastIndex - startPoint) { "" }
                var count = 0
                for (i in startPoint until lastIndex) {
                    array[count++] = iterator.next().value.toString()
                }

                ops.add(Pair(stringBuilder.toString().substring(0,
                    stringBuilder.toString().length - 1) + ")", array))
            }
            return ops
        }
    }
}
