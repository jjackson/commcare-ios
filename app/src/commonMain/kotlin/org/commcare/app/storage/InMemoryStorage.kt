package org.commcare.app.storage

import org.javarosa.core.model.condition.RequestAbandonedException
import org.javarosa.core.services.storage.EntityFilter
import org.javarosa.core.services.storage.IMetaData
import org.javarosa.core.services.storage.IStorageIterator
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.DataUtil
import org.javarosa.core.util.InvalidIndexException
import org.javarosa.core.util.externalizable.Externalizable
import kotlin.reflect.KClass

/**
 * Cross-platform in-memory storage implementing IStorageUtilityIndexed.
 * Stores objects in a HashMap with IMetaData-based indexing.
 */
class InMemoryStorage<T : Persistable>(
    private val prototypeClass: KClass<*>,
    private val instanceFactory: () -> T
) : IStorageUtilityIndexed<T> {

    private var meta: HashMap<String, HashMap<Any, ArrayList<Int>>>? = HashMap()
    private val data: HashMap<Int, T> = HashMap()
    private var curCount: Int = 0

    init {
        try {
            initMeta(instanceFactory())
        } catch (_: Exception) {
            // Factory may throw if it's an instance-based constructor
        }
    }

    private fun initMeta(p: Persistable) {
        if (p !is IMetaData) return
        for (key in p.getMetaDataFields()) {
            if (!meta!!.containsKey(key)) {
                meta!![key] = HashMap()
            }
        }
    }

    override fun read(id: Int): T {
        val item = data[DataUtil.integer(id)]
            ?: throw NoSuchElementException("No record for ID $id")
        return item
    }

    override fun readBytes(id: Int): ByteArray {
        throw UnsupportedOperationException("readBytes not supported in InMemoryStorage")
    }

    override fun write(p: Persistable) {
        if (p.getID() != -1) {
            @Suppress("UNCHECKED_CAST")
            data[DataUtil.integer(p.getID())] = p as T
            addMeta(DataUtil.integer(p.getID()))
        } else {
            p.setID(curCount)
            @Suppress("UNCHECKED_CAST")
            add(p as T)
        }
    }

    override fun add(e: T): Int {
        data[DataUtil.integer(curCount)] = e
        (e as Persistable).setID(curCount)
        addMeta(curCount)
        curCount++
        return curCount - 1
    }

    override fun update(id: Int, e: T) {
        data[DataUtil.integer(id)] = e
        addMeta(DataUtil.integer(id))
    }

    override fun remove(id: Int) {
        data.remove(DataUtil.integer(id))
        syncMeta()
    }

    override fun remove(p: Persistable) {
        remove(p.getID())
    }

    override fun removeAll() {
        data.clear()
        syncMeta()
    }

    override fun removeAll(ef: EntityFilter<*>): ArrayList<Int> {
        val removed = ArrayList<Int>()
        val en = data.keys.iterator()
        while (en.hasNext()) {
            val i = en.next()
            when (ef.preFilter(i, null)) {
                EntityFilter.PREFILTER_INCLUDE -> {
                    removed.add(i)
                    continue
                }
                EntityFilter.PREFILTER_EXCLUDE -> continue
            }
            @Suppress("UNCHECKED_CAST")
            if ((ef as EntityFilter<Any>).matches(data[i]!!)) {
                removed.add(i)
            }
        }
        for (i in removed) {
            data.remove(i)
        }
        syncMeta()
        return removed
    }

    override fun getNumRecords(): Int = data.size

    override fun isEmpty(): Boolean = data.isEmpty()

    override fun exists(id: Int): Boolean = data.containsKey(DataUtil.integer(id))

    override fun iterate(): IStorageIterator<T> = InMemoryStorageIterator(this, data)

    override fun iterate(includeData: Boolean): IStorageIterator<T> = iterate()

    override fun close() {}

    override fun getAccessLock(): Any? = null

    override fun getIDsForValue(fieldName: String, value: Any): ArrayList<Int> {
        if (meta!![fieldName] == null) {
            throw IllegalArgumentException("Unsupported index: $fieldName for storage of ${prototypeClass.simpleName}")
        }
        return meta!![fieldName]!![value] ?: ArrayList()
    }

    override fun getIDsForValues(fieldNames: Array<String>, values: Array<Any>): List<Int> {
        return getIDsForValues(fieldNames, values, LinkedHashSet())
    }

    override fun getIDsForValues(fieldNames: Array<String>, values: Array<Any>, returnSet: LinkedHashSet<Int>): List<Int> {
        return getIDsForValues(fieldNames, values, arrayOf(), arrayOf(), returnSet)
    }

    override fun getIDsForValues(
        fieldNames: Array<String>,
        values: Array<Any>,
        inverseMatchFields: Array<String>,
        inverseMatchValues: Array<Any>,
        returnSet: LinkedHashSet<Int>
    ): List<Int> {
        if (fieldNames.isEmpty() && inverseMatchFields.isEmpty()) {
            val matches = ArrayList(data.keys)
            returnSet.addAll(data.keys)
            return matches
        }

        var accumulator: MutableList<Int>? = null
        for (i in fieldNames.indices) {
            val matches = getIDsForValue(fieldNames[i], values[i])
            accumulator = if (accumulator == null) {
                ArrayList(matches)
            } else {
                DataUtil.intersection(accumulator, matches) as MutableList<Int>
            }
        }
        for (i in inverseMatchFields.indices) {
            val matches = getIDsForInverseValue(inverseMatchFields[i], inverseMatchValues[i])
            accumulator = if (accumulator == null) {
                ArrayList(matches)
            } else {
                DataUtil.intersection(accumulator, matches) as MutableList<Int>
            }
        }

        returnSet.addAll(accumulator!!)
        return accumulator
    }

    private fun getIDsForInverseValue(fieldName: String, value: Any): ArrayList<Int> {
        if (meta!![fieldName] == null) {
            throw IllegalArgumentException("Unsupported index: $fieldName for storage of ${prototypeClass.simpleName}")
        }
        val allValues = meta!![fieldName]!!
        val ids = ArrayList<Int>()
        for ((key, idList) in allValues) {
            if (key != value) {
                ids.addAll(idList)
            }
        }
        return ids
    }

    @Throws(NoSuchElementException::class, InvalidIndexException::class)
    override fun getRecordForValue(fieldName: String, value: Any): T {
        if (meta!![fieldName] == null) {
            throw NoSuchElementException("No record matching meta index $fieldName with value $value")
        }
        val matches = meta!![fieldName]!![value]
        if (matches == null || matches.size == 0) {
            throw NoSuchElementException("No record matching meta index $fieldName with value $value")
        }
        if (matches.size > 1) {
            throw InvalidIndexException("Multiple records matching meta index $fieldName with value $value", fieldName)
        }
        return read(matches[0])
    }

    override fun getRecordsForValues(metaFieldNames: Array<String>, values: Array<Any>): ArrayList<T> {
        val matches = ArrayList<T>()
        for (id in getIDsForValues(metaFieldNames, values)) {
            matches.add(read(id))
        }
        return matches
    }

    @Throws(RequestAbandonedException::class)
    override fun bulkRead(cuedCases: LinkedHashSet<Int>, recordMap: HashMap<Int, T>) {
        for (i in cuedCases) {
            recordMap[i] = data[i]!!
        }
    }

    override fun getMetaDataForRecord(recordId: Int, fieldNames: Array<String>): Array<String> {
        return Array(fieldNames.size) { i ->
            (data[recordId] as IMetaData).getMetaData(fieldNames[i]).toString()
        }
    }

    override fun bulkReadMetadata(recordIds: LinkedHashSet<Int>, metaFieldNames: Array<String>, metadataMap: HashMap<Int, Array<String>>) {
        for (i in recordIds) {
            metadataMap[i] = getMetaDataForRecord(i, metaFieldNames)
        }
    }

    override fun getBulkRecordsForIndex(metaFieldName: String, matchingValues: Collection<String>): ArrayList<T> {
        @Suppress("UNCHECKED_CAST")
        return getRecordsForValues(arrayOf(metaFieldName), matchingValues.toTypedArray() as Array<Any>)
    }

    override fun getBulkIdsForIndex(metaFieldName: String, matchingValues: Collection<String>): ArrayList<Int> {
        @Suppress("UNCHECKED_CAST")
        val result = getIDsForValues(arrayOf(metaFieldName), matchingValues.toTypedArray() as Array<Any>)
        return ArrayList(result)
    }

    override fun getPrototype(): KClass<*> = prototypeClass

    override fun isStorageExists(): Boolean = meta != null

    override fun initStorage() {
        meta = HashMap()
    }

    override fun deleteStorage() {
        meta = null
    }

    private fun syncMeta() {
        if (meta == null) return
        for (metaEntry in meta!!.values) {
            metaEntry.clear()
        }
        for (i in data.keys) {
            addMeta(i)
        }
    }

    private fun addMeta(i: Int) {
        val e: Externalizable = data[i] ?: return
        if (e is IMetaData) {
            for (key in meta!!.keys) {
                val value = e.getMetaData(key) ?: continue
                val records = meta!![key]!!
                if (!records.containsKey(value)) {
                    records[value] = ArrayList()
                }
                val indices = records[value]!!
                if (!indices.contains(i)) {
                    indices.add(i)
                }
            }
        }
    }
}

/**
 * Iterator over InMemoryStorage entries.
 */
class InMemoryStorageIterator<T : Persistable>(
    private val storage: InMemoryStorage<T>,
    data: HashMap<Int, T>
) : IStorageIterator<T> {
    private val keys = data.keys.toList().sorted()
    private var count = 0

    override fun hasMore(): Boolean = count < keys.size

    override fun nextID(): Int {
        val id = keys[count]
        count++
        return id
    }

    override fun peekID(): Int {
        return keys[count]
    }

    override fun nextRecord(): T = storage.read(nextID())

    override fun numRecords(): Int = keys.size
}
