package org.commcare.cases.query.queryset

import org.commcare.cases.query.PredicateProfile
import org.javarosa.core.model.instance.TreeReference

/**
 * A profile of an expression which can be matched to a query set lookup, which can be loaded in
 * bulk.
 *
 * Created by ctsims on 1/31/2017.
 */
class ModelQueryLookup(
    private val key: String,
    val setLookup: QuerySetLookup,
    val rootLookupRef: TreeReference
) : PredicateProfile {

    override fun getKey(): String {
        return key
    }
}
