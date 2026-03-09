package org.commcare.cases.entity

import org.commcare.modern.util.Pair
import org.commcare.util.EntitySortUtil
import org.javarosa.core.model.instance.TreeReference
import java.util.ArrayList
import java.util.Locale

/**
 * Filter entity list via all string-representable entity fields
 */
open class EntityStringFilterer(
    private val searchTerms: Array<String>?,
    private val nodeFactory: NodeEntityFactory,
    @JvmField
    protected val fullEntityList: List<Entity<TreeReference>>,
    private val isFuzzySearchEnabled: Boolean
) {
    private val matchScores: ArrayList<Pair<Int, Int>> = ArrayList()

    @JvmField
    protected val matchList: MutableList<Entity<TreeReference>> = ArrayList()

    init {
        if (searchTerms == null || searchTerms.isEmpty()) {
            matchList.addAll(fullEntityList)
        }
    }

    open fun buildMatchList(): List<Entity<TreeReference>> {
        while (!nodeFactory.isEntitySetReady) {
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        val currentLocale = Locale.getDefault()
        EntitySortUtil.sortEntities(
            fullEntityList,
            searchTerms,
            currentLocale,
            isFuzzySearchEnabled,
            matchScores,
            matchList
        ) { index -> fullEntityList[index] }
        return matchList
    }
}
