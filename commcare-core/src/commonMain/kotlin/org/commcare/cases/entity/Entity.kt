package org.commcare.cases.entity

import org.commcare.cases.util.StringUtils
import kotlin.jvm.JvmField

/**
 * @author ctsims
 */
open class Entity<T> {

    private val t: T
    private var data: Array<Any?>
    private var sortData: Array<String?>
    private var altTextData: Array<String?>
    private var relevancyData: BooleanArray
    /**
     * Key used to attach external data (i.e. from case list callout) to an entity
     */
    @JvmField
    val extraKey: String?
    private var shouldReceiveFocus: Boolean = false

    private var groupKey: String? = null

    protected constructor(t: T, extraKey: String?) {
        this.t = t
        this.extraKey = extraKey
        this.data = emptyArray()
        this.sortData = emptyArray()
        this.altTextData = emptyArray()
        this.relevancyData = BooleanArray(0)
    }

    constructor(
        data: Array<Any?>,
        sortData: Array<String?>,
        relevancyData: BooleanArray,
        t: T,
        extraKey: String?,
        shouldReceiveFocus: Boolean,
        groupKey: String?,
        altTextData: Array<String?>
    ) {
        this.t = t
        this.sortData = sortData
        this.data = data
        this.relevancyData = relevancyData
        this.extraKey = extraKey
        this.shouldReceiveFocus = shouldReceiveFocus
        this.groupKey = groupKey
        this.altTextData = altTextData
    }

    open fun getField(i: Int): Any? {
        return data[i]
    }

    /*
     * Same as getField, but guaranteed to return a string.
     * If field is not already a string, will return blank string.
     */
    open fun getFieldString(i: Int): String {
        val field = getField(i)
        if (field is String) {
            return field
        }
        return ""
    }

    /**
     * @return True iff the given field is relevant and has a non-blank value.
     */
    open fun isValidField(fieldIndex: Int): Boolean {
        return relevancyData[fieldIndex] && getField(fieldIndex) != ""
    }

    /**
     * Gets the indexed field used for searching and sorting these entities
     *
     * @return either the sort or the string field at the provided index, normalized
     * (IE: lowercase, etc) for searching.
     */
    open fun getNormalizedField(i: Int): String {
        val normalized = this.getFieldString(i)
        return StringUtils.normalize(normalized)
    }

    open fun getSortField(i: Int): String? {
        return sortData[i]
    }

    fun getElement(): T {
        return t
    }

    open fun getNumFields(): Int {
        return data.size
    }

    open fun getData(): Array<Any?> {
        return data
    }

    open fun getAltText(): Array<String?> {
        return altTextData
    }

    open fun getSortFieldPieces(i: Int): Array<String> {
        val sortField = getSortField(i) ?: return emptyArray()
        // We always fuzzy match on the sort field and only if it is available
        // (as a way to restrict possible matching)
        val normalized = StringUtils.normalize(sortField)
        return normalized.split("\\s+".toRegex()).toTypedArray()
    }

    fun shouldReceiveFocus(): Boolean {
        return shouldReceiveFocus
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (i in data.indices) {
            sb.append("\n").append(i).append("\n")
            sb.append("Data: ").append(data[i]).append("|")
            if (sortData[i] != null) {
                sb.append("SortData: ").append(sortData[i]).append("|")
            }
            sb.append("IsValidField: ").append(isValidField(i))
        }
        return sb.toString() + "\n" + super.toString()
    }

    open fun getGroupKey(): String? {
        return groupKey
    }
}
