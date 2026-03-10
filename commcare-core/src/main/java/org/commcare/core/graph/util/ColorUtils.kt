package org.commcare.core.graph.util

import java.util.Locale

/**
 * These functions are copied directly from android.graphics.Color for use in the core
 * (graphing) classes
 */
object ColorUtils {
    private const val BLACK = 0xFF000000.toInt()
    private const val DKGRAY = 0xFF444444.toInt()
    private const val GRAY = 0xFF888888.toInt()
    private const val LTGRAY = 0xFFCCCCCC.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val RED = 0xFFFF0000.toInt()
    private const val GREEN = 0xFF00FF00.toInt()
    private const val BLUE = 0xFF0000FF.toInt()
    private const val YELLOW = 0xFFFFFF00.toInt()
    private const val CYAN = 0xFF00FFFF.toInt()
    private const val MAGENTA = 0xFFFF00FF.toInt()

    private val sColorNameMap = HashMap<String, Int>().apply {
        put("black", BLACK)
        put("darkgray", DKGRAY)
        put("gray", GRAY)
        put("lightgray", LTGRAY)
        put("white", WHITE)
        put("red", RED)
        put("green", GREEN)
        put("blue", BLUE)
        put("yellow", YELLOW)
        put("cyan", CYAN)
        put("magenta", MAGENTA)
        put("aqua", 0xFF00FFFF.toInt())
        put("fuchsia", 0xFFFF00FF.toInt())
        put("darkgrey", DKGRAY)
        put("grey", GRAY)
        put("lightgrey", LTGRAY)
        put("lime", 0xFF00FF00.toInt())
        put("maroon", 0xFF800000.toInt())
        put("navy", 0xFF000080.toInt())
        put("olive", 0xFF808000.toInt())
        put("purple", 0xFF800080.toInt())
        put("silver", 0xFFC0C0C0.toInt())
        put("teal", 0xFF008080.toInt())
    }

    @JvmStatic
    fun parseColor(colorString: String): Int {
        if (colorString[0] == '#') {
            // Use a long to avoid rollovers on #ffXXXXXX
            var color = java.lang.Long.parseLong(colorString.substring(1), 16)
            if (colorString.length == 7) {
                // Set the alpha value
                color = color or 0x00000000ff000000L
            } else if (colorString.length != 9) {
                throw IllegalArgumentException("Unknown color")
            }
            return color.toInt()
        } else {
            val color = sColorNameMap[colorString.lowercase(Locale.ROOT)]
            if (color != null) {
                return color
            }
        }
        throw IllegalArgumentException("Unknown color")
    }

    @JvmStatic
    fun alpha(color: Int): Int {
        return color ushr 24
    }
}
