package org.commcare.test.utilities

import org.commcare.cases.model.Case
import org.commcare.cases.model.CaseIndex
import org.commcare.cases.util.CasePurgeFilter
import org.commcare.cases.util.CasePurgeFilter.Companion.getFullCaseGraph
import org.commcare.cases.util.InvalidCaseGraphException
import org.javarosa.core.services.storage.util.DummyIndexedStorageUtility
import org.javarosa.core.util.externalizable.LivePrototypeFactory
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.IOException

/**
 * A class for running tests on the case purge logic.
 *
 * Reads external JSON documents containing preconditions setting up case logic, and then
 * validates that the resulting case database is consistent with the purge/sync logic.
 *
 * Created by ctsims on 10/13/2015.
 */
@RunWith(Parameterized::class)
open class CasePurgeTest(
    private val name: String,
    cases: JSONArray?,
    owned: JSONArray?,
    closed: JSONArray?,
    subcases: JSONArray?,
    extensions: JSONArray?,
    outcome: JSONArray?,
    relationOutcomes: JSONArray?
) {

    private val cases = HashSet<String>()
    private val ownedCases = HashSet<String>()
    private val closedCases = HashSet<String>()
    private val outcomeSet = HashSet<String>()
    private val relationOutcomeSet = HashMap<String, HashSet<String>>()
    private val indices = ArrayList<Array<String>>()

    init {
        createTestObjectsFromParameters(cases, owned, closed, subcases, extensions, outcome, relationOutcomes)
    }

    private fun createTestObjectsFromParameters(
        casesJson: JSONArray?,
        ownedJson: JSONArray?,
        closedJson: JSONArray?,
        subcasesJson: JSONArray?,
        extensionsJson: JSONArray?,
        outcomeJson: JSONArray?,
        relationOutcomes: JSONArray?
    ) {
        if (casesJson != null) {
            getCases(casesJson, cases)
        }
        if (ownedJson != null) {
            getCases(ownedJson, ownedCases)
        }
        if (closedJson != null) {
            getCases(closedJson, closedCases)
        }

        if (subcasesJson != null) {
            getIndices(subcasesJson, indices, CaseIndex.RELATIONSHIP_CHILD)
        }
        if (extensionsJson != null) {
            getIndices(extensionsJson, indices, CaseIndex.RELATIONSHIP_EXTENSION)
        }
        if (outcomeJson != null) {
            getCases(outcomeJson, outcomeSet)
        }
        if (relationOutcomes != null) {
            populateRelationOutcomes(relationOutcomes, outcomeSet)
        }
    }

    private fun populateRelationOutcomes(relationOutcomes: JSONArray, outcomeSet: HashSet<String>) {
        var count = 0
        for (outcome in outcomeSet) {
            val relationOutcome = relationOutcomes.get(count++) as JSONObject
            val relatedCases = relationOutcome.optJSONArray("related_cases")
            val relatedCasesSet = HashSet<String>()
            for (i in 0 until relatedCases.length()) {
                relatedCasesSet.add(relatedCases.getString(i))
            }
            relationOutcomeSet[outcome] = relatedCasesSet
        }
    }

    private fun getIndices(indices: JSONArray, indexSet: ArrayList<Array<String>>, indexType: String) {
        for (i in 0 until indices.length()) {
            val index = indices.getJSONArray(i)
            val c = index.getString(0)
            val target = index.getString(1)
            cases.add(c)
            cases.add(target)
            indexSet.add(arrayOf(c, target, indexType))
        }
    }

    private fun getCases(owned: JSONArray, target: HashSet<String>) {
        for (i in 0 until owned.length()) {
            val c = owned.getString(i)
            cases.add(c)
            target.add(c)
        }
    }

    @Test
    @Throws(InvalidCaseGraphException::class)
    fun executeTest() {
        val storage = DummyIndexedStorageUtility<Case>(Case::class.java, LivePrototypeFactory())

        val userId = "user"

        initCaseStorage(storage, userId)

        val ownerIds = ArrayList<String>()
        ownerIds.add(userId)

        storage.removeAll(CasePurgeFilter(getFullCaseGraph(storage, ownerIds)))

        val inStorage = HashSet<String>()
        // redo the graph as we don't want the eliminated cases anymore
        val graph = getFullCaseGraph(storage, ownerIds)
        val iterator = storage.iterate()
        while (iterator.hasMore()) {
            val c = iterator.nextRecord()
            val caseId = c.getCaseId()!!
            inStorage.add(caseId)

            val relatedCasesSet = relationOutcomeSet[caseId]
            val input = HashSet<String>()
            input.add(caseId)
            val relatedCases = graph.findConnectedRecords(input)
            Assert.assertEquals(name, relatedCasesSet, relatedCases)
        }

        Assert.assertEquals(name, outcomeSet, inStorage)
    }

    private fun initCaseStorage(storage: DummyIndexedStorageUtility<Case>, userId: String) {
        for (c in cases) {
            val theCase = Case(c, "purge_test_case")
            theCase.setCaseId(c)
            if (ownedCases.contains(c)) {
                theCase.setUserId(userId)
            }
            if (closedCases.contains(c)) {
                theCase.setClosed(true)
            }
            storage.write(theCase)
        }

        for (index in indices) {
            val theCase = storage.getRecordForValue(Case.INDEX_CASE_ID, index[0])
            val caseIndex = CaseIndex(
                index[0] + index[1] + index[2],
                "purge_test_case", index[1], index[2]
            )
            theCase.setIndex(caseIndex)
            storage.write(theCase)
        }
    }

    fun getName(): String = name

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun testData(): Iterable<Array<Any?>> {
            try {
                val fullTestResource = JSONArray(TestHelpers.getResourceAsString("/case_relationship_tests.json"))
                val listOfParameterSets = ArrayList<Array<Any?>>()
                for (i in 0 until fullTestResource.length()) {
                    val root = fullTestResource.getJSONObject(i)
                    listOfParameterSets.add(parseParametersFromJSONObject(root))
                }
                return listOfParameterSets
            } catch (e: Exception) {
                val failure = RuntimeException("Failed to parse input for CasePurgeTest")
                failure.initCause(e)
                throw failure
            }
        }

        private fun parseParametersFromJSONObject(root: JSONObject): Array<Any?> {
            val parameters = arrayOfNulls<Any>(8)
            parameters[0] = root.getString("name")

            val jsonArrayKeys = arrayOf("cases", "owned", "closed", "subcases", "extensions", "outcome", "relation_outcome")
            for (i in jsonArrayKeys.indices) {
                addJSONArrayIfPresent(root, i + 1, jsonArrayKeys[i], parameters)
            }

            return parameters
        }

        private fun addJSONArrayIfPresent(
            root: JSONObject, index: Int, key: String,
            parameterSet: Array<Any?>
        ) {
            if (root.has(key)) {
                parameterSet[index] = root.getJSONArray(key)
            }
        }
    }
}
