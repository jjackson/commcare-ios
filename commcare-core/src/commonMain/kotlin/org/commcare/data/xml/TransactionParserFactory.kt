package org.commcare.data.xml

import org.javarosa.xml.PlatformXmlParser

fun interface TransactionParserFactory {
    fun getParser(parser: PlatformXmlParser): TransactionParser<*>?
}
