package org.commcare.backend.session.test

import org.commcare.session.SessionNavigator
import org.commcare.test.utilities.MockApp
import org.commcare.test.utilities.MockSessionNavigationResponder
import org.javarosa.form.api.FormEntryController
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Created by ctsims on 12/2/2016
 */
class InstanceEvaluationTests {

    private lateinit var mApp: MockApp
    private lateinit var sessionNavigator: SessionNavigator

    @Before
    fun setUp() {
        mApp = MockApp("/mixed_instance_initializers/")
        val mockSessionNavigationResponder = MockSessionNavigationResponder(mApp.getSession())
        sessionNavigator = SessionNavigator(mockSessionNavigationResponder)
        mApp.getSession().clearVolatiles()
    }

    /**
     * Testing cases where instances are used with different ID's in multiple contexts
     */
    @Test
    fun testMixedInstanceIdCaching() {
        val session = mApp.getSession()
        sessionNavigator.startNextSessionStep()

        session.setCommand("m0")
        sessionNavigator.startNextSessionStep()

        session.setCommand("m0-f0")
        sessionNavigator.startNextSessionStep()

        val fec = mApp.loadAndInitForm("form_placeholder.xml")

        assertEquals(FormEntryController.EVENT_BEGINNING_OF_FORM, fec.getModel().getEvent())

        fec.stepToNextEvent()

        assertEquals("one", fec.getQuestionPrompts()[0].getQuestionText())
    }

    @Test
    fun loadIndexedInstanceInForm() {
        val fec = mApp.loadAndInitForm("form_placeholder.xml")

        assertEquals(FormEntryController.EVENT_BEGINNING_OF_FORM, fec.getModel().getEvent())

        fec.stepToNextEvent()
        fec.stepToNextEvent()
        assertEquals("Huffy", fec.getQuestionPrompts()[0].getQuestionText())
        fec.stepToNextEvent()
        assertEquals("three", fec.getQuestionPrompts()[0].getQuestionText())
    }
}
