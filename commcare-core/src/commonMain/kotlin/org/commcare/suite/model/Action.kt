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
    private var display: DisplayUnit? = null
    private var stackOps: ArrayList<StackOperation>? = null
    private var relevantExpr: XPathExpression? = null
    private var iconReferenceForActionBar: String? = null
    private var autoLaunchExpr: XPathExpression? = null
    private var redoLast: Boolean = false

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
        this.stackOps = stackOps
        this.relevantExpr = relevantExpr
        this.iconReferenceForActionBar = if (iconForActionBar == null) "" else iconForActionBar
        this.autoLaunchExpr = autoLaunchExpr
        this.redoLast = redoLast
    }

    /**
     * @return The Display model for showing this action to the user
     */
    fun getDisplay(): DisplayUnit? = display

    /**
     * @return A vector of the StackOperation models which
     * should be processed sequentially upon this action
     * being triggered by the user.
     */
    fun getStackOperations(): ArrayList<StackOperation>? = stackOps

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

    fun getAutoLaunchExpr(): XPathExpression? = autoLaunchExpr

    fun isRedoLast(): Boolean = redoLast

    fun hasActionBarIcon(): Boolean = "" != iconReferenceForActionBar

    fun getActionBarIconReference(): String? = iconReferenceForActionBar

    @Suppress("UNCHECKED_CAST")
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        display = SerializationHelpers.readExternalizable(`in`, pf) { DisplayUnit() }
        stackOps = SerializationHelpers.readList(`in`, pf) { StackOperation() }
        autoLaunchExpr = SerializationHelpers.readNullableTagged(`in`, pf) as XPathExpression?
        relevantExpr = SerializationHelpers.readNullableTagged(`in`, pf) as XPathExpression?
        iconReferenceForActionBar = SerializationHelpers.readString(`in`)
        redoLast = SerializationHelpers.readBool(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.write(out, display as Any)
        SerializationHelpers.writeList(out, stackOps!!)
        SerializationHelpers.writeNullableTagged(out, autoLaunchExpr)
        SerializationHelpers.writeNullableTagged(out, relevantExpr)
        SerializationHelpers.writeString(out, iconReferenceForActionBar ?: "")
        SerializationHelpers.writeBool(out, redoLast)
    }
}
