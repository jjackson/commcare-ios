package org.commcare.suite.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.expr.XPathExpression

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Model for `<validation>` node in [QueryPrompt]
 */
class QueryPromptCondition : Externalizable {
    private var test: XPathExpression? = null
    private var message: Text? = null

    constructor()

    constructor(test: XPathExpression?, message: Text?) {
        this.test = test
        this.message = message
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        test = ExtUtil.read(`in`, ExtWrapTagged(), pf) as XPathExpression
        message = ExtUtil.read(`in`, ExtWrapNullable(Text::class.java), pf) as Text?
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.write(out, ExtWrapTagged(test!!))
        ExtUtil.write(out, ExtWrapNullable(message))
    }

    fun getTest(): XPathExpression? = test

    fun getMessage(): Text? = message
}
