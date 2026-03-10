package org.commcare.core.parse

import org.commcare.core.interfaces.UserSandbox
import org.commcare.data.xml.DataModelPullParser
import org.commcare.data.xml.TransactionParserFactory
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.xmlpull.v1.XmlPullParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import java.io.InputStream

/**
 * Created by wpride1 on 8/11/15.
 */
object ParseUtils {

    @JvmStatic
    @Throws(
        InvalidStructureException::class,
        UnfullfilledRequirementsException::class,
        XmlPullParserException::class,
        PlatformIOException::class
    )
    fun parseIntoSandbox(stream: InputStream, sandbox: UserSandbox) {
        parseIntoSandbox(stream, sandbox, failfast = false)
    }

    @JvmStatic
    @Throws(
        InvalidStructureException::class,
        UnfullfilledRequirementsException::class,
        XmlPullParserException::class,
        PlatformIOException::class
    )
    fun parseIntoSandbox(stream: InputStream, sandbox: UserSandbox, failfast: Boolean) {
        parseIntoSandbox(stream, sandbox, failfast, bulkProcessingEnabled = false)
    }

    @JvmStatic
    @Throws(
        InvalidStructureException::class,
        PlatformIOException::class,
        UnfullfilledRequirementsException::class,
        XmlPullParserException::class
    )
    fun parseIntoSandbox(
        stream: InputStream,
        sandbox: UserSandbox,
        failfast: Boolean,
        bulkProcessingEnabled: Boolean
    ) {
        val factory = CommCareTransactionParserFactory(sandbox, bulkProcessingEnabled)
        parseIntoSandbox(stream, factory, failfast, bulkProcessingEnabled)
    }

    @JvmStatic
    @Throws(
        PlatformIOException::class,
        InvalidStructureException::class,
        UnfullfilledRequirementsException::class,
        XmlPullParserException::class
    )
    fun parseIntoSandbox(
        stream: InputStream,
        factory: TransactionParserFactory,
        failfast: Boolean,
        bulkProcessingEnabled: Boolean
    ) {
        val parser = DataModelPullParser(stream, factory, failfast, bulkProcessingEnabled)
        parser.parse()
    }
}
