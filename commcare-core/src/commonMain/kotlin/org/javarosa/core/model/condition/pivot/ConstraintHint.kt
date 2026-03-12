package org.javarosa.core.model.condition.pivot

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.IConditionExpr
import org.javarosa.core.model.instance.FormInstance

/**
 * @author ctsims
 */
interface ConstraintHint {
    @Throws(UnpivotableExpressionException::class)
    fun init(c: EvaluationContext?, conditional: IConditionExpr?, instance: FormInstance?)
}
