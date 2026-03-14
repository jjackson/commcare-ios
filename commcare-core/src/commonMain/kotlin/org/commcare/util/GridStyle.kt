package org.commcare.util

/**
 * Represents the stylistic attributes of an Entity
 * in a GridEntityView
 *
 * @author wspride
 */
class GridStyle(
    private val _fontSize: String?,
    private val _horzAlign: String?,
    private val _vertAlign: String?,
    private val _cssID: String?
) {
    val fontSize: String get() = _fontSize ?: "normal"

    val horzAlign: String get() = _horzAlign ?: "none"

    val vertAlign: String get() = _vertAlign ?: "none"

    val cssID: String get() = _cssID ?: "none"

    override fun toString(): String {
        return "font size: $_fontSize, horzAlign: $_horzAlign, vertAlign: $_vertAlign"
    }
}
