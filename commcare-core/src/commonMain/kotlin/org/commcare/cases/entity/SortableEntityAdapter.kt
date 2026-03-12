package org.commcare.cases.entity

import org.commcare.suite.model.Detail
import org.javarosa.core.model.instance.TreeReference

/**
 * Created by amstone326 on 5/24/17.
 */
abstract class SortableEntityAdapter(
    private val entities: MutableList<Entity<TreeReference>>,
    private val detail: Detail,
    sortByDefault: Boolean
) : EntitySortNotificationInterface {

    var currentSort: IntArray = intArrayOf()
        private set
    var isCurrentSortReversed: Boolean = false
        private set

    init {
        val orderedFieldsForSorting = determineFieldsForSortingInOrder()
        if (sortByDefault && orderedFieldsForSorting.isNotEmpty()) {
            sort(orderedFieldsForSorting)
        }
    }

    private fun determineFieldsForSortingInOrder(): IntArray {
        var fieldsForSorting = detail.orderedFieldIndicesForSorting
        if (fieldsForSorting.isEmpty()) {
            for (i in detail.fields.indices) {
                val header = detail.fields[i].header?.evaluate()
                if ("" != header) {
                    fieldsForSorting = intArrayOf(i)
                    break
                }
            }
        }
        return fieldsForSorting
    }

    protected fun sort(fields: IntArray) {
        // The reversing here is only relevant if there's only one sort field and we're on it
        sort(fields, (currentSort.size == 1 && currentSort[0] == fields[0]) && !isCurrentSortReversed)
    }

    private fun sort(fields: IntArray, reverse: Boolean) {
        this.isCurrentSortReversed = reverse
        currentSort = fields

        this.entities.sortWith(
            EntitySorter(detail.fields, isCurrentSortReversed, currentSort, this)
        )
    }
}
