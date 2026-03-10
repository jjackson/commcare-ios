package org.commcare.suite.model

import org.commcare.session.RemoteQuerySessionManager
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream

import java.io.DataInputStream
import java.io.DataOutputStream
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
        display = ExtUtil.read(`in`, DisplayUnit::class.java, pf) as DisplayUnit
        stackOps = ExtUtil.read(`in`, ExtWrapList(StackOperation::class.java), pf) as ArrayList<StackOperation>
        autoLaunchExpr = ExtUtil.read(`in`, ExtWrapNullable(ExtWrapTagged()), pf) as XPathExpression?
        relevantExpr = ExtUtil.read(`in`, ExtWrapNullable(ExtWrapTagged()), pf) as XPathExpression?
        iconReferenceForActionBar = ExtUtil.readString(`in`)
        redoLast = ExtUtil.readBool(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.write(out, display as Any)
        ExtUtil.write(out, ExtWrapList(stackOps!!))
        val ale = autoLaunchExpr
        ExtUtil.write(out, ExtWrapNullable(if (ale == null) null else ExtWrapTagged(ale)))
        val re = relevantExpr
        ExtUtil.write(out, ExtWrapNullable(if (re == null) null else ExtWrapTagged(re)))
        ExtUtil.writeString(out, iconReferenceForActionBar)
        ExtUtil.writeBool(out, redoLast)
    }
}
