package org.commcare.modern.engine.cases

import org.commcare.cases.query.QueryCache
import org.commcare.modern.util.Pair


/**
 * A record set result cache keeps track of different sets of "Bulk" record which are
 * likely to have data or operations tracked about them (IE: results of a common query which
 * are likely to have further filtering applied.)
 *
 * Since these results are often captured/reported before a context is escalated, this cache
 * doesn't directly hold the resulting cached records themselves. Rather a RecordObjectCache should
 * be used to track the resulting records. This will ensure that cache can be attached to the
 * appropriate lifecycle
 *
 * Created by ctsims on 1/25/2017.
 */
class RecordSetResultCache : QueryCache {

    private val bulkFetchBodies = HashMap<String, Pair<String, LinkedHashSet<Int>>>()

    /**
     * Report a set of bulk records that are likely to be needed as a group.
     *
     * @param key A unique key for the provided record set. It is presumed that if the key is
     *            already in use that the id set is redundant.
     * @param storageSetID The name of the Storage where the records are stored.
     * @param ids The record set ID's
     */
    fun reportBulkRecordSet(key: String, storageSetID: String, ids: LinkedHashSet<Int>) {
        val fullKey = "$key|$storageSetID"
        if (bulkFetchBodies.containsKey(fullKey)) {
            return
        }
        bulkFetchBodies[fullKey] = Pair(storageSetID, ids)
    }

    fun hasMatchingRecordSet(recordSetName: String, recordId: Int): Boolean {
        return getRecordSetForRecordId(recordSetName, recordId) != null
    }

    /**
     * Retrieves a record set result which contains the provided record ID and record set.
     *
     * If no record sets contain the provided record, returns null.
     *
     * If multiple record set results contain the provided record, this method will return the
     * result of the smallest size.
     */
    fun getRecordSetForRecordId(recordSetName: String,
                                recordId: Int): Pair<String, LinkedHashSet<Int>>? {
        var match: Pair<String, LinkedHashSet<Int>>? = null
        for (key in bulkFetchBodies.keys) {
            val tranche = bulkFetchBodies[key]!!
            if (tranche.second.contains(recordId) && tranche.first == recordSetName) {
                if (match == null || tranche.second.size < match.second.size) {
                    match = Pair(key, tranche.second)
                }
            }
        }
        return match
    }
}
