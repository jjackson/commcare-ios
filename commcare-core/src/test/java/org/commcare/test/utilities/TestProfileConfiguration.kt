package org.commcare.test.utilities

import org.commcare.core.interfaces.UserSandbox
import org.commcare.core.parse.ParseUtils
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.util.Arrays

/**
 * Holds potential configuration profile hooks that let tests run over lots of different scopes
 * a bit more easily
 *
 * Created by ctsims on 3/15/2017.
 */
class TestProfileConfiguration(private val useBulkCaseProcessing: Boolean) {

    override fun toString(): String {
        return String.format("Bulk Parse[%s]", if (useBulkCaseProcessing) "On" else "Off")
    }

    @Throws(InvalidStructureException::class, UnfullfilledRequirementsException::class, XmlPullParserException::class, IOException::class)
    fun parseIntoSandbox(stream: InputStream, sandbox: UserSandbox) {
        ParseUtils.parseIntoSandbox(stream, sandbox, false, useBulkCaseProcessing)
    }

    @Throws(InvalidStructureException::class, UnfullfilledRequirementsException::class, XmlPullParserException::class, IOException::class)
    fun parseIntoSandbox(stream: InputStream, sandbox: UserSandbox, failfast: Boolean) {
        ParseUtils.parseIntoSandbox(stream, sandbox, failfast, useBulkCaseProcessing)
    }

    companion object {
        @JvmStatic
        fun BulkOffOn(): Collection<Array<Any>> {
            val data = arrayOf(
                arrayOf<Any>(TestProfileConfiguration(true)),
                arrayOf<Any>(TestProfileConfiguration(false))
            )
            return Arrays.asList(*data)
        }
    }
}
