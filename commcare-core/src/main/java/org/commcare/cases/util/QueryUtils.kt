package org.commcare.cases.util

import org.commcare.cases.query.IndexedValueLookup
import org.commcare.cases.query.PredicateProfile
import org.commcare.cases.query.QueryContext
import org.commcare.cases.query.QuerySensitive

/**
 * Created by ctsims on 1/25/2017.
 */
object QueryUtils {

    @JvmStatic
    fun getFirstKeyIndexedValue(profiles: ArrayList<PredicateProfile>): IndexedValueLookup? {
        if (profiles[0] is IndexedValueLookup) {
            return profiles[0] as IndexedValueLookup
        }
        return null
    }

    @JvmStatic
    fun wrapSingleResult(result: Int?): ArrayList<Int> {
        val results = ArrayList<Int>()
        if (result != null) {
            results.add(result)
        }
        return results
    }

    /**
     * If the provided object has the QuerySensitive instance tag, provide the object with the
     * current query context so it can potentially prepare itself for use in a more efficient
     * manner
     */
    @JvmStatic
    fun prepareSensitiveObjectForUseInCurrentContext(o: Any?, context: QueryContext) {
        if (o is QuerySensitive) {
            o.prepareForUseInCurrentContext(context)
        }
    }
}
