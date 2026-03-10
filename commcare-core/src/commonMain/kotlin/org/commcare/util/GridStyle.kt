package org.commcare.util

/**
 * Represents the stylistic attributes of an Entity
 * in a GridEntityView
 *
 * @author wspride
 */
class GridStyle(
    private val fontSize: String?,
    private val horzAlign: String?,
    private val vertAlign: String?,
    private val cssID: String?
) {
    fun getFontSize(): String = fontSize ?: "normal"

    fun getHorzAlign(): String = horzAlign ?: "none"

    fun getVertAlign(): String = vertAlign ?: "none"

    fun getCssID(): String = cssID ?: "none"

    override fun toString(): String {
        return "font size: $fontSize, horzAlign: $horzAlign, vertAlign: $vertAlign"
    }
}
