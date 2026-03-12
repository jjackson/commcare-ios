package org.commcare.cases.query.queryset

import org.javarosa.core.model.instance.TreeReference

/**
 * The "current" query set is a set of tree references which are in play that are the result of a
 * query to the current() context within a predicate expression.
 *
 * Created by ctsims on 2/6/2017.
 */
class CurrentModelQuerySet(
    val currentQuerySet: Collection<TreeReference>
) : ModelQuerySet {

    companion object {
        const val CURRENT_QUERY_SET_ID = "current"
    }

    // the below is a hack and is bad.

    // This shouldn't actually be a model query set as outlined currently, but it's gonna take
    // a bit to pull it into its own caching model
    override fun getMatchingValues(i: Int): Collection<Int>? {
        return null
    }

    override fun getSetBody(): Set<Int>? {
        return null
    }
}
