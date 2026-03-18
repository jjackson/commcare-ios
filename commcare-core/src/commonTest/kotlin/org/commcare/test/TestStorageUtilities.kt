package org.commcare.test

import org.javarosa.core.model.condition.RequestAbandonedException
import org.javarosa.core.services.storage.IMetaData
import org.javarosa.core.services.storage.IStorageIndexedFactory
import org.javarosa.core.services.storage.IStorageIterator
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.externalizable.Externalizable
import kotlin.reflect.KClass

/**
 * Minimal in-memory IStorageUtilityIndexed for cross-platform tests.
 * Only implements methods actually called by ResourceTable and ProfileParser.
 */
class TestInMemoryStorage<T : Externalizable>(
    private val prototypeClass: KClass<*> = Any::class
) : IStorageUtilityIndexed<T> {
    private val data = HashMap<Int, T>()
    private var nextId = 1

    override fun read(id: Int): T = data[id] ?: throw IllegalArgumentException("No record: $id")
    override fun readBytes(id: Int): ByteArray = throw UnsupportedOperationException("test stub")
    override fun write(p: Persistable) {
        val id = if (p.getID() > 0) p.getID() else nextId++
        p.setID(id)
        @Suppress("UNCHECKED_CAST")
        data[id] = p as T
    }
    override fun add(e: T): Int {
        val id = nextId++
        data[id] = e
        return id
    }
    override fun update(id: Int, e: T) { data[id] = e }
    override fun remove(id: Int) { data.remove(id) }
    override fun remove(p: Persistable) { data.remove(p.getID()) }
    override fun removeAll() { data.clear() }
    override fun removeAll(ef: org.javarosa.core.services.storage.EntityFilter<*>): ArrayList<Int> = ArrayList()
    override fun getNumRecords(): Int = data.size
    override fun isEmpty(): Boolean = data.isEmpty()
    override fun exists(id: Int): Boolean = data.containsKey(id)
    override fun iterate(): IStorageIterator<T> = createIterator()
    override fun iterate(includeData: Boolean): IStorageIterator<T> = createIterator()
    override fun close() {}
    override fun getAccessLock(): Any = this
    override fun getIDsForValue(metaFieldName: String, value: Any): ArrayList<Int> {
        val result = ArrayList<Int>()
        for ((id, record) in data) {
            if (record is IMetaData) {
                if (record.getMetaData(metaFieldName) == value) {
                    result.add(id)
                }
            }
        }
        return result
    }
    override fun getIDsForValues(metaFieldNames: Array<String>, values: Array<Any>): List<Int> = emptyList()
    override fun getIDsForValues(metaFieldNames: Array<String>, values: Array<Any>, returnSet: LinkedHashSet<Int>): List<Int> = emptyList()
    override fun getIDsForValues(
        metaFieldNames: Array<String>, values: Array<Any>,
        inverseFieldNames: Array<String>, inverseValues: Array<Any>,
        returnSet: LinkedHashSet<Int>
    ): List<Int> = emptyList()
    override fun getRecordForValue(metaFieldName: String, value: Any): T {
        for ((_, record) in data) {
            if (record is IMetaData) {
                if (record.getMetaData(metaFieldName) == value) {
                    return record
                }
            }
        }
        throw NoSuchElementException("No record with $metaFieldName=$value")
    }
    override fun getRecordsForValues(metaFieldNames: Array<String>, values: Array<Any>): ArrayList<T> = ArrayList()
    @Throws(RequestAbandonedException::class)
    override fun bulkRead(cuedCases: LinkedHashSet<Int>, recordMap: HashMap<Int, T>) {
        for (id in cuedCases) {
            if (!recordMap.containsKey(id) && data.containsKey(id)) {
                recordMap[id] = data[id]!!
            }
        }
    }
    override fun getMetaDataForRecord(recordId: Int, metaFieldNames: Array<String>): Array<String> =
        Array(metaFieldNames.size) { "" }
    override fun bulkReadMetadata(recordIds: LinkedHashSet<Int>, metaFieldNames: Array<String>, metadataMap: HashMap<Int, Array<String>>) {}
    override fun getBulkRecordsForIndex(metaFieldName: String, matchingValues: Collection<String>): ArrayList<T> = ArrayList()
    override fun getBulkIdsForIndex(metaFieldName: String, matchingValues: Collection<String>): ArrayList<Int> = ArrayList()
    override fun getPrototype(): KClass<*> = prototypeClass
    override fun isStorageExists(): Boolean = true
    override fun initStorage() {}
    override fun deleteStorage() { data.clear() }

    private fun createIterator(): IStorageIterator<T> {
        val keys = data.keys.toList().sorted()
        return object : IStorageIterator<T> {
            private var idx = 0
            override fun hasMore(): Boolean = idx < keys.size
            override fun nextID(): Int = keys[idx++]
            override fun nextRecord(): T = data[keys[idx - 1]]!!
            override fun numRecords(): Int = keys.size
            override fun peekID(): Int = keys[idx]
        }
    }
}

class TestStorageFactory : IStorageIndexedFactory {
    override fun newStorage(name: String, type: KClass<*>): IStorageUtilityIndexed<*> {
        return TestInMemoryStorage<Persistable>(type)
    }
}
