package org.commcare.cases.query

import kotlin.jvm.JvmField

/**
 * A negative indexed value lookup is a singular key/value comparison where the key being checked is
 * indexed by the current platform
 *
 * IE:
 *
 * index != 'a'
 *
 * Created by cellowitz on 11/23/2020.
 */
class NegativeIndexedValueLookup(
    @JvmField val key: String,
    @JvmField val value: Any
) : PredicateProfile {

    override fun getKey(): String = key
}
