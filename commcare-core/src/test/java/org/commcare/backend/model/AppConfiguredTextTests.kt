package org.commcare.backend.model

import org.commcare.backend.suite.model.test.EmptyAppElementsTests
import org.commcare.modern.session.SessionWrapper
import org.commcare.suite.model.MenuDisplayable
import org.commcare.suite.model.MenuLoader
import org.commcare.test.utilities.MockApp
import org.javarosa.core.services.locale.Localization
import org.junit.Assert
import org.junit.Before
import org.junit.Test

/**
 * Tests for `<text/>` elements as they are used live in the suite file structure
 *
 * @author ctsims
 */
class AppConfiguredTextTests {

    private lateinit var mApp: MockApp
    private lateinit var session: SessionWrapper

    @Before
    fun setUp() {
        mApp = MockApp("/app_for_text_tests/")
        session = mApp.getSession()
        Localization.setDefaultLocale("default")
    }

    @Test
    fun testBasicText() {
        val display = getDisplayable("test1")

        val evaluationContext = session.getEvaluationContext("test1")

        Assert.assertEquals("RAWTEXT", display.getDisplayText(evaluationContext))
    }

    @Test
    fun testLocalizedTextBehavior() {
        Localization.setLocale("en")
        val display = getDisplayable("test2")
        val evaluationContext = session.getEvaluationContext("test2")

        Assert.assertEquals("EnglishString", display.getDisplayText(evaluationContext))

        Localization.setLocale("hin")

        Assert.assertEquals("DefaultString", display.getDisplayText(evaluationContext))
    }

    @Test
    fun testLocalizationParams() {
        Localization.setLocale("en")
        val display = getDisplayable("test3")
        val evaluationContext = session.getEvaluationContext("test3")

        Assert.assertEquals("ValueArgument", display.getDisplayText(evaluationContext))

        Localization.setLocale("hin")

        Assert.assertEquals("ArgumentValue", display.getDisplayText(evaluationContext))
    }

    @Test
    fun testLocalizationIdParam() {
        Localization.setLocale("en")
        val display = getDisplayable("test4")
        val evaluationContext = session.getEvaluationContext("test4")

        Assert.assertEquals("Message1", display.getDisplayText(evaluationContext))

        Localization.setLocale("hin")

        Assert.assertEquals("Message2", display.getDisplayText(evaluationContext))
    }

    @Test
    fun testMultipleArgs() {
        Localization.setLocale("en")
        val display = getDisplayable("test5")
        val evaluationContext = session.getEvaluationContext("test5")

        Assert.assertEquals("OneThreeTwo", display.getDisplayText(evaluationContext))

        Localization.setLocale("hin")

        Assert.assertEquals("TwoOneThree", display.getDisplayText(evaluationContext))
    }

    @Test
    fun testMultipleArgsAndId() {
        Localization.setLocale("en")
        val display = getDisplayable("test6")
        val evaluationContext = session.getEvaluationContext("test6")

        Assert.assertEquals("OneThreeTwo", display.getDisplayText(evaluationContext))

        Localization.setLocale("hin")

        Assert.assertEquals("TwoOneThree", display.getDisplayText(evaluationContext))
    }

    private fun getDisplayable(commandId: String): MenuDisplayable {
        val menuLoader = MenuLoader(
            session.getPlatform(), session, "root",
            EmptyAppElementsTests.TestLogger(), false, false
        )

        for (displayable in menuLoader.menus!!) {
            if (displayable.getCommandID() == commandId) {
                return displayable
            }
        }
        throw RuntimeException("No Command $commandId found in test harness")
    }
}
