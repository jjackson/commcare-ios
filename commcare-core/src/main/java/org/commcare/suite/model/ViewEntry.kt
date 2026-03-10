package org.commcare.suite.model

import org.javarosa.core.model.instance.DataInstance

import java.util.Hashtable
import java.util.Vector

/**
 * An entry action used for viewing data; main difference from an Entry is that
 * it doesn't end in launching form entry.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
class ViewEntry : Entry {
    /**
     * Serialization only!
     */
    constructor()

    constructor(
        commandId: String?, display: DisplayUnit?,
        data: Vector<SessionDatum>?,
        instances: Hashtable<String, DataInstance<*>>?,
        stackOperations: Vector<StackOperation>?,
        assertions: AssertionSet?
    ) : super(commandId, display, data, instances, stackOperations, assertions)

    override fun isView(): Boolean = true
}
