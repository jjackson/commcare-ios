package org.commcare.backend.suite.model.test

import org.commcare.suite.model.MenuLoader
import org.commcare.test.utilities.MockApp
import org.commcare.util.LoggerInterface
import org.junit.Assert
import org.junit.Before
import org.junit.Test

/**
 * Tests for app functioning with empty (edge case) elements
 *
 * @author ctsims
 */
class EmptyAppElementsTests {

    private lateinit var mApp: MockApp

    @Before
    fun setUp() {
        mApp = MockApp("/empty_app_elements/")
    }

    @Test
    fun testEmptyMenu() {
        val session = mApp.getSession()

        val menuLoader = MenuLoader(session.getPlatform(), session, "root", TestLogger(), false, false)
        val choices = menuLoader.menus!!
        Assert.assertEquals("Number of Menu roots in empty example", choices.size, 1)
    }

    @Test
    fun testEmptyGlobal() {
        val global = mApp.getSession().getPlatform()!!.getDetail("m0_case_short")!!.getGlobal()!!
        Assert.assertEquals(0, global.getGeoOverlays().size)
    }

    class TestLogger : LoggerInterface {
        override fun logError(message: String, cause: Exception) {
            Assert.fail(message)
        }

        override fun logError(message: String) {
            Assert.fail(message)
        }
    }
}
