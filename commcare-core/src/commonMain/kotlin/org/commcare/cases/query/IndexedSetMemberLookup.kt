package org.commcare.cases.query

import kotlin.jvm.JvmField

/**
 * An indexed set member lookup is a check for whether a value which is indexed on the current
 * platform is a member of a set of elements.
 *
 * IE:
 *
 * "index in ['b' 'c' 'a' 'd']"
 *
 * Created by ctsims on 1/18/2017.
 */
class IndexedSetMemberLookup(
    @JvmField val key: String,
    valueSet: Any
) : PredicateProfile {

    @JvmField
    val valueSet: Array<String> = (valueSet as String).split(" ").toTypedArray()

    override fun getKey(): String = key
}
