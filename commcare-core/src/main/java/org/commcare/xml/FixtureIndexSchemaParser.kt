package org.commcare.xml

import org.commcare.cases.instance.FixtureIndexSchema
import org.commcare.data.xml.TransactionParser
import org.javarosa.xml.TreeElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.kxml2.io.KXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Parses fixture index schemas into an object representation:
 *
 * <schema id="some-fixture-name">
 *   <indices>
 *     <index>some-index</index>
 *     <index>name,some-index</index>
 *   </indices>
 * </schema>
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
class FixtureIndexSchemaParser(
    parser: KXmlParser,
    private val fixtureSchemas: MutableMap<String, FixtureIndexSchema>,
    private val processedFixtures: Set<String>
) : TransactionParser<FixtureIndexSchema>(parser) {

    companion object {
        const val INDICE_SCHEMA: String = "schema"
    }

    @Throws(PlatformIOException::class, InvalidStructureException::class)
    override fun commit(parsed: FixtureIndexSchema) {
        fixtureSchemas[parsed.fixtureName] = parsed
    }

    @Throws(
        InvalidStructureException::class, PlatformIOException::class,
        PlatformXmlParserException::class, UnfullfilledRequirementsException::class
    )
    override fun parse(): FixtureIndexSchema {
        checkNode(INDICE_SCHEMA)

        val fixtureId = parser.getAttributeValue(null, "id")
            ?: throw InvalidStructureException("$INDICE_SCHEMA is lacking an 'id' attribute", parser)

        if (processedFixtures.contains(fixtureId)) {
            throw InvalidStructureException(
                "$INDICE_SCHEMA for '$fixtureId' appeared after fixture in the user restore", parser
            )
        }

        val root = TreeElementParser(parser, 0, fixtureId).parse()
        val schema = FixtureIndexSchema(root.getChild("indices", 0)!!, fixtureId)
        commit(schema)
        return schema
    }
}
