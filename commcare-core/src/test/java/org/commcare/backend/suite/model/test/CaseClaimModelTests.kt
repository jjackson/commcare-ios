package org.commcare.backend.suite.model.test

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.commcare.data.xml.SimpleNode
import org.commcare.data.xml.TreeBuilder
import org.commcare.data.xml.VirtualInstances
import org.commcare.session.RemoteQuerySessionManager
import org.commcare.suite.model.QueryPrompt
import org.commcare.suite.model.QueryPrompt.Companion.DEFAULT_VALIDATION_ERROR
import org.commcare.suite.model.RemoteQueryDatum
import org.commcare.test.utilities.MockApp
import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.util.ListMultimap
import org.javarosa.xpath.XPathMissingInstanceException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.FunctionUtils
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for basic app models for case claim
 *
 * @author ctsims
 */
class CaseClaimModelTests {

    private var mApp: MockApp? = null

    @Test
    fun testRemoteQueryDatum() {
        val mApp = MockApp("/case_claim_example/")

        val session = mApp.getSession()
        session.setCommand("patient-search")

        val datum = session.getNeededDatum()

        assertTrue("Didn't find Remote Query datum definition", datum is RemoteQueryDatum)

        val title = (datum as RemoteQueryDatum).getTitleText()
        assertEquals("Title Label", title!!.evaluate())

        val description = datum.getDescriptionText()
        assertEquals("Description text", description!!.evaluate())

        assertTrue(datum.getDynamicSearch())

        assertTrue(datum.isSearchOnClear())
    }

    @Test
    fun testPopulateItemsetChoices__inputReference() {
        val remoteQuerySessionManager = testPopulateItemsetChoices(
            ImmutableMap.of("state", "ka"), ImmutableList.of("bang"), null
        )

        // test updating input updates the dependent itemset
        testPopulateItemsetChoices(
            ImmutableMap.of("state", "rj"), ImmutableList.of("kota"), remoteQuerySessionManager
        )
    }

    @Test
    fun testPopulateItemsetChoices__emptyInput() {
        testPopulateItemsetChoices(emptyMap(), emptyList(), null)
    }

    private fun testPopulateItemsetChoices(
        userInput: Map<String, String>,
        expected: List<String>,
        existingQuerySessionManager: RemoteQuerySessionManager?
    ): RemoteQuerySessionManager {
        val remoteQuerySessionManager =
            existingQuerySessionManager ?: buildRemoteQuerySessionManager()

        userInput.forEach { (key, value) -> remoteQuerySessionManager.answerUserPrompt(key, value) }

        val inputDisplays = remoteQuerySessionManager.getNeededUserInputDisplays()!!
        val districtPrompt = inputDisplays["district"]!!

        remoteQuerySessionManager.populateItemSetChoices(districtPrompt)
        val choices = districtPrompt.getItemsetBinding()!!.getChoices()!!
            .map { it.value }
        assertEquals(expected, choices)
        return remoteQuerySessionManager
    }

    private fun buildRemoteQuerySessionManager(): RemoteQuerySessionManager {
        mApp = MockApp("/case_claim_example/")

        val session = mApp!!.getSession()
        session.setCommand("patient-search")

        val districtInstance = buildDistrictInstance()
        val stateInstance = buildStateInstance()
        val context = session.getEvaluationContext().spawnWithCleanLifecycle(
            ImmutableMap.of(
                stateInstance.getInstanceId(), stateInstance,
                districtInstance.getInstanceId(), districtInstance
            )
        )

        return RemoteQuerySessionManager.buildQuerySessionManager(
            session, context, ImmutableList.of(QueryPrompt.INPUT_TYPE_SELECT1)
        )!!
    }

    @Test
    fun testRemoteRequestSessionManager_getRawQueryParamsWithUserInput() {
        val remoteQuerySessionManager = testGetRawQueryParamsWithUserInput(
            ImmutableMap.of("patient_id", "123"),
            ImmutableList.of("external_id = 123"),
            "patient_id"
        )

        // test that updating the input results in an updated output
        testGetRawQueryParamsWithUserInput(
            ImmutableMap.of("patient_id", "124"),
            ImmutableList.of("external_id = 124"),
            "patient_id",
            remoteQuerySessionManager
        )
    }

    @Test
    fun testRemoteRequestSessionManager_getRawQueryParamsWithUserInput_missing() {
        testGetRawQueryParamsWithUserInput(emptyMap(), ImmutableList.of(""), "patient_id")
    }

    @Test
    fun testRemoteRequestSessionManager_getRawQueryParamsWithUserInput_legacy() {
        testGetRawQueryParamsWithUserInput(
            ImmutableMap.of("patient_id", "123"),
            ImmutableList.of("external_id = 123"),
            "patient_id_legacy"
        )
    }

