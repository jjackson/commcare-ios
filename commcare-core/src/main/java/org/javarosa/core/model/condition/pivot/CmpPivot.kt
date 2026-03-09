package org.javarosa.core.model.condition.pivot

/**
 * @author ctsims
 */
class CmpPivot(
    private val value: Double,
    private val op: Int
) : Pivot {

    var outcome: Boolean = false

    fun getVal(): Double {
        return value
    }

    fun getOp(): Int {
        return op
    }
}
