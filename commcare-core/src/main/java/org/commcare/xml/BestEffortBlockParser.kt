package org.commcare.xml

import org.commcare.data.xml.TransactionParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.xml.PlatformXmlParser

/**
 * This parser is for scanning through a block making a best-effort to identify a few
 * nodes inside. Valuable for semi-structured data.
 *
 * Note: Doesn't process attributes usefully yet.
 *
 * @author ctsims
 */
abstract class BestEffortBlockParser(
    parser: PlatformXmlParser,
    private val elements: Array<String>
) : TransactionParser<HashMap<String, String>>(parser) {

    @Throws(PlatformIOException::class)
    abstract override fun commit(parsed: HashMap<String, String>)

    @Throws(
        InvalidStructureException::class, PlatformIOException::class,
        PlatformXmlParserException::class, UnfullfilledRequirementsException::class
    )
    override fun parse(): HashMap<String, String> {
        val name = parser.name
        val ret = HashMap<String, String>()

        var expecting = false
        var expected: String? = null
        while (this.nextTagInBlock(name)) {
            if (expecting) {
                if (parser.eventType == PlatformXmlParser.TEXT) {
                    ret[expected!!] = parser.text!!
                }
                expecting = false
            }
            if (matches()) {
                expecting = true
                expected = parser.name
            }
        }
        commit(ret)
        return ret
    }

    private fun matches(): Boolean {
        val name = parser.name
        for (elementName in elements) {
            if (elementName == name) {
                return true
            }
        }
        return false
    }
}
