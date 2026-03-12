package org.commcare.suite.model

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.util.externalizable.Externalizable

/**
 * Interface for classes that represent query data elements.
 */
interface QueryData : Externalizable {
    fun getKey(): String

    fun getValues(context: EvaluationContext): Iterable<String>
}
