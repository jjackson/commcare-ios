package org.commcare.core.ios.storage

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
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import kotlin.reflect.KClass

/**
 * In-memory storage implementation for iOS.
 * Mirrors DummyIndexedStorageUtility from JVM but without Class<T> dependencies.
 * Suitable for development and testing; will be replaced with SQLite for production.
 */
class IosInMemoryStorage<T : Persistable>(
    private val prototypeClass: KClass<*>,
    private val instanceFactory: () -> T,
    private val pFactory: PrototypeFactory
) : IStorageUtilityIndexed<T> {

    private var meta: HashMap<String, HashMap<Any, ArrayList<Int>>>? = HashMap()
    private val data: HashMap<Int, T> = HashMap()
    private var curCount: Int = 0

    init {
        initMetaFromInstance(instanceFactory())
    }

    constructor(instance: T, factory: PrototypeFactory) : this(
        instance::class,
        { throw UnsupportedOperationException("Cannot create new instances from instance-based constructor") },
        factory
    ) {
        // Re-init meta from the provided instance
        initMetaFromProvidedInstance(instance)
    }

    private fun initMetaFromProvidedInstance(p: Persistable) {
        meta = HashMap()
        if (p !is IMetaData) return
        for (key in p.getMetaDataFields()) {
            if (!meta!!.containsKey(key)) {
                meta!![key] = HashMap()
            }
        }
    }

    private fun initMetaFromInstance(p: Persistable) {
        if (p !is IMetaData) return
        for (key in p.getMetaDataFields()) {
            if (!meta!!.containsKey(key)) {
                meta!![key] = HashMap()
            }
        }
    }

    override fun read(id: Int): T {
        try {
            val t = instanceFactory()
            t.readExternal(PlatformDataInputStream(readBytes(id)), pFactory)
            t.setID(id)
            return t
        } catch (e: PlatformIOException) {
            e.printStackTrace()
            throw RuntimeException(e.message)
        } catch (e: DeserializationException) {
            e.printStackTrace()
            throw RuntimeException(e.message)
        }
    }

    override fun readBytes(id: Int): ByteArray {
        try {
            val item = data[DataUtil.integer(id)]
            if (item != null) {
                val dos = PlatformDataOutputStream()
                item.writeExternal(dos)
                return dos.toByteArray()
            } else {
                throw NoSuchElementException("No record for ID $id")
            }
        } catch (e: PlatformIOException) {
            throw RuntimeException("Couldn't serialize data to return to readBytes")
        }
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

    override fun iterate(): IStorageIterator<T> = IosStorageIterator(this, data)

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
            (data[recordId] as IMetaData).getMetaData(fieldNames[i]) as String
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
