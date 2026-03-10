package org.commcare.util

/**
 * Represents a rectangle in a Detail's EntityViewTile, via the coordinate of the top
 * left corner (gridX and gridY) and the height down (gridHeight) and
 * width right (gridWidth) from there.
 *
 * @author wspride
 */
class GridCoordinate(
    private val gridX: Int,
    private val gridY: Int,
    private val gridWidth: Int,
    private val gridHeight: Int
) {
    fun getX(): Int = gridX

    fun getY(): Int = gridY

    fun getWidth(): Int = gridWidth

    fun getHeight(): Int = gridHeight

    override fun toString(): String {
        return "x: $gridX, y: $gridY, gridWidth: $gridWidth, gridHeight: $gridHeight"
    }
}
