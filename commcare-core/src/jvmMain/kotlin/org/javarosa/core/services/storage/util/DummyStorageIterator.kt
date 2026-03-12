package org.javarosa.core.services.storage.util

import org.javarosa.core.services.storage.IStorageIterator
import org.javarosa.core.services.storage.Persistable

/**
 * @author ctsims
 */
class DummyStorageIterator<T : Persistable>(
    private val dummyStorage: DummyIndexedStorageUtility<T>,
    data: HashMap<Int, T>
) : IStorageIterator<T> {

    private var count: Int = 0
    private val keys: Array<Int>

    init {
        keys = Array(data.size) { 0 }
        var i = 0
        val en = data.keys.iterator()
        while (en.hasNext()) {
            keys[i] = en.next()
            ++i
        }
    }

    override fun hasMore(): Boolean {
        return count < keys.size
    }

    override fun nextID(): Int {
        count++
        return keys[count - 1]
    }

    override fun nextRecord(): T {
        return dummyStorage.read(nextID())
    }

    override fun numRecords(): Int {
        return dummyStorage.getNumRecords()
    }

    override fun peekID(): Int {
        return keys[count]
    }
}
