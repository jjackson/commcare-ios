package org.commcare.xml

import org.commcare.suite.model.Endpoint
import org.commcare.suite.model.EndpointArgument
import org.commcare.suite.model.StackOperation
import org.javarosa.core.model.instance.ExternalDataInstance.Companion.JR_SELECTED_ENTITIES_REFERENCE
import org.javarosa.xml.ElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import kotlin.jvm.JvmField

class EndpointParser(parser: PlatformXmlParser) : ElementParser<Endpoint>(parser) {

    companion object {
        @JvmField
        val NAME_ENDPOINT: String = "endpoint"
        private const val NAME_ARGUMENT = "argument"
        private const val ATTR_ARGUMENT_ID = "id"
        private const val ATTR_ARGUMENT_RESPECT_RELEVANCY = "respect-relevancy"
        private const val ATTR_ARGUMENT_INSTANCE_ID = "instance-id"
        private const val ATTR_ARGUMENT_INSTANCE_SRC = "instance-src"
    }

    @Throws(
        InvalidStructureException::class, PlatformIOException::class,
        PlatformXmlParserException::class, UnfullfilledRequirementsException::class
    )
    override fun parse(): Endpoint {
        val endpointId = parser.getAttributeValue(null, "id")
        if (endpointId == null || endpointId.isEmpty()) {
            throw InvalidStructureException("endpoint must define a non empty id", parser)
        }

        val respectRelevancyStr = parser.getAttributeValue(null, ATTR_ARGUMENT_RESPECT_RELEVANCY)

        // we are defaulting to true by checking against "false"
        val respectReslevancy = "false" != respectRelevancyStr

        val stackOperations = ArrayList<StackOperation>()
        val arguments = ArrayList<EndpointArgument>()

        while (nextTagInBlock(NAME_ENDPOINT)) {
            val tagName = parser.getName()!!.lowercase()
            if (tagName.contentEquals(NAME_ARGUMENT)) {
                val argumentID = parser.getAttributeValue(null, ATTR_ARGUMENT_ID)
                if (argumentID == null || argumentID.isEmpty()) {
                    throw InvalidStructureException("argument must define a non empty id", parser)
                }
                val argInstanceId = parser.getAttributeValue(null, ATTR_ARGUMENT_INSTANCE_ID)
                val argInstanceSrc = parser.getAttributeValue(null, ATTR_ARGUMENT_INSTANCE_SRC)

                if (argInstanceId != null && argInstanceSrc == null) {
                    throw InvalidStructureException(
                        "Endpoint argument containing a non-null instance-id must define an instance-src", parser
                    )
                }

                val validInstanceSrc = listOf(JR_SELECTED_ENTITIES_REFERENCE)
                if (argInstanceSrc != null && !validInstanceSrc.contains(argInstanceSrc)) {
                    throw InvalidStructureException(
                        "instance-src for an endpoint argument must be one of $validInstanceSrc", parser
                    )
                }

                arguments.add(EndpointArgument(argumentID, argInstanceId, argInstanceSrc))
            } else if (tagName.contentEquals(StackOpParser.NAME_STACK)) {
                val sop = StackOpParser(parser)
                while (this.nextTagInBlock(StackOpParser.NAME_STACK)) {
                    stackOperations.add(sop.parse())
                }
            }
        }

        return Endpoint(endpointId, arguments, stackOperations, respectReslevancy)
    }
}
