package org.javarosa.xform.parse

import org.javarosa.core.test.FormParseInit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Created by ctsims on 07/07/2020
 */
class TextEncodingTests {

    @Test
    fun testUnicode() {
        val fpi = FormParseInit("/xform_tests/itext_encoding.xml")
        val l = fpi.getFormDef()!!.getLocalizer()!!
        assertEquals("\uD83E\uDDD2", l.getText("four_byte_emoji", "en"))
    }
}
