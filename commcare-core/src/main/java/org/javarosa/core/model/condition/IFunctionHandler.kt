package org.javarosa.core.model.condition

import org.javarosa.xpath.XPathArityException
import java.util.Vector

interface IFunctionHandler {
    /**
     * @return The name of function being handled
     */
    fun getName(): String

    /**
     * @return Vector of allowed prototypes for this function. Each prototype is
     * an array of Class, corresponding to the types of the expected
     * arguments. The first matching prototype is used.
     */
    fun getPrototypes(): Vector<*>

    /**
     * @return true if this handler should be fed the raw argument list if no
     * prototype matches it
     */
    fun rawArgs(): Boolean

    /**
     * Evaluate the function
     */
    @Throws(XPathArityException::class)
    fun eval(args: Array<Any?>?, ec: EvaluationContext?): Any?
}
