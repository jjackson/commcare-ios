package org.javarosa.core.log

import org.javarosa.core.util.SortedIntSet
import org.javarosa.core.util.externalizable.PlatformIOException

abstract class StreamLogSerializer {

    private val logIDs: SortedIntSet = SortedIntSet()
    private var purger: Purger? = null

    interface Purger {
        fun purge(IDs: SortedIntSet)
    }

    @Throws(PlatformIOException::class)
    fun serializeLog(id: Int, entry: LogEntry) {
        logIDs.add(id)
        serializeLog(entry)
    }

    @Throws(PlatformIOException::class)
    protected abstract fun serializeLog(entry: LogEntry)

    fun setPurger(purger: Purger?) {
        this.purger = purger
    }

    fun purge() {
        purger?.purge(logIDs)
    }
}
