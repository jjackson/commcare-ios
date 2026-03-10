package org.commcare.suite.model

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

class Endpoint : Externalizable {
    @JvmField
    internal var id: String? = null
    @JvmField
    internal var respectRelevancy: Boolean = false
    @JvmField
    internal var arguments: ArrayList<EndpointArgument>? = null
    @JvmField
    internal var stackOperations: ArrayList<StackOperation>? = null

    // for serialization
    constructor()

    constructor(
        id: String?,
        arguments: ArrayList<EndpointArgument>?,
        stackOperations: ArrayList<StackOperation>?,
        respectRelevancy: Boolean
    ) {
        this.id = id
        this.arguments = arguments
        this.stackOperations = stackOperations
        this.respectRelevancy = respectRelevancy
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        id = ExtUtil.readString(`in`)
        arguments = ExtUtil.read(`in`, ExtWrapList(EndpointArgument::class.java), pf) as ArrayList<EndpointArgument>
        stackOperations = ExtUtil.read(`in`, ExtWrapList(StackOperation::class.java), pf) as ArrayList<StackOperation>
        respectRelevancy = ExtUtil.readBool(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeString(out, id!!)
        ExtUtil.write(out, ExtWrapList(arguments!!))
        ExtUtil.write(out, ExtWrapList(stackOperations!!))
        ExtUtil.writeBool(out, respectRelevancy)
    }

    fun getId(): String? = id

    fun getArguments(): ArrayList<EndpointArgument> = arguments!!

    fun getStackOperations(): ArrayList<StackOperation> = stackOperations!!

    fun isRespectRelevancy(): Boolean = respectRelevancy

    // Utility Functions
    companion object {
        @JvmStatic
        fun populateEndpointArgumentsToEvaluationContext(
            endpoint: Endpoint,
            args: ArrayList<String>,
            evaluationContext: EvaluationContext
        ) {
            val endpointArguments = endpoint.getArguments()

            if (endpointArguments.size > args.size) {
                val missingArguments = ArrayList<String>()
                for (i in args.size until endpointArguments.size) {
                    missingArguments.add(endpointArguments[i].getId()!!)
                }
                throw InvalidEndpointArgumentsException(missingArguments, null)
            }

            for (i in 0 until endpointArguments.size) {
                val argumentName = endpointArguments[i].getId()!!
                evaluationContext.setVariable(argumentName, args[i])
            }
        }

        @JvmStatic
        fun populateEndpointArgumentsToEvaluationContext(
            endpoint: Endpoint,
            args: HashMap<String, String>,
            evaluationContext: EvaluationContext
        ) {
            val endpointArguments = endpoint.getArguments()
            val argumentIds = args.keys
            val missingArguments = ArrayList<String>()
            for (endpointArgument in endpointArguments) {
                if (!argumentIds.contains(endpointArgument.getId())) {
                    missingArguments.add(endpointArgument.getId()!!)
                }
            }

            val unexpectedArguments = ArrayList<String>()
            for (argumentId in argumentIds) {
                if (!isValidArgumentId(endpointArguments, argumentId)) {
                    unexpectedArguments.add(argumentId)
                }
            }

            if (missingArguments.size > 0 || unexpectedArguments.size > 0) {
                throw InvalidEndpointArgumentsException(missingArguments, unexpectedArguments)
            }

            for (i in 0 until endpointArguments.size) {
                val argumentName = endpointArguments[i].getId()!!
                if (args.containsKey(argumentName)) {
                    evaluationContext.setVariable(argumentName, args[argumentName])
                }
            }
        }

        private fun isValidArgumentId(
            endpointArguments: ArrayList<EndpointArgument>,
            argumentId: String
        ): Boolean {
            for (endpointArgument in endpointArguments) {
                if (endpointArgument.getId()!!.contentEquals(argumentId)) {
                    return true
                }
            }
            return false
        }
    }

    class InvalidEndpointArgumentsException(
        private val missingArguments: ArrayList<String>?,
        private val unexpectedArguments: ArrayList<String>?
    ) : RuntimeException() {

        fun hasMissingArguments(): Boolean = missingArguments != null && missingArguments.size > 0

        fun getMissingArguments(): ArrayList<String>? = missingArguments

        fun hasUnexpectedArguments(): Boolean = unexpectedArguments != null && unexpectedArguments.size > 0

        fun getUnexpectedArguments(): ArrayList<String>? = unexpectedArguments
    }
}
