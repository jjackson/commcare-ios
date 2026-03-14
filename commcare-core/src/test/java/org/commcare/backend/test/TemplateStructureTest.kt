package org.commcare.backend.test

import org.commcare.session.SessionFrame
import org.commcare.test.utilities.MockApp
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * This is a super basic test just to make sure the test infrastructure is working correctly
 * and to act as an example of how to build template app tests.
 *
 * Created by ctsims on 8/14/2015.
 */
class TemplateStructureTest {
    lateinit var mApp: MockApp

    @Before
    fun init() {
        mApp = MockApp("/template/")
    }

    @Test
    fun testBasicSessionWalk() {
        val session = mApp.getSession()
        assertEquals(session.getNeededData(), SessionFrame.STATE_COMMAND_ID)

        session.setCommand("m0")

        assertEquals(session.getNeededData(), SessionFrame.STATE_DATUM_VAL)
        assertEquals(session.getNeededDatum()!!.getDataId(), "case_id")

        session.setEntityDatum("case_id", "case_one")

        assertEquals(session.getNeededData(), SessionFrame.STATE_COMMAND_ID)

        session.setCommand("m0-f0")

        assertEquals(session.getNeededData(), null)

        assertEquals(session.getForm(), "http://commcarehq.org/test/placeholder")
    }
}
