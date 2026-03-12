package org.javarosa.core.model.actions

import org.javarosa.core.model.FormDef
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.SerializationHelpers
import kotlin.jvm.JvmStatic

/**
 * @author ctsims
 */
abstract class Action : Externalizable {

    private var name: String? = null

    constructor()

    constructor(name: String?) {
        this.name = name
    }

    /**
     * Process actions that were triggered in the form.
     *
     * NOTE: Currently actions are only processed on nodes that are
     * WITHIN the context provided, if one is provided. This will
     * need to get changed possibly for future action types.
     *
     * @return TreeReference targeted by the action or null if the action
     * wasn't completed.
     */
    abstract fun processAction(model: FormDef, context: TreeReference?): TreeReference?

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        name = SerializationHelpers.readString(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeString(out, name!!)
    }

    companion object {
        // Events that can trigger an action
        const val EVENT_XFORMS_READY: String = "xforms-ready"
        const val EVENT_XFORMS_REVALIDATE: String = "xforms-revalidate"
        const val EVENT_JR_INSERT: String = "jr-insert"
        const val EVENT_QUESTION_VALUE_CHANGED: String = "xforms-value-changed"

        private val allEvents = arrayOf(
            EVENT_JR_INSERT,
            EVENT_QUESTION_VALUE_CHANGED,
            EVENT_XFORMS_READY,
            EVENT_XFORMS_REVALIDATE
        )

        @JvmStatic
        fun isValidEvent(actionEventAttribute: String?): Boolean {
            for (event in allEvents) {
                if (event == actionEventAttribute) {
                    return true
                }
            }
            return false
        }
    }
}
