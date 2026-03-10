package org.commcare.data.xml;

import org.javarosa.xml.PlatformXmlParser;

public interface TransactionParserFactory {
    TransactionParser getParser(PlatformXmlParser parser);
}
