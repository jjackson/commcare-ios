package org.commcare.xml

import org.javarosa.xml.ElementParser
import org.javarosa.xml.PlatformXmlParser
import java.io.ByteArrayInputStream
import java.util.function.Function

/**
 * Helper functions for building parsers
 */
class ParserTestUtils {
    companion object {
        @JvmStatic
        fun <T : CommCareElementParser<*>> buildParser(xml: String, parserClass: Class<T>): T {
            return buildParser(xml, Function { xmlParser ->
                try {
                    val constructor = parserClass.getConstructor(PlatformXmlParser::class.java)
                    constructor.newInstance(xmlParser)
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            })
        }

        @JvmStatic
        fun <T : CommCareElementParser<*>> buildParser(xml: String, builder: Function<PlatformXmlParser, T>): T {
            try {
                val inputStream = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))
                val parser = ElementParser.instantiateParser(inputStream)
                return builder.apply(parser)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }
}
