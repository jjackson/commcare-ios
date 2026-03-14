package org.commcare.suite.model

import org.commcare.session.RemoteQuerySessionManager
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.xpath.expr.XPathExpression

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * An action defines a user interface element that can be
 * triggered by the user to fire off one or more stack operations
 * in the current session
 *
 * @author ctsims
 */
class Action : Externalizable {
    var display: DisplayUnit? = null
        private set
    var stackOperations: ArrayList<StackOperation>? = null
        private set
    private var relevantExpr: XPathExpression? = null
    var actionBarIconReference: String? = null
        private set
    var autoLaunchExpr: XPathExpression? = null
        private set
    var isRedoLast: Boolean = false
        private set

    /**
     * Serialization only!!!
     */
    constructor()

    /**
     * Creates an Action model with the associated display details and stack
     * operations set.
     */
    constructor(
        display: DisplayUnit?,
        stackOps: ArrayList<StackOperation>?,
        relevantExpr: XPathExpression?,
        iconForActionBar: String?,
        autoLaunchExpr: XPathExpression?,
        redoLast: Boolean
    ) {
        this.display = display
        this.stackOperations = stackOps
        this.relevantExpr = relevantExpr
        this.actionBarIconReference = if (iconForActionBar == null) "" else iconForActionBar
        this.autoLaunchExpr = autoLaunchExpr
        this.isRedoLast = redoLast
    }

    fun isRelevant(evalContext: EvaluationContext): Boolean {
        val re = relevantExpr ?: return true
        val result = RemoteQuerySessionManager.evalXpathExpression(re, evalContext)
        return "true" == result
    }

    fun isAutoLaunchAction(evalContext: EvaluationContext): Boolean {
        val ale = autoLaunchExpr ?: return false
        val result = RemoteQuerySessionManager.evalXpathExpression(ale, evalContext)
        return "true" == result
    }

    fun hasActionBarIcon(): Boolean = "" != actionBarIconReference

    @Suppress("UNCHECKED_CAST")
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        display = SerializationHelpers.readExternalizable(`in`, pf) { DisplayUnit() }
        stackOperations = SerializationHelpers.readList(`in`, pf) { StackOperation() }
        autoLaunchExpr = SerializationHelpers.readNullableTagged(`in`, pf) as XPathExpression?
        relevantExpr = SerializationHelpers.readNullableTagged(`in`, pf) as XPathExpression?
        actionBarIconReference = SerializationHelpers.readString(`in`)
        isRedoLast = SerializationHelpers.readBool(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.write(out, display as Any)
        SerializationHelpers.writeList(out, stackOperations!!)
        SerializationHelpers.writeNullableTagged(out, autoLaunchExpr)
        SerializationHelpers.writeNullableTagged(out, relevantExpr)
        SerializationHelpers.writeString(out, actionBarIconReference ?: "")
        SerializationHelpers.writeBool(out, isRedoLast)
    }
}
