package org.commcare.cases.entity

import org.commcare.modern.util.Pair
import org.commcare.util.EntityProvider
import org.commcare.util.EntitySortUtil
import org.javarosa.core.model.instance.TreeReference
import java.util.Locale
import kotlin.jvm.JvmField

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
                org.javarosa.core.util.PlatformThread.sleep(100)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val currentLocale = Locale.getDefault()
        EntitySortUtil.sortEntities(
            fullEntityList,
            searchTerms!!,
            currentLocale,
            isFuzzySearchEnabled,
            matchScores,
            matchList,
            object : EntityProvider {
                override fun getEntity(index: Int): Entity<TreeReference>? {
                    return fullEntityList[index]
                }
            }
        )
        return matchList
    }
}
