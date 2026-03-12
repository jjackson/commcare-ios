package org.commcare.data.xml

import org.javarosa.xml.ElementParser
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * @author ctsims
 */
abstract class TransactionParser<T>(parser: PlatformXmlParser) : ElementParser<T>(parser) {

    @Throws(PlatformIOException::class, InvalidStructureException::class)
    protected abstract fun commit(parsed: T)

    /**
     * Notifies the parser that the end-to-end parse has been completed and allows it to
     * clean up any state it may have reserved.
     */
    @Throws(PlatformIOException::class, PlatformXmlParserException::class, InvalidStructureException::class)
    internal open fun flush() {
    }
}
