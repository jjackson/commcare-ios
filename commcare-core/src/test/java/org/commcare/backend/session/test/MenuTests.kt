package org.commcare.backend.session.test

import org.commcare.suite.model.Suite
import org.commcare.test.utilities.MockApp
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for assertions in menus
 */
class MenuTests {

    lateinit var appWithMenuAssertions: MockApp
    lateinit var suite: Suite

    @Before
    fun setup() {
        appWithMenuAssertions = MockApp("/app_structure/")
        suite = appWithMenuAssertions.getSession().getPlatform()!!.getInstalledSuites()[0]
    }

    @Test
    fun testAssertionsEvaluated() {
        val menuWithAssertionsBlock = suite.getMenusWithId("m0")!![0]
        val assertions = menuWithAssertionsBlock.getAssertions()
        val ec = appWithMenuAssertions.getSession().getEvaluationContext()
        val assertionFailures = assertions.getAssertionFailure(ec)!!
        assertNotNull(assertions)
        assertEquals("custom_assertion.m0.0", assertionFailures.getArgument())
    }

    /**
     * When there are multiple menu blocks with same ids, we should accumulate the required instances from all
     * of the menus and their contained entries
     */
    @Test
    fun testMenuInstances_WhenMenuHaveSameIds() {
        val currentSession = appWithMenuAssertions.getSession()
        val ec = currentSession.getEvaluationContext(currentSession.getIIF(), "m3", null)
        val instanceIds = ec.getInstanceIds()
        assertEquals(2, instanceIds.size)
        assertTrue(instanceIds.contains("my_instance"))
        assertTrue(instanceIds.contains("casedb"))
    }
}
