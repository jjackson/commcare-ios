package org.commcare.backend.suite.model.test

import org.commcare.resources.model.UnresolvedResourceException
import org.commcare.suite.model.Text
import org.commcare.test.utilities.MockApp
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for general app structure, like menus and commands
 *
 * @author ctsims
 */
class AppStructureTests {

    private lateinit var mApp: MockApp

    @Before
    fun setUp() {
        mApp = MockApp("/app_structure/")
    }

    @Test
    fun testMenuStyles() {
        assertEquals(
            "Root Menu Style",
            "grid",
            mApp.getSession().getPlatform()!!.getMenuDisplayStyle("root")
        )

        assertEquals(
            "Common Menu Style",
            "list",
            mApp.getSession().getPlatform()!!.getMenuDisplayStyle("m1")
        )

        assertEquals(
            "Disperate Menu Style",
            null,
            mApp.getSession().getPlatform()!!.getMenuDisplayStyle("m2")
        )

        assertEquals(
            "Empty Menu",
            null,
            mApp.getSession().getPlatform()!!.getMenuDisplayStyle("m0")
        )

        assertEquals(
            "Specific override",
            "grid",
            mApp.getSession().getPlatform()!!.getMenuDisplayStyle("m3")
        )
    }

    @Test
    fun testDetailStructure() {
        // A suite detail can have a lookup block for performing an app callout
        val callout = mApp.getSession().getPlatform()!!.getDetail("m0_case_short")!!.getCallout()!!

        // specifies the callout's intent type
        assertEquals(callout.evaluate(mApp.getSession().getEvaluationContext()).type, "text/plain")

        // If the detail block represents an entity list, then the 'lookup' can
        // have a detail field describing the UI for displaying callout result
        // data in the case list.
        val lookupCalloutDetailField = callout.getResponseDetailField()

        // The header is the data's title
        assertTrue(lookupCalloutDetailField!!.getHeader() != null)

        // The template defines the key used to map an entity to the callout
        // result data.  callout result data is a mapping from keys to string
        // values, so each entity who's template evalutates to a key will have
        // the associated result data attached to it.
        assertTrue(lookupCalloutDetailField!!.getTemplate() is Text)
    }

    @Test
    fun testDetailWithFocusFunction() {
        val focusFunction = mApp.getSession().getPlatform()!!.getDetail("m1_case_short")!!.getFocusFunction()
        assertTrue(focusFunction != null)
    }

    @Test
    fun testDetailWithoutFocusFunction() {
        val focusFunction = mApp.getSession().getPlatform()!!.getDetail("m0_case_short")!!.getFocusFunction()
        assertTrue(focusFunction == null)
    }

    @Test
    fun testDetailGlobalStructure() {
        val global = mApp.getSession().getPlatform()!!.getDetail("m0_case_short")!!.getGlobal()!!
        assertEquals(2, global.getGeoOverlays().size)

        val geoOverlay1 = global.getGeoOverlays()[0]
        assertEquals("region1", geoOverlay1.getLabel()!!!!.evaluate().name)
        assertEquals(
            "25.099143024399652,76.51193084262178 \\n25.09659806293257,76.50851525117463 \\n25.094815052360374,76.51072357910209 \\n25.097369086424337,76.51234989287263",
            geoOverlay1.getCoordinates()!!!!.evaluate().name
        )

        val geoOverlay2 = global.getGeoOverlays()[1]
        assertEquals("region2", geoOverlay2.getLabel()!!!!.evaluate().name)
        assertEquals(
            "76.51193084262178,25.099143024399652 \\n76.50851525117463,25.09659806293257 \\n76.51072357910209,25.094815052360374 \\n76.51234989287263,25.097369086424337",
            geoOverlay2.getCoordinates()!!!!.evaluate().name
        )
    }

    @Test
    fun testDetailNoItemsText() {
        val noItemsText = mApp.getSession().getPlatform()!!.getDetail("m0_case_short")!!.getNoItemsText()!!
        assertEquals("Empty List", noItemsText.evaluate())
    }

    @Test
    fun testDetailSelectText() {
        val selectText = mApp.getSession().getPlatform()!!.getDetail("m0_case_short")!!.getSelectText()!!
        assertEquals("Continue With Case", selectText.evaluate())
    }

