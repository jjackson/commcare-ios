package org.javarosa.core.model

import org.javarosa.core.model.condition.Condition
import org.javarosa.core.model.condition.IConditionExpr
import org.javarosa.core.model.condition.Recalculate
import org.javarosa.model.xform.XPathReference
import kotlin.jvm.JvmField

/**
 * A data binding is an object that represents how a
 * data element is to be used in a form entry interaction.
 *
 * It contains a reference to where the data should be retreived
 * and stored, as well as the preload parameters, and the
 * conditional logic for the question.
 *
 * The class relies on any Data References that are used
 * in a form to be registered with the FormDefRMSUtility's
 * prototype factory in order to properly deserialize.
 *
 * @author Drew Roos
 */
class DataBinding {
    var id: String? = null
    var reference: XPathReference? = null
    var dataType: Int = 0

    @JvmField
    var relevancyCondition: Condition? = null
    @JvmField
    var relevantAbsolute: Boolean = true
    @JvmField
    var requiredCondition: Condition? = null
    @JvmField
    var requiredAbsolute: Boolean = false
    @JvmField
    var readonlyCondition: Condition? = null
    @JvmField
    var readonlyAbsolute: Boolean = false
    @JvmField
    var constraint: IConditionExpr? = null
    @JvmField
    var calculate: Recalculate? = null

    var preload: String? = null
    var preloadParams: String? = null
    @JvmField
    var constraintMessage: String? = null
}
