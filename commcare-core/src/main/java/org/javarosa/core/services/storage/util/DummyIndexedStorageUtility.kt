package org.javarosa.core.services.storage.util

import org.javarosa.core.model.condition.RequestAbandonedException
import org.javarosa.core.services.storage.EntityFilter
import org.javarosa.core.services.storage.IMetaData
import org.javarosa.core.services.storage.IStorageIterator
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.DataUtil
import org.javarosa.core.util.InvalidIndexException
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.io.createByteArrayInputStream
import org.javarosa.core.io.createByteArrayOutputStream
import org.javarosa.core.io.byteArrayOutputStreamToBytes
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * @author ctsims
 */
class DummyIndexedStorageUtility<T : Persistable> : IStorageUtilityIndexed<T> {

    private var meta: HashMap<String, HashMap<Any, ArrayList<Int>>>? = HashMap()
    private val data: HashMap<Int, T> = HashMap()
    private var curCount: Int = 0
    private val prototype: Class<T>
    private val mFactory: PrototypeFactory

    constructor(prototype: Class<T>, factory: PrototypeFactory) {
        this.prototype = prototype
        this.mFactory = factory
        initMetaFromClass()
    }

    constructor(instance: T, factory: PrototypeFactory) {
        @Suppress("UNCHECKED_CAST")
        this.prototype = instance.javaClass as Class<T>
        this.mFactory = factory
        initMetaFromInstance(instance)
    }

    private fun initMetaFromClass() {
        val p: Persistable
        try {
            p = prototype.newInstance()
        } catch (e: InstantiationException) {
            throw RuntimeException("Couldn't create a serializable class for storage!" + prototype.name)
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Couldn't create a serializable class for storage!" + prototype.name)
        }

        initMetaFromInstance(p)
    }

    private fun initMetaFromInstance(p: Persistable) {
        if (p !is IMetaData) {
            return
        }
        val m = p as IMetaData
        for (key in m.getMetaDataFields()) {
            if (!meta!!.containsKey(key)) {
                meta!![key] = HashMap<Any, ArrayList<Int>>()
            }
        }
    }

    private fun getIDsForInverseValue(fieldName: String, value: Any): ArrayList<Int> {
        if (meta!![fieldName] == null) {
            throw IllegalArgumentException("Unsupported index: $fieldName for storage of ${prototype.name}")
        }
        val allValues = meta!![fieldName]!!
        val ids = ArrayList<Int>()
        val en = allValues.keys.iterator()
        while (en.hasNext()) {
            val key = en.next()

            if (key != value && allValues[key] != null) {
                for (id in allValues[key]!!) {
                    ids.add(id)
                }
            }
        }
        return ids
    }

    override fun getIDsForValue(fieldName: String, value: Any): ArrayList<Int> {
        //We don't support all index types
        if (meta!![fieldName] == null) {
            throw IllegalArgumentException("Unsupported index: $fieldName for storage of ${prototype.name}")
        }
        if (meta!![fieldName]!![value] == null) {
            return ArrayList()
        }
        return meta!![fieldName]!![value]!!
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
            if (accumulator == null) {
                accumulator = ArrayList(matches)
            } else {
                accumulator = DataUtil.intersection(accumulator, matches) as MutableList<Int>
            }
        }
        for (i in inverseMatchFields.indices) {
            val matches = getIDsForInverseValue(inverseMatchFields[i], inverseMatchValues[i])
            if (accumulator == null) {
                accumulator = ArrayList(matches)
            } else {
                accumulator = DataUtil.intersection(accumulator, matches) as MutableList<Int>
            }
        }

