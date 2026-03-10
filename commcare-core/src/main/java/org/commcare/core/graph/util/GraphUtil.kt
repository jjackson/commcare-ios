package org.commcare.core.graph.util

/**
 * Constants used by graphing
 *
 * @author jschweers
 */
object GraphUtil {
    const val TYPE_XY = "xy"
    const val TYPE_BAR = "bar"
    const val TYPE_BUBBLE = "bubble"
    const val TYPE_TIME = "time"

    private var charLimit: Int = -1

    @JvmStatic
    fun getLabelCharacterLimit(): Int = charLimit

    @JvmStatic
    fun setLabelCharacterLimit(limit: Int) {
        charLimit = limit
    }
}
