package org.commcare.core.process

import org.commcare.core.interfaces.UserSandbox
import org.commcare.data.xml.DataModelPullParser
import org.commcare.data.xml.TransactionParserFactory
import org.commcare.xml.CaseXmlParser
import org.commcare.xml.LedgerXmlParsers
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.io.PlatformInputStream
import kotlin.jvm.JvmStatic

/**
 * Utility methods for processing XML transactions against a user sandbox.
 * This was written to make TouchForms XML submissions easier to perform. Only processes
 * blocks that need to be transacted against the user record (IE cases and ledgers at the moment).
 * This should be used when you have a raw input stream of the XML; FormRecordProcessor on Android
 * should be used when you have a FormRecord object
 *
 * Created by wpride1 on 7/21/15.
 */
object XmlFormRecordProcessor {

    @JvmStatic
    @Throws(
        InvalidStructureException::class,
        PlatformIOException::class,
        PlatformXmlParserException::class,
        UnfullfilledRequirementsException::class
    )
    fun process(sandbox: UserSandbox, stream: PlatformInputStream) {
        process(stream, TransactionParserFactory { parser ->
            if (LedgerXmlParsers.STOCK_XML_NAMESPACE == parser.getNamespace()) {
                LedgerXmlParsers(parser, sandbox.getLedgerStorage())
            } else if ("case".equals(parser.getName(), ignoreCase = true)) {
                CaseXmlParser(parser, sandbox.getCaseStorage())
            } else {
                null
            }
        })
    }

    @JvmStatic
    @Throws(
        InvalidStructureException::class,
        PlatformIOException::class,
        PlatformXmlParserException::class,
        UnfullfilledRequirementsException::class
    )
    fun process(stream: PlatformInputStream, factory: TransactionParserFactory) {
        val parser = DataModelPullParser(stream, factory, true, true)
        parser.parse()
    }
}
