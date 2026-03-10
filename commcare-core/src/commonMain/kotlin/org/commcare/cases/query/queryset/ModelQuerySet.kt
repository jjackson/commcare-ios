package org.commcare.cases.query.queryset

/**
 * A ModelQuerySet is a very basic data type which stores the result of a query set lookup. It
 * maintains one-to-many mapping of model id's (IE: Cases in the case db) which are the result
 * of a potentially complex query, and allow individual values to be returned, as well as providing
 * a way to get all matching values to perform bulk operations.
 *
 * Created by ctsims on 1/25/2017.
 */
interface ModelQuerySet {
    fun getMatchingValues(i: Int): Collection<Int>?
    fun getSetBody(): Set<Int>?
}
