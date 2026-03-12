package org.commcare.suite.model

import org.javarosa.core.model.condition.EvaluationContext

/**
 * Interface for any class that can be used as a template in a detail field.
 *
 * @author jschweers
 */
interface DetailTemplate {
    /**
     * Generate the actual data for this template, given a specific EvaluationContext.
     */
    fun evaluate(context: EvaluationContext?): Any?
}
