package org.javarosa.core.model.condition.pivot

/**
 * @author ctsims
 */
class UnpivotableExpressionException : Exception {

    /**
     * Default constructor. Should be used for semantically unpivotable
     * expressions which are expected
     */
    constructor()

    /**
     * Message constructor. Should be used when something unusual happens.
     */
    constructor(message: String?) : super(message)
}
