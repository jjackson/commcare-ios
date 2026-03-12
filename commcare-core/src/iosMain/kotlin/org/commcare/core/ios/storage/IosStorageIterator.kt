package org.commcare.core.ios.storage

import org.javarosa.core.services.storage.IStorageIterator
import org.javarosa.core.services.storage.Persistable

class IosStorageIterator<T : Persistable>(
    private val storage: IosInMemoryStorage<T>,
    data: HashMap<Int, T>
) : IStorageIterator<T> {

    private var count: Int = 0
    private val keys: Array<Int>

    init {
        keys = Array(data.size) { 0 }
        var i = 0
        for (key in data.keys) {
            keys[i] = key
            i++
        }
    }

    override fun hasMore(): Boolean = count < keys.size

    override fun nextID(): Int {
        count++
        return keys[count - 1]
    }

    override fun nextRecord(): T = storage.read(nextID())

    override fun numRecords(): Int = storage.getNumRecords()

    override fun peekID(): Int = keys[count]
}
