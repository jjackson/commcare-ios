package org.commcare.cases.query

import kotlin.jvm.JvmField

/**
 * An indexed value lookup is a singular key/value comparison where the key being checked is
 * indexed by the current platform
 *
 * IE:
 *
 * index = 'a'
 *
 * Created by ctsims on 1/18/2017.
 */
class IndexedValueLookup(
    @JvmField val key: String,
    @JvmField val value: Any
) : PredicateProfile {

    override fun getKey(): String = key
}
