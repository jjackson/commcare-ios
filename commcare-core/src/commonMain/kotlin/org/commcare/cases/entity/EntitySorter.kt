package org.commcare.cases.entity

import org.commcare.suite.model.DetailField
import org.javarosa.core.model.Constants
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.services.Logger
import org.javarosa.xpath.XPathTypeMismatchException
import org.javarosa.xpath.expr.FunctionUtils

class EntitySorter(
    private val detailFields: Array<DetailField>,
    private val reverseSort: Boolean,
    private val fieldIndicesToSortBy: IntArray,
    private val notifier: EntitySortNotificationInterface
) : Comparator<Entity<TreeReference>> {

    private var hasWarned = false

    override fun compare(object1: Entity<TreeReference>, object2: Entity<TreeReference>): Int {
        for (fieldIndex in fieldIndicesToSortBy) {
            val reverse = (detailFields[fieldIndex].sortDirection == DetailField.DIRECTION_DESCENDING) xor reverseSort
            val cmp = getCmp(object1, object2, fieldIndex, reverse)
            if (cmp != 0) {
                return cmp
            }
        }
        return 0
    }

    /**
     * Implemented assuming that the sort direction is DIRECTION_ASCENDING, meaning that:
     *
     * -If object1 < object2, this method should return a negative number
     * -If object1 > object2, this method should return a positive number
     *
     * @param reverse - If true, then the rules above are inverted
     */
    private fun getCmp(
        object1: Entity<TreeReference>,
        object2: Entity<TreeReference>,
        index: Int,
        reverse: Boolean
    ): Int {
        var a1 = object1.getSortField(index)
        var a2 = object2.getSortField(index)

        // If one of these is null, we need to get the field in the same index, not the field in SortType
        if (a1 == null) {
            a1 = object1.getFieldString(index)
        }
        if (a2 == null) {
            a2 = object2.getFieldString(index)
        }

        val showBlanksLast = detailFields[index].showBlanksLastInSort()
        // The user's 'blanks' preference is independent of the specified sort order, so don't
        // factor in the 'reverse' parameter here
        if (a1 == "") {
            return if (a2 == "") {
                0
            } else {
                // a1 is blank and a2 is not
                if (showBlanksLast) 1 else -1
            }
        } else if (a2 == "") {
            // a2 is blank and a1 is not
            return if (showBlanksLast) -1 else 1
        }

        val sortType = detailFields[index].sortType
        val c1 = applyType(sortType, a1)
        val c2 = applyType(sortType, a2)

        if (c1 == null || c2 == null) {
            // Don't do something smart here, just bail.
            return -1
        }

        @Suppress("UNCHECKED_CAST")
        return (if (reverse) -1 else 1) * (c1 as Comparable<Any>).compareTo(c2)
    }

    private fun applyType(sortType: Int, value: String): Comparable<*>? {
        try {
            if (sortType == Constants.DATATYPE_TEXT) {
                return value.lowercase()
            } else if (sortType == Constants.DATATYPE_INTEGER) {
                // Double int compares just fine here and also
                // deals with NaN's appropriately
                val ret = FunctionUtils.toInt(value)
                if (ret.isNaN()) {
                    val stringArgs = arrayOfNulls<String>(3)
                    stringArgs[2] = value
                    if (!hasWarned) {
                        notifier.notifyBadFilter(stringArgs)
                        hasWarned = true
                    }
                }
                return ret
            } else if (sortType == Constants.DATATYPE_DECIMAL) {
                val ret = FunctionUtils.toDouble(value)
                if (ret.isNaN()) {
                    val stringArgs = arrayOfNulls<String>(3)
                    stringArgs[2] = value
                    if (!hasWarned) {
                        notifier.notifyBadFilter(stringArgs)
                        hasWarned = true
                    }
                }
                return ret
            } else {
                // Hrmmmm :/ Handle better?
                return value
            }
        } catch (e: XPathTypeMismatchException) {
            Logger.exception("Exception when sorting case list.", e)
            e.printStackTrace()
            return null
        }
    }
}