        returnSet.addAll(accumulator!!)
        return accumulator
    }

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
        val idMatches = getIDsForValues(metaFieldNames, values)
        for (id in idMatches) {
            matches.add(read(id))
        }
        return matches
    }

    override fun add(e: T): Int {
        data[DataUtil.integer(curCount)] = e
        addMeta(curCount)

        // This is not a legit pair of operations;
        curCount++

        return curCount - 1
    }

    override fun close() {
    }

    override fun exists(id: Int): Boolean {
        return data.containsKey(DataUtil.integer(id))
    }

    override fun getAccessLock(): Any? {
        return null
    }

    override fun getNumRecords(): Int {
        return data.size
    }

    override fun isEmpty(): Boolean {
        return data.size > 0
    }

    override fun iterate(): IStorageIterator<T> {
        //We should really find a way to invalidate old iterators first here
        return DummyStorageIterator(this, data)
    }

    override fun iterate(includeData: Boolean): IStorageIterator<T> {
        return iterate()
    }

    override fun read(id: Int): T {
        try {
            val t = prototype.newInstance()
            t.readExternal(PlatformDataInputStream(createByteArrayInputStream(readBytes(id))), mFactory)
            t.setID(id)
            return t
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
            throw RuntimeException(e.message)
        } catch (e: InstantiationException) {
            e.printStackTrace()
            throw RuntimeException(e.message)
        } catch (e: PlatformIOException) {
            e.printStackTrace()
            throw RuntimeException(e.message)
        } catch (e: DeserializationException) {
            e.printStackTrace()
            throw RuntimeException(e.message)
        }
    }

    override fun readBytes(id: Int): ByteArray {
        val stream = createByteArrayOutputStream()
        try {
            val item = data[DataUtil.integer(id)]
            if (item != null) {
                item.writeExternal(PlatformDataOutputStream(stream))
                return byteArrayOutputStreamToBytes(stream)
            } else {
                throw NoSuchElementException("No record for ID $id")
            }
        } catch (e: PlatformIOException) {
            throw RuntimeException("Couldn't serialize data to return to readBytes")
        }
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

    override fun update(id: Int, e: T) {
        data[DataUtil.integer(id)] = e
        addMeta(DataUtil.integer(id))
    }

    override fun write(p: Persistable) {
        if (p.getID() != -1) {
            @Suppress("UNCHECKED_CAST")
            this.data[DataUtil.integer(p.getID())] = p as T
            addMeta(DataUtil.integer(p.getID()))
        } else {
            p.setID(curCount)
            @Suppress("UNCHECKED_CAST")
            this.add(p as T)
        }
    }

    private fun syncMeta() {
        for (metaEntry in meta!!.values) {
            metaEntry.clear()
        }

        val en = data.keys.iterator()
        while (en.hasNext()) {
            val i = en.next()
            addMeta(i)
        }
    }

    private fun addMeta(i: Int) {
        val e: Externalizable = data[i] ?: return

        if (e is IMetaData) {
            val m = e as IMetaData
            val keys = meta!!.keys.iterator()
            while (keys.hasNext()) {
                val key = keys.next()

                val value = m.getMetaData(key) ?: continue

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

    @Throws(RequestAbandonedException::class)
    override fun bulkRead(cuedCases: LinkedHashSet<Int>, recordMap: HashMap<Int, T>) {
        for (i in cuedCases) {
            recordMap[i] = data[i]!!
        }
    }

    override fun getMetaDataForRecord(recordId: Int, fieldNames: Array<String>): Array<String> {
        return Array(fieldNames.size) { i ->
            (data[recordId] as IMetaData).getMetaData(fieldNames[i]) as String
        }
    }

    override fun getBulkRecordsForIndex(metaFieldName: String, matchingValues: Collection<String>): ArrayList<T> {
        // we don't care about bulk retrieval for dummy storage, so just call normal method to get records here
        @Suppress("UNCHECKED_CAST")
        return getRecordsForValues(arrayOf(metaFieldName), matchingValues.toTypedArray() as Array<Any>)
    }

    override fun getBulkIdsForIndex(metaFieldName: String, matchingValues: Collection<String>): ArrayList<Int> {
        // we don't care about bulk retrieval for dummy storage, so just call normal method to get records here
        @Suppress("UNCHECKED_CAST")
        val result = getIDsForValues(arrayOf(metaFieldName), matchingValues.toTypedArray() as Array<Any>)
        return ArrayList(result)
    }

    override fun bulkReadMetadata(cuedCases: LinkedHashSet<Int>, metaDataIds: Array<String>, metadataMap: HashMap<Int, Array<String>>) {
        for (i in cuedCases) {
            metadataMap[i] = getMetaDataForRecord(i, metaDataIds)
        }
    }

    override fun getPrototype(): Class<*> {
        return prototype
    }

    override fun isStorageExists(): Boolean {
        return meta != null
    }

    override fun initStorage() {
        meta = HashMap()
    }

    override fun deleteStorage() {
        meta = null
    }
}