    @Test
    fun testRemoteRequestSessionManager_getRawQueryParamsWithUserInput_missing_legacy() {
        testGetRawQueryParamsWithUserInput(emptyMap(), ImmutableList.of(""), "patient_id_legacy")
    }

    @Test
    fun testRemoteRequestSessionManager_getRawQueryParamsWithUserInput_customInstanceId() {
        testGetRawQueryParamsWithUserInput(
            ImmutableMap.of("patient_id", "123"),
            ImmutableList.of("external_id = 123"),
            "patient_id_custom_id"
        )
    }

    /**
     * Test that using 'current()' works with the lazy initialized instances
     */
    @Test
    fun testRemoteRequestSessionManager_getRawQueryParamsWithUserInput_current() {
        val mApp = MockApp("/case_claim_example/")

        val session = mApp.getSession()
        session.setCommand("patient-search")

        val input = ImmutableMap.of("name", "bob", "age", "23")
        val userInputInstance = VirtualInstances.buildSearchInputInstance("patients", input)

        // make sure the evaluation context doesn't get an instance with ID=userInputInstance.instanceID
        // After this there should this instance should be registered under 2 IDs: 'bad-id' and 'my-search-input'
        val instances = ImmutableMap.of("bad-id", userInputInstance)
        val evaluationContext = session.getEvaluationContext().spawnWithCleanLifecycle(instances)

        val xpe = XPathParseTool.parseXPath(
            "count(instance('my-search-input')/input/field[current()/@name = 'name'])"
        )!!
        val result = FunctionUtils.toString(xpe.eval(evaluationContext))
        assertEquals("1", result)

        try {
            val xpe1 = XPathParseTool.parseXPath(
                "count(instance('bad-id')/input/field[current()/@name = 'name'])"
            )!!
            FunctionUtils.toString(xpe1.eval(evaluationContext))
            fail("Expected exception")
        } catch (e: XPathMissingInstanceException) {
            // this fails because we added this instance to the eval context with a different ID ('bad-id')
            assertTrue(e.message!!.contains("search-input:patients"))
        }
    }

    @Test
    fun testRemoteRequestSessionManager_getRawQueryParamsWithExclude() {
        testGetRawQueryParamsWithUserInputExcluded(
            ImmutableMap.of("exclude_patient_id", "123")
        )
    }

    private fun testGetRawQueryParamsWithUserInput(
        userInput: Map<String, String>,
        expected: List<String>,
        key: String
    ): RemoteQuerySessionManager {
        return testGetRawQueryParamsWithUserInput(userInput, expected, key, null)
    }

    private fun testGetRawQueryParamsWithUserInput(
        userInput: Map<String, String>,
        expected: List<String>,
        key: String,
        existingManager: RemoteQuerySessionManager?
    ): RemoteQuerySessionManager {
        val remoteQuerySessionManager =
            existingManager ?: buildRemoteQuerySessionManager()

        userInput.forEach { (k, v) -> remoteQuerySessionManager.answerUserPrompt(k, v) }

        val params: ListMultimap<String, String> = remoteQuerySessionManager.getRawQueryParams(true)

        assertEquals(expected, params[key])
        return remoteQuerySessionManager
    }

    private fun testGetRawQueryParamsWithUserInputExcluded(userInput: Map<String, String>) {
        val remoteQuerySessionManager = buildRemoteQuerySessionManager()

        userInput.forEach { (k, v) -> remoteQuerySessionManager.answerUserPrompt(k, v) }

        val params: ListMultimap<String, String> = remoteQuerySessionManager.getRawQueryParams(false)

        assertFalse(params.containsKey("exclude_patient_id"))
    }

    private fun buildStateInstance(): ExternalDataInstance {
        val noAttrs = emptyMap<String, String>()
        val nodes = ImmutableList.of(
            SimpleNode.parentNode(
                "state", noAttrs, ImmutableList.of(
                    SimpleNode.textNode("id", noAttrs, "ka"),
                    SimpleNode.textNode("name", noAttrs, "Karnataka")
                )
            ),
            SimpleNode.parentNode(
                "state", noAttrs, ImmutableList.of(
                    SimpleNode.textNode("id", noAttrs, "rj"),
                    SimpleNode.textNode("name", noAttrs, "Rajasthan")
                )
            )
        )

        val root = TreeBuilder.buildTree("state", "state_list", nodes)
        return ExternalDataInstance(ExternalDataInstance.JR_SEARCH_INPUT_REFERENCE, "state", root)
    }

