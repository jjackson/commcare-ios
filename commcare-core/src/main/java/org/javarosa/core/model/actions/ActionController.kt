package org.javarosa.core.model.actions

import org.javarosa.core.model.FormDef
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Registers actions that should be triggered by certain events, and handles the triggering
 * of those actions when an event occurs.
 *
 * @author Aliza Stone
 */
class ActionController : Externalizable {

    // map from an event to the actions it should trigger
    private var eventListeners: HashMap<String, ArrayList<Action>> = HashMap()

    private fun getListenersForEvent(event: String): ArrayList<Action> {
        return if (eventListeners.containsKey(event)) {
            eventListeners[event]!!
        } else {
            ArrayList()
        }
    }

    fun registerEventListener(event: String, action: Action) {
        val actions: ArrayList<Action> = if (eventListeners.containsKey(event)) {
            eventListeners[event]!!
        } else {
            val newActions = ArrayList<Action>()
            eventListeners[event] = newActions
            newActions
        }
        actions.add(action)
    }

    fun triggerActionsFromEvent(event: String, model: FormDef) {
        triggerActionsFromEvent(event, model, null, null)
    }

    fun triggerActionsFromEvent(
        event: String,
        model: FormDef,
        contextForAction: TreeReference?,
        resultProcessor: ActionResultProcessor?
    ) {
        for (action in getListenersForEvent(event)) {
            val refSetByAction = action.processAction(model, contextForAction)
            if (resultProcessor != null && refSetByAction != null) {
                resultProcessor.processResultOfAction(refSetByAction, event)
            }
        }
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(inStream: PlatformDataInputStream, pf: PrototypeFactory) {
        @Suppress("UNCHECKED_CAST")
        eventListeners = SerializationHelpers.readStringListPolyMap(inStream, pf) as HashMap<String, ArrayList<Action>>
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(outStream: PlatformDataOutputStream) {
        SerializationHelpers.writeStringListPolyMap(outStream, eventListeners as HashMap<*, *>)
    }

    // Allows defining of a custom callback to execute on a result of processAction()
    interface ActionResultProcessor {
        /**
         * @param targetRef - the ref that this action targeted
         * @param event - the event that triggered this action
         */
        fun processResultOfAction(targetRef: TreeReference, event: String)
    }
}
