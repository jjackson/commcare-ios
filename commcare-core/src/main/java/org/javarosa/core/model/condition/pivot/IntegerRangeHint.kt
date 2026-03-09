package org.javarosa.core.model.condition.pivot

import org.javarosa.core.model.data.IntegerData
import kotlin.math.floor

/**
 * @author ctsims
 */
class IntegerRangeHint : RangeHint<IntegerData>() {

    @Throws(UnpivotableExpressionException::class)
    override fun castToValue(value: Double): IntegerData {
        return IntegerData(floor(value).toInt())
    }

    override fun unit(): Double {
        return 1.0
    }
}