    private fun buildDistrictInstance(): ExternalDataInstance {
        val noAttrs = emptyMap<String, String>()
        val nodes = ImmutableList.of(
            SimpleNode.parentNode(
                "district", noAttrs, ImmutableList.of(
                    SimpleNode.textNode("id", noAttrs, "bang"),
                    SimpleNode.textNode("state_id", noAttrs, "ka"),
                    SimpleNode.textNode("name", noAttrs, "Bangalore")
                )
            ),
            SimpleNode.parentNode(
                "district", noAttrs, ImmutableList.of(
                    SimpleNode.textNode("id", noAttrs, "kota"),
                    SimpleNode.textNode("state_id", noAttrs, "rj"),
                    SimpleNode.textNode("name", noAttrs, "Kota")
                )
            )
        )

        val root = TreeBuilder.buildTree("district", "district_list", nodes)
        return ExternalDataInstance(ExternalDataInstance.JR_SEARCH_INPUT_REFERENCE, "district", root)
    }

    @Test
    fun testErrorsWithUserInput_noInput() {
        testErrorsWithUserInput(
            ImmutableMap.of(),
            ImmutableMap.of("age", "One of age or DOB is required", "dob", "One of age or DOB is required"),
            null
        )
    }

    @Test
    fun testErrorsWithUserInput_EmptyInput() {
        testErrorsWithUserInput(
            ImmutableMap.of("age", "", "another_age", ""),
            ImmutableMap.of("age", "One of age or DOB is required"),
            null
        )
    }

    @Test
    fun testErrorsWithUserInput_errorsClearWithValidInput() {
        val remoteQuerySessionManager = testErrorsWithUserInput(
            ImmutableMap.of("name", "", "age", "15", "another_age", "12"),
            ImmutableMap.of(
                "age", "age should be greater than 18",
                "another_age", DEFAULT_VALIDATION_ERROR
            ),
            null
        )

        testErrorsWithUserInput(
            ImmutableMap.of("name", "Ruth", "age", "21", "another_age", "20"),
            ImmutableMap.of(), remoteQuerySessionManager
        )
    }

    private fun testErrorsWithUserInput(
        userInput: Map<String, String>,
        expectedErrors: Map<String, String>,
        existingManager: RemoteQuerySessionManager?
    ): RemoteQuerySessionManager {
        val remoteQuerySessionManager =
            existingManager ?: buildRemoteQuerySessionManager()

        userInput.forEach { (k, v) -> remoteQuerySessionManager.answerUserPrompt(k, v) }
        remoteQuerySessionManager.refreshInputDependentState()
        val errors = remoteQuerySessionManager.getErrors()

        if (expectedErrors.isEmpty()) {
            assertTrue(errors.isEmpty())
        }

        expectedErrors.forEach { (key, expectedError) ->
            assertEquals(expectedError, errors[key])
        }

        return remoteQuerySessionManager
    }

    @Test
    fun testRequiredWithUserInput_dependentConditions() {
        // when age, dob is not required
        testRequiredWithUserInput(
            ImmutableMap.of("age", "15"),
            ImmutableMap.of("age", true, "dob", false),
            null
        )

        // when dob, age is not required
        testRequiredWithUserInput(
            ImmutableMap.of("dob", "30-02-1000"),
            ImmutableMap.of("age", false, "dob", true),
            null
        )

        // when none, both age and dob is required
        testRequiredWithUserInput(
            ImmutableMap.of(),
            ImmutableMap.of("age", true, "dob", true),
            null
        )
    }

    @Test
    fun testRequiredWithUserInput_oldRequiredSyntax() {
        val remoteQuerySessionManager = testRequiredWithUserInput(
            ImmutableMap.of("age", "15"),
            ImmutableMap.of("name", true),
            null
        )
        val namePrompt = remoteQuerySessionManager.getNeededUserInputDisplays()!!["name"]
        assertEquals(
            QueryPrompt.DEFAULT_REQUIRED_ERROR,
            namePrompt!!.getRequiredMessage(mApp!!.getSession().getEvaluationContext())
        )
    }

    private fun testRequiredWithUserInput(
        userInput: Map<String, String>,
        expectedRequired: Map<String, Boolean>,
        existingManager: RemoteQuerySessionManager?
    ): RemoteQuerySessionManager {
        val remoteQuerySessionManager =
            existingManager ?: buildRemoteQuerySessionManager()

        userInput.forEach { (k, v) -> remoteQuerySessionManager.answerUserPrompt(k, v) }
        remoteQuerySessionManager.refreshInputDependentState()
        val requiredPrompts = remoteQuerySessionManager.getRequiredPrompts()

        if (expectedRequired.isEmpty()) {
            assertTrue(requiredPrompts.isEmpty())
        }

        expectedRequired.forEach { (key, isRequired) ->
            assertEquals(isRequired, requiredPrompts[key])
        }

        return remoteQuerySessionManager
    }
}
