package org.javarosa.xpath.expr

import org.commcare.util.EncryptionUtils
import org.commcare.util.EncryptionUtils.decrypt
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.parser.XPathSyntaxException

open class XPathDecryptStringFunc : XPathFuncExpr {
    constructor() {
        name = NAME
        expectedArgCount = EXPECTED_ARG_COUNT
    }

    @Throws(XPathSyntaxException::class)
    constructor(args: Array<XPathExpression>) : super(NAME, args, EXPECTED_ARG_COUNT, true)

    override fun evalBody(model: DataInstance<*>, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        return decryptString(evaluatedArgs[0], evaluatedArgs[1], evaluatedArgs[2])
    }

    companion object {
        const val NAME: String = "decrypt-string"
        private const val EXPECTED_ARG_COUNT: Int = 3

        /**
         * Encrypt a message with the given algorithm and key.
         *
         * @param o1 a message to be decrypted
         * @param o2 the key used for encryption
         * @param o3 the encryption algorithm to use
         */
        private fun decryptString(o1: Any?, o2: Any?, o3: Any?): String {
            val message = FunctionUtils.toString(o1)
            val key = FunctionUtils.toString(o2)
            val algorithm = FunctionUtils.toString(o3)

            if (algorithm != "AES") {
                throw XPathException("Unknown algorithm \"$algorithm\" for $NAME")
            }

            try {
                return decrypt(message, key)
            } catch (e: EncryptionUtils.EncryptionException) {
                throw XPathException(e)
            }
        }
    }
}
