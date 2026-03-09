package org.javarosa.core.model.actions

import org.javarosa.core.model.FormDef
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapListPoly
import org.javarosa.core.util.externalizable.ExtWrapMap
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Hashtable
import java.util.Vector

/**
 * Registers actions that should be triggered by certain events, and handles the triggering
 * of those actions when an event occurs.
 *
 * @author Aliza Stone
 */
class ActionController : Externalizable {

    // map from an event to the actions it should trigger
    private var eventListeners: Hashtable<String, Vector<Action>> = Hashtable()

    private fun getListenersForEvent(event: String): Vector<Action> {
        return if (eventListeners.containsKey(event)) {
            eventListeners[event]!!
        } else {
            Vector()
        }
    }

    fun registerEventListener(event: String, action: Action) {
        val actions: Vector<Action> = if (eventListeners.containsKey(event)) {
            eventListeners[event]!!
        } else {
            val newActions = Vector<Action>()
            eventListeners[event] = newActions
            newActions
        }
        actions.addElement(action)
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

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(inStream: DataInputStream, pf: PrototypeFactory) {
        @Suppress("UNCHECKED_CAST")
        eventListeners = ExtUtil.read(
            inStream,
            ExtWrapMap(String::class.java, ExtWrapListPoly()), pf
        ) as Hashtable<String, Vector<Action>>
    }

    @Throws(IOException::class)
    override fun writeExternal(outStream: DataOutputStream) {
        ExtUtil.write(outStream, ExtWrapMap(eventListeners, ExtWrapListPoly()))
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
