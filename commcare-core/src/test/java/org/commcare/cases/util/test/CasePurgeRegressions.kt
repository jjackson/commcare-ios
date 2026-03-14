package org.commcare.cases.util.test

import org.commcare.cases.model.Case
import org.commcare.cases.util.CasePurgeFilter
import org.commcare.cases.util.InvalidCaseGraphException
import org.commcare.core.parse.ParseUtils
import org.commcare.core.sandbox.SandboxUtils
import org.commcare.util.mocks.MockDataUtils
import org.commcare.util.mocks.MockUserDataSandbox
import org.javarosa.core.services.storage.IStorageIterator
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.util.DAG
import org.junit.Assert
import org.junit.Test
import java.util.Arrays

/**
 * Quick test to be able to restore a set of user data
 * and ensure users and groups are properly being included
 * in case purges.
 *
 * @author ctsims
 */
class CasePurgeRegressions {

    @Test
    @Throws(Exception::class)
    fun testSimpleExtensions() {
        val sandbox = MockDataUtils.getStaticStorage()
        ParseUtils.parseIntoSandbox(javaClass.classLoader
                .getResourceAsStream("case_purge/simple_extension_test.xml"), sandbox)
        val owners = SandboxUtils.extractEntityOwners(sandbox)

        val purger = CasePurgeFilter(sandbox.getCaseStorage(), owners)
        val removedCases = sandbox.getCaseStorage().removeAll(purger).size

        if (removedCases > 0) {
            throw RuntimeException("Incorrectly removed cases")
        }
    }

    /**
     * Test correct validation of a graph where 1 case indexes a non-existent node
     */
    @Test
    @Throws(Exception::class)
    fun testValidateCaseGraphBeforePurge_simple() {
        val sandbox = MockDataUtils.getStaticStorage()
        ParseUtils.parseIntoSandbox(javaClass.classLoader
                .getResourceAsStream("case_purge/validate_case_graph_test_simple.xml"), sandbox)
        val storage = sandbox.getCaseStorage()

        val caseIdsToRecordIds = createCaseIdsMap(storage)
        val filter = CasePurgeFilter(storage)

        val nodesExpectedToBeLeft = hashSetOf("case_one", "case_two")

        val edgesExpectedToBeLeft = hashSetOf(arrayOf("case_two", "case_one"))

        // Check that the edges and nodes still present in the graph are as expected
        val internalCaseGraph = filter.internalCaseGraph!!
        checkProperNodesPresent(nodesExpectedToBeLeft, internalCaseGraph)
        checkProperEdgesPresent(edgesExpectedToBeLeft, internalCaseGraph)

        // Check that the correct cases were actually purged
        val expectedToRemove = arrayListOf(caseIdsToRecordIds["case_three"]!!)
        val removed = storage.removeAll(filter)
        checkProperCasesRemoved(expectedToRemove, removed)
    }

    /**
     * Test correct validation of a graph where 2 different cases index the same non-existent node,
     * and both of those cases have child nodes
     */
    @Test
    @Throws(Exception::class)
    fun testValidateCaseGraphBeforePurge_complex() {
        val sandbox = MockDataUtils.getStaticStorage()
        ParseUtils.parseIntoSandbox(javaClass.classLoader
                .getResourceAsStream("case_purge/validate_case_graph_test_complex.xml"), sandbox)
        val storage = sandbox.getCaseStorage()

        val caseIdsToRecordIds = createCaseIdsMap(storage)
        val filter = CasePurgeFilter(storage)

        val nodesExpectedToBeLeft = hashSetOf("case_one", "case_two")

        val edgesExpectedToBeLeft = hashSetOf(arrayOf("case_two", "case_one"))

        // Check that the edges and nodes still present in the graph are as expected
        val internalCaseGraph = filter.internalCaseGraph!!
        checkProperNodesPresent(nodesExpectedToBeLeft, internalCaseGraph)
        checkProperEdgesPresent(edgesExpectedToBeLeft, internalCaseGraph)

        // Check that the correct cases were actually purged
        val expectedToRemove = arrayListOf(
                caseIdsToRecordIds["case_three"]!!,
                caseIdsToRecordIds["case_four"]!!,
                caseIdsToRecordIds["case_five"]!!,
                caseIdsToRecordIds["case_six"]!!,
                caseIdsToRecordIds["case_seven"]!!
        )
        val removed = storage.removeAll(filter)
        checkProperCasesRemoved(expectedToRemove, removed)
    }

    /**
     * Test correct validation of a graph where 1 case indexes 2 other cases - 1 valid and 1 that
     * does not exist
     */
    @Test
    @Throws(Exception::class)
    fun testValidateCaseGraphBeforePurge_multipleParents() {
        val sandbox = MockDataUtils.getStaticStorage()
        ParseUtils.parseIntoSandbox(javaClass.classLoader
                .getResourceAsStream("case_purge/validate_case_graph_test_multiple_parents.xml"),
                sandbox)
        val storage = sandbox.getCaseStorage()

        val caseIdsToRecordIds = createCaseIdsMap(storage)
        val filter = CasePurgeFilter(storage)

        val nodesExpectedToBeLeft = hashSetOf("case_two", "case_three")

        val edgesExpectedToBeLeft = hashSetOf(arrayOf("case_three", "case_two"))

        // Check that the edges and nodes still present in the graph are as expected
        val internalCaseGraph = filter.internalCaseGraph!!
        checkProperNodesPresent(nodesExpectedToBeLeft, internalCaseGraph)
        checkProperEdgesPresent(edgesExpectedToBeLeft, internalCaseGraph)

        // Check that the correct cases were actually purged
        val expectedToRemove = arrayListOf(
                caseIdsToRecordIds["case_one"]!!,
                caseIdsToRecordIds["case_four"]!!
        )
        val removed = storage.removeAll(filter)
        checkProperCasesRemoved(expectedToRemove, removed)
    }

