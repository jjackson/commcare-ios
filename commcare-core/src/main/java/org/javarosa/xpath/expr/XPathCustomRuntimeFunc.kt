package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.IFunctionHandler
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.xpath.XPathArityException
import org.javarosa.xpath.XPathTypeMismatchException
import org.javarosa.xpath.XPathUnhandledException
import org.javarosa.xpath.parser.XPathSyntaxException

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.model.utils.PlatformDate

/**
 * Custom function that is dispatched at runtime
 */
class XPathCustomRuntimeFunc : XPathFuncExpr {

    constructor()

    @Throws(XPathSyntaxException::class)
    constructor(name: String, args: Array<XPathExpression>) : super(name, args, -1, true)

    override fun validateArgCount() {
        // no validation for custom runtime functions
    }

    override fun evalBody(model: DataInstance<*>?, evalContext: EvaluationContext, evaluatedArgs: Array<Any?>): Any {
        throw XPathUnhandledException("function '$name'")
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        super.readExternal(`in`, pf)
        name = SerializationHelpers.readString(`in`)
        cacheState = SerializationHelpers.readExternalizable(`in`, pf) { CacheableExprState() }
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        super.writeExternal(out)
        SerializationHelpers.writeString(out, name)
        SerializationHelpers.write(out, cacheState)
    }

    companion object {
        /**
         * Given a handler registered to handle the function, try to coerce the
         * function arguments into one of the prototypes defined by the handler. If
         * no suitable prototype found, throw an eval exception. Otherwise,
         * evaluate.
         *
         * Note that if the handler supports 'raw args', it will receive the full,
         * unaltered argument list if no prototype matches. (this lets functions
         * support variable-length argument lists)
         */
        @JvmStatic
        fun evalCustomFunction(handler: IFunctionHandler, args: Array<Any?>,
                               ec: EvaluationContext): Any {
            val prototypes = handler.getPrototypes()
            val e = prototypes.iterator()
            var typedArgs: Array<Any?>? = null

            var argPrototypeArityMatch = false
            while (typedArgs == null && e.hasNext()) {
                // try to coerce args into prototype, stopping on first success
                val proto = e.next() as Array<Class<*>>
                typedArgs = matchPrototype(args, proto)
                argPrototypeArityMatch = argPrototypeArityMatch ||
                        (proto.size == args.size)
            }

            try {
                if (typedArgs != null) {
                    return handler.eval(typedArgs, ec)!!
                } else if (handler.rawArgs()) {
                    // should we have support for expanding nodesets here?
                    return handler.eval(args, ec)!!
                } else if (!argPrototypeArityMatch) {
                    // When the argument count doesn't match any of the prototype
                    // sizes, we have an arity error.
                    throw XPathArityException(handler.getName(),
                            "a different number of arguments",
                            args.size)
                } else {
                    throw XPathTypeMismatchException("for function '${handler.getName()}'")
                }
            } catch (ex: XPathArityException) {
                //With static expr's we streat the ArityException as a parse exception to catch it at
                //the appropriate time. Here we need to rethrow as a dynamic exception
                val wrapped = XPathTypeMismatchException(ex.message)
                wrapped.initCause(ex)
                throw wrapped
            }
        }

        /**
         * Given a prototype defined by the function handler, attempt to coerce the
         * function arguments to match that prototype (checking # args, type
         * conversion, etc.). If it is coercible, return the type-converted
         * argument list -- these will be the arguments used to evaluate the
         * function.  If not coercible, return null.
         */
        private fun matchPrototype(args: Array<Any?>, prototype: Array<Class<*>>): Array<Any?>? {
            var typed: Array<Any?>? = null

            if (prototype.size == args.size) {
                typed = arrayOfNulls(args.size)

                for (i in prototype.indices) {
                    typed[i] = null

                    // how to handle type conversions of custom types?
                    if (prototype[i].isAssignableFrom(args[i]!!.javaClass)) {
                        typed[i] = args[i]
                    } else {
                        try {
                            if (prototype[i] == java.lang.Boolean::class.java) {
                                typed[i] = FunctionUtils.toBoolean(args[i])
                            } else if (prototype[i] == java.lang.Double::class.java) {
                                typed[i] = FunctionUtils.toNumeric(args[i])
                            } else if (prototype[i] == String::class.java) {
                                typed[i] = FunctionUtils.toString(args[i])
                            } else if (prototype[i] == PlatformDate::class.java) {
                                typed[i] = FunctionUtils.toDate(args[i])
                            }
                        } catch (xptme: XPathTypeMismatchException) {
                            // ignore, typed[i] stays null
                        }
                    }

                    if (typed[i] == null) {
                        return null
                    }
                }
            }

            return typed
        }
    }
}
