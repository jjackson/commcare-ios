package org.javarosa.core.model.test

import org.javarosa.core.model.UploadQuestionExtension
import org.javarosa.core.test.FormParseInit
import org.javarosa.xform.parse.UploadQuestionExtensionParser
import org.javarosa.xform.parse.QuestionExtensionParser
import org.javarosa.xform.parse.XFormParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

/**
 * Tests for UploadQuestionExtensionParser and UploadQuestionExtension
 */
class UploadExtensionTest {

    private lateinit var extensionParsers: ArrayList<QuestionExtensionParser>

    @Rule
    @JvmField
    val exception: ExpectedException = ExpectedException.none()

    @Before
    fun init() {
        extensionParsers = arrayListOf()
        extensionParsers.add(UploadQuestionExtensionParser())
    }

    @Test
    fun testParseMaxDimenWithPx() {
        val formWithPx = FormParseInit("/xform_tests/test_upload_extension_1.xml", extensionParsers)
        val q = formWithPx.getFirstQuestionDef()!!

        val extensions = q.extensions
        assertEquals("There should be exactly one QuestionDataExtension registered with this QuestionDef",
                1, extensions.size)

        val ext = extensions[0]
        assertTrue("The extension registered was not an UploadQuestionExtension", ext is UploadQuestionExtension)

        val maxDimen = (ext as UploadQuestionExtension).maxDimen
        assertEquals("Parsed value of max dimen was incorrect", 800, maxDimen)
    }

    @Test
    fun testParseMaxDimenWithoutPx() {
        val formWithoutPx = FormParseInit("/xform_tests/test_upload_extension_2.xml", extensionParsers)
        val q = formWithoutPx.getFirstQuestionDef()!!

        val extensions = q.extensions
        assertEquals("There should be exactly one QuestionDataExtension registered with this QuestionDef",
                1, extensions.size)

        val ext = extensions[0]
        assertTrue("The extension registered was not an UploadQuestionExtension", ext is UploadQuestionExtension)

        val maxDimen = (ext as UploadQuestionExtension).maxDimen
        assertEquals("Parsed value of max dimen was incorrect", 800, maxDimen)
    }

    @Test
    fun testParseInvalidMaxDimen() {
        exception.expect(XFormParseException::class.java)
        exception.expectMessage("Invalid input for image max dimension: bad_dimen")
        FormParseInit("/xform_tests/test_upload_extension_3.xml", extensionParsers)
    }
}
