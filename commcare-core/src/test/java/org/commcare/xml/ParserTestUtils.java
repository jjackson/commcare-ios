package org.commcare.xml;

import org.javarosa.xml.ElementParser;
import org.javarosa.xml.PlatformXmlParser;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.util.function.Function;

/**
 * Helper functions for building parsers
 */
public class ParserTestUtils {

    public static <T extends CommCareElementParser> T buildParser(String xml, Class<T> parserClass) {
        return buildParser(xml, (xmlParser) -> {
            try {
                Constructor<T> constructor = parserClass.getConstructor(PlatformXmlParser.class);
                return constructor.newInstance(xmlParser);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static <T extends CommCareElementParser> T buildParser(String xml, Function<PlatformXmlParser, T> builder) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(xml.getBytes("UTF-8"));
            PlatformXmlParser parser = ElementParser.instantiateParser(inputStream);
            return builder.apply(parser);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
