package org.commcare.suite.model

/**
 * Piece of required session data that is acquired via an xpath computation
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
class ComputedDatum : SessionDatum {
    /**
     * Used in serialization
     */
    constructor()

    /**
     * @param id    Name used to access the computed data in the session instance
     * @param value XPath expression whose evaluation result is used as the data
     */
    constructor(id: String?, value: String?) : super(id, value)
}
