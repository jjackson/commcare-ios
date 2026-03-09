package org.javarosa.core.services.storage

import java.util.Hashtable

abstract class EntityFilter<E> {

    companion object {
        const val PREFILTER_EXCLUDE: Int = -1
        const val PREFILTER_INCLUDE: Int = 1
        const val PREFILTER_FILTER: Int = 0
    }

    /**
     * filter based just on ID and metadata (metadata not supported yet!! will always be 'null', currently)
     *
     * @return if PREFILTER_INCLUDE, record will be included, matches() not called
     * if PREFILTER_EXCLUDE, record will be excluded, matches() not called
     * if PREFILTER_FILTER, matches() will be called and record will be included or excluded based on return value
     */
    open fun preFilter(id: Int, metaData: Hashtable<String, Any>?): Int {
        return PREFILTER_FILTER
    }

    abstract fun matches(e: E): Boolean
}
