package org.commcare.backend.suite.model.test

import org.cli.MockSessionUtils
import org.commcare.core.encryption.CryptUtil
import org.commcare.core.interfaces.MemoryVirtualDataInstanceStorage
import org.commcare.data.xml.VirtualInstances
import org.commcare.modern.session.SessionWrapper
import org.commcare.suite.model.RemoteQueryDatum
import org.commcare.test.utilities.CaseTestUtils
import org.commcare.test.utilities.MockApp
import org.commcare.util.screen.QueryScreen
import org.javarosa.core.model.instance.ExternalDataInstance
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Basic test for remote query as part of form entry session
 */
class QueryModelTests {

    val virtualDataInstanceStorage = MemoryVirtualDataInstanceStorage()

    @Before
    fun setUp() {
        virtualDataInstanceStorage.clear()
    }

    @Test
    fun testQueryEntryDatum() {
        val mApp = MockApp("/case_claim_example/")
        val session = mApp.getSession()
        val screen = setupQueryScreen(session)

        // perform the query
        val success = screen.handleInputAndUpdateSession(session, "bob,23", false, null, true)
        assertTrue(success)

        // check that session datum requirement is satisfied
        assertNull(session.getNeededDatum())
    }

    @Test
    fun testScreenCreatesVirtualInstance() {
        val mApp = MockApp("/case_claim_example/")
        val session = mApp.getSession()
        val screen = setupQueryScreen(session)

        val refId = "registry1"
        val instanceID = VirtualInstances.makeSearchInputInstanceID(refId)
        val expectedInstanceStorageKey = CryptUtil.sha256(instanceID + "/name=bob|age=23|")
        assertFalse(virtualDataInstanceStorage.contains(expectedInstanceStorageKey))

        // perform the query
        val success = screen.handleInputAndUpdateSession(session, "bob,23", false, null, true)
        assertTrue(success)

        // check that saved instance matches expect what we expect
        assertTrue(virtualDataInstanceStorage.contains(expectedInstanceStorageKey))
        val input = mapOf("name" to "bob", "age" to "23")
        assertEquals(
            VirtualInstances.buildSearchInputInstance(refId, input).getRoot(),
            virtualDataInstanceStorage.read(expectedInstanceStorageKey, instanceID, refId)!!.getRoot()
        )

        CaseTestUtils.xpathEvalAndAssert(
            session.getEvaluationContext(),
            "instance('search-input:registry1')/input/field[@name='age']",
            "23"
        )

        // test loading instance with new ref
        val instance = ExternalDataInstance(
            "jr://instance/search-input/registry1",
            "custom-id"
        )
        assertNotNull(session.getIIF().generateRoot(instance).getRoot())

        // test that we can still load instances using the legacy ref
        val legacyInstance = ExternalDataInstance(
            "jr://instance/search-input",
            "search-input:registry1"
        )
        assertNotNull(session.getIIF().generateRoot(legacyInstance).getRoot())
    }

    private fun setupQueryScreen(session: SessionWrapper): QueryScreen {
        session.setCommand("m0-f0")

        val datum = session.getNeededDatum()!!
        assertTrue(datum is RemoteQueryDatum)
        assertEquals("registry1", datum.getDataId())

        // construct the screen
        // mock the query response
        val response = this.javaClass.getResourceAsStream("/case_claim_example/query_response.xml")
        val screen = QueryScreen(
            "username", "password",
            System.out, virtualDataInstanceStorage, MockSessionUtils(response)
        )
        screen.init(session)
        return screen
    }
}
