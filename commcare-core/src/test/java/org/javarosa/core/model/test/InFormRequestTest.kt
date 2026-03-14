package org.javarosa.core.model.test

import org.javarosa.core.test.FormParseInit
import org.javarosa.test_utils.MockFormSendCalloutHandler
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Created by ctsims on 9/27/2017.
 */
class InFormRequestTest {

    @Test
    fun testResponseWithParams() {
        val fpi = FormParseInit("/send_action/end_to_end_with_params.xml")
        val form = fpi.getFormDef()!!

        form.setSendCalloutHandler(MockFormSendCalloutHandler.succeedWithArgAtKey("value_two"))
        val fec = FormDefTest.initFormEntry(fpi)
        fec.stepToNextEvent()
        assertEquals("Response:two", fec.getQuestionPrompts()[0].getQuestionText())
    }

    @Test
    fun testResponseNull() {
        val fpi = FormParseInit("/send_action/end_to_end_with_params.xml")
        val form = fpi.getFormDef()!!

        form.setSendCalloutHandler(MockFormSendCalloutHandler.nullResponse())
        val fec = FormDefTest.initFormEntry(fpi)
        fec.stepToNextEvent()
        assertEquals("Response:", fec.getQuestionPrompts()[0].getQuestionText())
    }

    @Test
    fun testResponseError() {
        val fpi = FormParseInit("/send_action/end_to_end_with_params.xml")
        val form = fpi.getFormDef()!!

        form.setSendCalloutHandler(MockFormSendCalloutHandler.withException())
        val fec = FormDefTest.initFormEntry(fpi)
        fec.stepToNextEvent()
        assertEquals("Response:", fec.getQuestionPrompts()[0].getQuestionText())
    }

    @Test
    fun testWithNoParams() {
        val fpi = FormParseInit("/send_action/end_to_end_no_params.xml")
        val form = fpi.getFormDef()!!

        form.setSendCalloutHandler(MockFormSendCalloutHandler.forSuccess("payloadvalue"))
        val fec = FormDefTest.initFormEntry(fpi)
        fec.stepToNextEvent()
        assertEquals("Response:payloadvalue", fec.getQuestionPrompts()[0].getQuestionText())
    }

    @Test
    fun testWithEmptyParams() {
        val fpi = FormParseInit("/send_action/end_to_end_empty_params.xml")
        val form = fpi.getFormDef()!!

        form.setSendCalloutHandler(MockFormSendCalloutHandler.forSuccess("payloadvalue"))
        val fec = FormDefTest.initFormEntry(fpi)
        fec.stepToNextEvent()
        assertEquals("Response:payloadvalue", fec.getQuestionPrompts()[0].getQuestionText())
    }

    @Test
    fun testWithMissingParams() {
        val fpi = FormParseInit("/send_action/end_to_end_missing_params.xml")
        val form = fpi.getFormDef()!!

        form.setSendCalloutHandler(MockFormSendCalloutHandler.forSuccess("payloadvalue"))
        val fec = FormDefTest.initFormEntry(fpi)
        fec.stepToNextEvent()
        assertEquals("Response:payloadvalue", fec.getQuestionPrompts()[0].getQuestionText())
    }

    @Test
    fun testWithNoHandler() {
        val fpi = FormParseInit("/send_action/end_to_end_missing_params.xml")

        val fec = FormDefTest.initFormEntry(fpi)
        fec.stepToNextEvent()
        assertEquals("Response:", fec.getQuestionPrompts()[0].getQuestionText())
    }
}