    @Test
    fun testDemoUserRestoreParsing() {
        // Test parsing an app with a properly-formed demo user restore file
        val appWithGoodUserRestore = MockApp("/app_with_good_demo_restore/")
        val offlineUserRestore = appWithGoodUserRestore.getSession().getPlatform()!!.getDemoUserRestore()!!
        assertNotNull(offlineUserRestore)
        assertEquals("test", offlineUserRestore.getUsername())

        // Test parsing an app where the user_type is not set to 'demo'
        var exceptionThrown = false
        try {
            MockApp("/app_with_bad_demo_restore/")
        } catch (e: UnresolvedResourceException) {
            exceptionThrown = true
            val expectedErrorMsg =
                "Demo user restore file must be for a user with user_type set to demo"
            assertEquals(
                "The UnresolvedResourceException that was thrown was due to an unexpected cause, " +
                        "the actual error message is: " + e.message,
                expectedErrorMsg,
                e.message
            )
        }
        if (!exceptionThrown) {
            fail(
                "A demo user restore file that does not specify user_type to demo should throw " +
                        "an UnfulfilledRequirementsException"
            )
        }

        // Test parsing an app where the username block is empty
        exceptionThrown = false
        try {
            MockApp("/app_with_bad_demo_restore2/")
        } catch (e: UnresolvedResourceException) {
            exceptionThrown = true
            val expectedErrorMsg =
                "Demo user restore file must specify a username in the Registration block"
            assertEquals(
                "The UnresolvedResourceException that was thrown was due to an unexpected cause, " +
                        "the actual error message is: " + e.message,
                expectedErrorMsg,
                e.message
            )
        }
        if (!exceptionThrown) {
            fail(
                "A demo user restore file that does not specify a username should throw " +
                        "an UnfulfilledRequirementsException"
            )
        }
    }

    @Test
    fun testDisplayBlockParsing_good() {
        val appWithGoodUserRestore = MockApp("/app_with_good_numeric_badge/")
        val s = appWithGoodUserRestore.getSession().getPlatform()!!.getInstalledSuites()[0]
        val menuWithDisplayBlock = s!!.getMenusWithId("m1")!![0]
        assertEquals("Menu 1 Text", menuWithDisplayBlock.getDisplayText(null))
        val ec = appWithGoodUserRestore.getSession().getEvaluationContext(menuWithDisplayBlock.getId()!!)
        val testObserver = menuWithDisplayBlock.getTextForBadge(ec)!!.single.test()
        testObserver.assertNoErrors()
        testObserver.assertValue("1")
    }

    @Test
    fun testDisplayBlockParsing_invalidXPathExpr() {
        var exceptionThrown = false
        try {
            MockApp("/app_with_bad_numeric_badge/")
        } catch (e: UnresolvedResourceException) {
            exceptionThrown = true
            val expectedErrorMsg = "Invalid XPath Expression : ,3"
            assertTrue(
                "The exception that was thrown was due to an unexpected cause",
                e.message!!.contains(expectedErrorMsg)
            )
        }
        if (!exceptionThrown) {
            fail(
                "A Text block of form badge whose xpath element contains an invalid xpath " +
                        "expression should throw an exception"
            )
        }
    }

    @Test
    fun testMenuAssertions() {
        val s = mApp.getSession().getPlatform()!!.getInstalledSuites()[0]
        val menuWithAssertionsBlock = s!!.getMenusWithId("m0")!![0]
        val assertions = menuWithAssertionsBlock.getAssertions()
        assertNotNull(assertions)
    }

    @Test
    fun testDetailWithFieldAction() {
        val detail = mApp.getSession().getPlatform()!!.getDetail("m0_case_short")!!
        val field = detail.getFields()[0]
        val endpointAction = field.getEndpointAction()!!
        val endpointActionId = endpointAction.getEndpointId()!!
        assertEquals("case_list", endpointActionId)
        assertEquals(true, endpointAction.isBackground())

        val endpoint = mApp.getSession().getPlatform()!!.getEndpoint(endpointActionId)!!
        assertEquals(endpoint.getId(), endpointActionId)
        assertFalse(endpoint.isRespectRelevancy())
    }

    @Test
    fun testDetailWithAltText() {
        val detail = mApp.getSession().getPlatform()!!.getDetail("m0_case_short")!!
        val field = detail.getFields()[0]
        val altText = field.getAltText()!!
        assertEquals("gold star", altText.evaluate())
    }

    @Test
    fun testDetailWithBorder() {
        val detail = mApp.getSession().getPlatform()!!.getDetail("m0_case_short")!!
        val field1 = detail.getFields()[0]
        assertTrue(field1.getShowBorder())
    }

    @Test
    fun testDetailWithShading() {
        val detail = mApp.getSession().getPlatform()!!.getDetail("m0_case_short")!!
        val field1 = detail.getFields()[0]
        assertTrue(field1.getShowShading())
    }

    @Test
    fun testDetailPerformanceAttributes() {
        val detail = mApp.getSession().getPlatform()!!.getDetail("m0_case_short")!!
        assertTrue(detail.isLazyLoading)
        assertTrue(detail.isCacheEnabled)
        assertTrue(detail.getFields()[0].isCacheEnabled)
        assertFalse(detail.getFields()[0].isLazyLoading)
        assertTrue(detail.getFields()[1].isLazyLoading)
        assertFalse(detail.getFields()[1].isCacheEnabled)

        val detailNoCaching = mApp.getSession().getPlatform()!!.getDetail("m1_case_short")!!
        assertFalse(detailNoCaching.isCacheEnabled)
        assertFalse(detailNoCaching.isCacheEnabled)
    }

    @Test
    fun testDefaultEndpointRelevancy_shouldBeTrue() {
        val endpoint = mApp.getSession().getPlatform()!!.getEndpoint("endpoint_with_no_relevancy")!!
        assertTrue(endpoint.isRespectRelevancy())
    }
}