    /**
     * Test correct validation of a graph where no cases index a non-existent node, so there is no
     * change
     */
    @Test
    @Throws(Exception::class)
    fun testValidateCaseGraphBeforePurge_noRemoval() {
        val sandbox = MockDataUtils.getStaticStorage()
        ParseUtils.parseIntoSandbox(javaClass.classLoader
                .getResourceAsStream("case_purge/validate_case_graph_test_no_change.xml"), sandbox)
        val storage = sandbox.getCaseStorage()

        val filter = CasePurgeFilter(storage)

        val nodesExpectedToBeLeft = hashSetOf("case_one", "case_two", "case_three", "case_four")

        val edgesExpectedToBeLeft = hashSetOf(arrayOf("case_two", "case_one"))

        // Check that the edges and nodes still present in the graph are as expected
        val internalCaseGraph = filter.internalCaseGraph!!
        checkProperNodesPresent(nodesExpectedToBeLeft, internalCaseGraph)
        checkProperEdgesPresent(edgesExpectedToBeLeft, internalCaseGraph)

        // Check that the correct cases (none in this case) were actually purged
        val expectedToRemove = arrayListOf<Int>()
        val removed = storage.removeAll(filter)
        checkProperCasesRemoved(expectedToRemove, removed)
    }

    @Test(expected = InvalidCaseGraphException::class)
    @Throws(Exception::class)
    fun testCyclicGraphThrowsException() {
        val sandbox = MockDataUtils.getStaticStorage()
        ParseUtils.parseIntoSandbox(javaClass.classLoader
                .getResourceAsStream("case_purge/cyclic_case_relationship_test.xml"), sandbox)
        val storage = sandbox.getCaseStorage()
        CasePurgeFilter(storage)
    }

    companion object {
        /**
         * For all cases in this storage object, create a mapping from the case id to its record id,
         * so that we can later test that the correct record ids were removed
         */
        private fun createCaseIdsMap(storage: IStorageUtilityIndexed<Case>): HashMap<String, Int> {
            val iterator = storage.iterate()
            val caseIdsToRecordIds = HashMap<String, Int>()
            while (iterator.hasMore()) {
                val c = iterator.nextRecord()
                caseIdsToRecordIds[c.getCaseId()!!] = c.getID()
            }
            return caseIdsToRecordIds
        }

        /**
         * Check that the set of nodes we expect to still be in the case DAG is identical to the
         * nodes actually there
         */
        private fun checkProperNodesPresent(nodesExpected: Set<String>,
                                            graph: DAG<String, IntArray, String>) {
            val nodesActuallyLeft = getSimpleFormNodes(graph.getIndices())
            Assert.assertTrue(nodesExpected == nodesActuallyLeft)
        }

        /**
         * Check that the set of edges we expect to still be in the case DAG is identical to the
         * edges actually there
         */
        private fun checkProperEdgesPresent(edgesExpected: Set<Array<String>>,
                                            graph: DAG<String, IntArray, String>) {
            val edgesActuallyLeft = getSimpleFormEdges(graph.getEdges())
            for (expected in edgesExpected) {
                Assert.assertTrue(checkContainsThisEdge(edgesActuallyLeft, expected))
            }
            for (actual in edgesActuallyLeft) {
                Assert.assertTrue(checkContainsThisEdge(edgesExpected, actual))
            }
        }

        private fun checkProperCasesRemoved(expectedToRemove: ArrayList<Int>,
                                            removed: ArrayList<Int>) {
            // Check that the 2 vectors are same size
            Assert.assertTrue(removed.size == expectedToRemove.size)

            // Check that every element in expectedToRemove is also in removed
            for (caseId in expectedToRemove) {
                removed.remove(caseId)
            }

            // Check that the removed vector is empty now that all elements from expectedToRemove
            // were removed
            Assert.assertTrue(removed.size == 0)
        }

        /**
         * Helper method for testing that a set of String[] contains the given String[], based upon
         * content value equality rather than reference equality
         */
        private fun checkContainsThisEdge(setOfEdges: Set<Array<String>>, edgeToFind: Array<String>): Boolean {
            for (edge in setOfEdges) {
                if (Arrays.equals(edge, edgeToFind)) {
                    return true
                }
            }
            return false
        }

        private fun getSimpleFormEdges(
                edges: HashMap<String, ArrayList<DAG.Edge<String, String>>>): Set<Array<String>> {
            val simpleFormEdges = HashSet<Array<String>>()
            for (sourceIndex in edges.keys) {
                val edgesFromSource = edges[sourceIndex]!!
                for (edge in edgesFromSource) {
                    simpleFormEdges.add(arrayOf(sourceIndex, edge.i))
                }
            }
            return simpleFormEdges
        }

        private fun getSimpleFormNodes(e: Iterator<*>): Set<String> {
            val simpleFormNodes = HashSet<String>()
            while (e.hasNext()) {
                simpleFormNodes.add(e.next() as String)
            }
            return simpleFormNodes
        }
    }
}
