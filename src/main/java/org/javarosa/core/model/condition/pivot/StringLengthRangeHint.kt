package org.javarosa.core.model.condition.pivot

import org.javarosa.core.model.data.StringData

/**
 * @author ctsims
 */
class StringLengthRangeHint : RangeHint<StringData>() {

    @Throws(UnpivotableExpressionException::class)
    override fun castToValue(value: Double): StringData {
        if (value > 50) {
            throw UnpivotableExpressionException("No calculating string length pivots over 50 characters currently")
        }
        val sb = StringBuilder()
        for (i in 0 until value.toInt()) {
            sb.append("X")
        }
        return StringData(sb.toString())
    }

    override fun unit(): Double {
        return 1.0
    }
}
