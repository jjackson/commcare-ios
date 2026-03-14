package org.commcare.util

/**
 * Represents a rectangle in a Detail's EntityViewTile, via the coordinate of the top
 * left corner (gridX and gridY) and the height down (gridHeight) and
 * width right (gridWidth) from there.
 *
 * @author wspride
 */
class GridCoordinate(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    override fun toString(): String {
        return "x: $x, y: $y, gridWidth: $width, gridHeight: $height"
    }
}
