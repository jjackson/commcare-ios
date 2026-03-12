package org.commcare.suite.model

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.xpath.expr.XPathExpression

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

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

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        test = SerializationHelpers.readTagged(`in`, pf) as XPathExpression
        message = SerializationHelpers.readNullableExternalizable(`in`, pf) { Text() }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeTagged(out, test!!)
        SerializationHelpers.writeNullable(out, message)
    }

    fun getTest(): XPathExpression? = test

    fun getMessage(): Text? = message
}
