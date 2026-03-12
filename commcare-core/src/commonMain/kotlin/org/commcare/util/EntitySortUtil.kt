package org.commcare.util

import org.commcare.cases.entity.Entity
import org.commcare.cases.util.StringUtils
import org.commcare.modern.util.Pair
import org.javarosa.core.model.instance.TreeReference

import kotlin.jvm.JvmStatic

object EntitySortUtil {

    @JvmStatic
    fun sortEntities(
        fullEntityList: List<Entity<TreeReference>>,
        searchTerms: Array<String>,
        isFuzzySearchEnabled: Boolean,
        matchScores: ArrayList<Pair<Int, Int>>,
        matchList: MutableList<Entity<TreeReference>>,
        entityProvider: EntityProvider
    ) {
        for (index in fullEntityList.indices) {
            // Every once and a while we should make sure we're not blocking anything with the database
            val e = entityProvider.getEntity(index) ?: return

            var add = false
            var score = 0
            for (filter in searchTerms) {
                add = false
                var normalizedFilter = StringUtils.normalize(filter)
                for (i in 0 until e.getNumFields()) {
                    val field = e.getNormalizedField(i)
                    if ("" != field && field.lowercase().contains(normalizedFilter)) {
                        add = true
                        break
                    } else if (isFuzzySearchEnabled) {
                        // We possibly now want to test for edit distance for fuzzy matching
                        for (fieldChunk in e.getSortFieldPieces(i)) {
                            val match = StringUtils.fuzzyMatch(normalizedFilter, fieldChunk)
                            if (match.first) {
                                add = true
                                score += match.second
                                break
                            }
                        }
                        if (add) break
                    }
                }
                if (!add) {
                    break
                }
            }
            if (add) {
                matchScores.add(Pair.create(index, score))
            }
        }
        // If fuzzy search is enabled need to re-sort based on edit distance
        if (isFuzzySearchEnabled) {
            matchScores.sortWith { lhs, rhs -> lhs.second - rhs.second }
        }

        for (match in matchScores) {
            matchList.add(fullEntityList[match.first])
        }
    }
}
