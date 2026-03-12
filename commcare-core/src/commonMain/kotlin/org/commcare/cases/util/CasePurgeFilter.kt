package org.commcare.cases.util

import org.commcare.cases.model.Case
import org.commcare.cases.model.CaseIndex
import org.javarosa.core.services.storage.EntityFilter
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.util.DAG
import org.javarosa.core.util.DAG.Edge
import org.javarosa.core.util.DataUtil
import kotlin.jvm.JvmStatic

/**
 * @author ctsims
 */
open class CasePurgeFilter : EntityFilter<Case> {

    companion object {
        /**
         * Owned by the user or a group
         */
        const val STATUS_OWNED = 1

        /**
         * Should be included in the set of cases seen by the user
         */
        const val STATUS_RELEVANT = 2

        /**
         * Isn't precluded from being included in a sync for any reason
         */
        const val STATUS_AVAILABLE = 4

        /**
         * Should remain on the phone.
         */
        const val STATUS_ALIVE = 8

        const val STATUS_OPEN = 16

        /**
         * Given the case storage and valid owners in the current environment, produce a case graph of
         * the state on the phone.
         *
         * The Index of each node is its case id guid
         * The Node element for the guide consists of an integer array [STATUS_FLAGS, storageid]
         * storageid is the id of the row of the case in the provided storage
         * STATUS_FLAGS is a bitmasked integer. It should be initialized with any of STATUS_OWNED,
         * STATUS_OPEN, and/or STATUS_RELEVANT as applies to the node (A node starts as relevant
         * if it is OWNED and it is OPEN)
         *
         * The Edge string is the 'type' of index from the originating case to the target case
         * (ie: parent, or extension)
         */
        @JvmStatic
        fun getFullCaseGraph(
            caseStorage: IStorageUtilityIndexed<Case>,
            owners: ArrayList<String>?
        ): DAG<String, IntArray, String> {
            val caseGraph = DAG<String, IntArray, String>()
            val indexHolder = ArrayList<CaseIndex>()

            // Pass 1: Create a DAG which contains all of the cases on the phone as nodes, and has a
            // directed edge for each index (from the 'child' case pointing to the 'parent' case) with
            // the appropriate relationship tagged
            val i = caseStorage.iterate()
            while (i.hasMore()) {
                val c = i.nextRecord()
                val owned = if (owners != null) owners.contains(c.getUserId()) else true

                // In order to deal with multiple indices pointing to the same case with different
                // relationships, we'll need to traverse once to eliminate any ambiguity
                // TODO: How do we speed this up? Do we need to?
                for (index in c.getIndices()) {
                    var toReplace: CaseIndex? = null
                    var skip = false
                    for (existing in indexHolder) {
                        if (existing.getTarget() == index.getTarget()) {
                            if (existing.getRelationship() == CaseIndex.RELATIONSHIP_EXTENSION &&
                                index.getRelationship() != CaseIndex.RELATIONSHIP_EXTENSION
                            ) {
                                toReplace = existing
                            } else {
                                skip = true
                            }
                            break
                        }
                    }
                    if (toReplace != null) {
                        indexHolder.remove(toReplace)
                    }
                    if (!skip) {
                        indexHolder.add(index)
                    }
                }
                var nodeStatus = 0
                if (owned) {
                    nodeStatus = nodeStatus or STATUS_OWNED
                }

                if (!c.isClosed()) {
                    nodeStatus = nodeStatus or STATUS_OPEN
                }

                if (owned && !c.isClosed()) {
                    nodeStatus = nodeStatus or STATUS_RELEVANT
                }

                caseGraph.addNode(c.getCaseId()!!, intArrayOf(nodeStatus, c.getID()))

                for (index in indexHolder) {
                    caseGraph.setEdge(c.getCaseId()!!, index.getTarget()!!, index.getRelationship()!!)
                }
                indexHolder.clear()
            }

            return caseGraph
        }

        private fun propagateRelevance(g: DAG<String, IntArray, String>) {
            propagateMarkToDAG(g, true, STATUS_RELEVANT, STATUS_RELEVANT)
            propagateMarkToDAG(
                g, false, STATUS_RELEVANT, STATUS_RELEVANT,
                CaseIndex.RELATIONSHIP_EXTENSION, false
            )
        }

        private fun propagateAvailabile(g: DAG<String, IntArray, String>) {
            val e = g.getIndices()
            while (e.hasNext()) {
                val index = e.next() as String
                val node = g.getNode(index)
                if (caseStatusIs(node!![0], STATUS_OPEN or STATUS_RELEVANT) &&
                    !hasOutgoingExtension(g, index)
                ) {
                    node[0] = node[0] or STATUS_AVAILABLE
                }
            }
            propagateMarkToDAG(
                g, false, STATUS_AVAILABLE, STATUS_AVAILABLE,
                CaseIndex.RELATIONSHIP_EXTENSION, true
            )
        }

        private fun hasOutgoingExtension(g: DAG<String, IntArray, String>, index: String): Boolean {
            for (edge in g.getChildren(index)) {
                if (edge.e == CaseIndex.RELATIONSHIP_EXTENSION) {
                    return true
                }
            }
            return false
        }

        private fun propagateLive(g: DAG<String, IntArray, String>) {
            val e = g.getIndices()
            while (e.hasNext()) {
                val index = e.next() as String
                val node = g.getNode(index)
                if (caseStatusIs(node!![0], STATUS_OWNED or STATUS_RELEVANT or STATUS_AVAILABLE)) {
                    node[0] = node[0] or STATUS_ALIVE
                }
            }

            // NOTE: There is no currently 'safe' number of times to execute these two propagations that
            // will properly cover the graph, this is a short-term fix for covering the most common
            // cases.
            propagateMarkToDAG(g, true, STATUS_ALIVE, STATUS_ALIVE)
            propagateMarkToDAG(
                g, false, STATUS_ALIVE, STATUS_ALIVE,
                CaseIndex.RELATIONSHIP_EXTENSION, true
            )
            propagateMarkToDAG(g, true, STATUS_ALIVE, STATUS_ALIVE)
            propagateMarkToDAG(
                g, false, STATUS_ALIVE, STATUS_ALIVE,
                CaseIndex.RELATIONSHIP_EXTENSION, true
            )
        }

        private fun propagateMarkToDAG(
            g: DAG<String, IntArray, String>,
            direction: Boolean,
            mask: Int,
            mark: Int
        ) {
            propagateMarkToDAG(g, direction, mask, mark, null, false)
        }

        /**
         * Propagates the provided mark in a chain from all nodes which meet the mask to all of their
         * neighboring nodes, as long as those nodes meet the relationship provided. If the relationship
         * is null, only the mask is checked.
         *
         * @param walkFromSourceToSink If true, start at sources (nodes with only outgoing edges), and walk edges
         *                             from parent to child. If false, start at sinks (nodes with only incoming edges)
         *                             and walk from child to parent
         * @param maskCondition        A mask for what nodes meet the criteria of being marked in the walk.
         * @param markToApply          A new binary flag (or set of flags) to apply to all nodes meeting the criteria
         * @param relationship         If non-null, an additional criteria for whether a node should be marked.
         *                             A node will only be marked if the edge walked to put it on the stack
         *                             meets this criteria.
         */
        private fun propagateMarkToDAG(
            dag: DAG<String, IntArray, String>,
            walkFromSourceToSink: Boolean,
            maskCondition: Int,
            markToApply: Int,
            relationship: String?,
            requireOpenDestination: Boolean
        ) {
            val toProcess: ArrayDeque<String> =
                if (walkFromSourceToSink) dag.getSources() else dag.getSinks()
            while (!toProcess.isEmpty()) {
                // current node
                val index = toProcess.removeLast()
                val node = dag.getNode(index)

                val edgeSet: ArrayList<Edge<String, String>> =
                    if (walkFromSourceToSink) dag.getChildren(index) else dag.getParents(index)

                for (edge in edgeSet) {
                    if (caseStatusIs(node!![0], maskCondition) &&
                        (relationship == null || edge.e == relationship)
                    ) {
                        if (!requireOpenDestination ||
                            caseStatusIs(dag.getNode(edge.i)!![0], STATUS_OPEN)
                        ) {
                            dag.getNode(edge.i)!![0] = dag.getNode(edge.i)!![0] or markToApply
                        }
                    }
                    toProcess.add(edge.i)
                }
            }
        }

        private fun caseStatusIs(status: Int, flag: Int): Boolean {
            return (status and flag) == flag
        }

        private fun flattenVectorOfStrings(v: ArrayList<String>): String {
            val builder = StringBuilder()
            for (caseId in v) {
                builder.append(caseId).append(" ")
            }
            return builder.toString()
        }
    }

    private val idsToRemove = ArrayList<Int>()

    // The index is a string containing the case GUID. The Nodes will be a int array containing
    // [STATUS_FLAGS, storageid]. Edges are a string representing the relationship between the
    // nodes, which is one of the Case Index relationships (IE: parent, extension)
    private var internalCaseDAG: DAG<String, IntArray, String>? = null

    // Flag that gets checked by DataPullTask, in order to report to device logs
    private var invalidEdgesWereRemovedFlag: Boolean = false

    // List of case ids for cases that were indexed and expected to be on the phone, but were
    // actually not present
    private val missingCases = ArrayList<String>()

    // List of case ids for cases that were deleted off of the device as a result missing cases
    private val casesRemovedDueToMissingCases = ArrayList<String>()

    @Throws(InvalidCaseGraphException::class)
    constructor(caseStorage: IStorageUtilityIndexed<Case>) : this(caseStorage, null)

    /**
     * Create a filter for purging cases which should no longer be on the phone from
     * the database. Identifies liveness appropriately based on index dependencies,
     * along with cases which have no valid owner in the current context.
     *
     * @param caseStorage The storage which is to be cleaned up.
     * @param owners      A list of IDs for users whose cases should still be on the device.
     *                    Any cases which do not have a valid owner will be considered 'closed' when
     *                    determining the purge behavior. Null to not enable
     *                    this behavior
     */
    @Throws(InvalidCaseGraphException::class)
    constructor(caseStorage: IStorageUtilityIndexed<Case>, owners: ArrayList<String>?) :
            this(getFullCaseGraph(caseStorage, owners))

    @Throws(InvalidCaseGraphException::class)
    constructor(graph: DAG<String, IntArray, String>) {
        setIdsToRemoveWithNewExtensions(graph)
    }

    @Throws(InvalidCaseGraphException::class)
    private fun setIdsToRemoveWithNewExtensions(graph: DAG<String, IntArray, String>) {
        internalCaseDAG = graph

        // It is important that actual edge removal be done after the call to getInvalidEdges() is
        // complete, to prevent a ConcurrentModificationException
        val edgesToRemove = getInvalidEdges()
        for (edge in edgesToRemove) {
            internalCaseDAG!!.removeEdge(edge[0], edge[1])
        }

        if (internalCaseDAG!!.containsCycle()) {
            throw InvalidCaseGraphException("Invalid data sandbox, cycle detected")
        }

        propagateRelevance(internalCaseDAG!!)
        propagateAvailabile(internalCaseDAG!!)
        propagateLive(internalCaseDAG!!)

        // Ok, so now just go through all nodes and signal that we need to remove anything
        // that isn't live!
        val iterator = internalCaseDAG!!.getNodes()
        while (iterator.hasNext()) {
            val node = iterator.next().value as IntArray
            if (!caseStatusIs(node[0], STATUS_ALIVE)) {
                idsToRemove.add(node[1])
            }
        }
    }

    /**
     * Traverse the graph to accumulate a list of any edges to empty nodes (which are created when
     * a child makes a placeholder index to a parent, but then the parent does not actually exist
     * on the phone for some reason).
     *
     * Then remove any nodes that are made invalid by that parent node not existing, which further
     * accumulates the list of invalid edges
     *
     * @return Whether or not this method invocation removed any invalid edges from the DAG
     */
    private fun getInvalidEdges(): ArrayList<Array<String>> {
        val allEdges = internalCaseDAG!!.getEdges()
        val childOfNonexistentParent = ArrayList<String>()
        val edgesToRemove = ArrayList<Array<String>>()
        val edgeOriginIndices = allEdges.keys.iterator()
        while (edgeOriginIndices.hasNext()) {
            val originIndex = edgeOriginIndices.next() as String
            val edgeListForOrigin = allEdges[originIndex]!!
            for (edge in edgeListForOrigin) {
                val targetIndex = edge.i
                if (internalCaseDAG!!.getNode(targetIndex) == null) {
                    missingCases.add(targetIndex)
                    edgesToRemove.add(arrayOf(originIndex, targetIndex))
                    childOfNonexistentParent.add(originIndex)
                }
            }
        }

        // Any case node with a nonexistent parent should be removed from the graph, as should
        // all of its descendants, and any edges to or from it
        for (index in childOfNonexistentParent) {
            removeNodeAndPropagate(index, edgesToRemove)
        }

        invalidEdgesWereRemovedFlag = edgesToRemove.size > 0
        return edgesToRemove
    }

    /**
     * Remove from the graph the node at this index, and remove all nodes that are made
     * invalid by the non-existence of that node (i.e. all of its child and extension cases).
     * Also accumulate a list of edges that need to be removed as a result of this method call.
     * Actual edge removal should then be done all at once by the caller, to avoid
     * ConcurrentModificationException
     */
    private fun removeNodeAndPropagate(
        indexOfRemovedNode: String,
        accumulatedEdgesToRemove: ArrayList<Array<String>>
    ) {
        // Wording is confusing here -- because all edges in this graph are from a child case to
        // a parent case, calling getParents() for a node returns all of its child/extension cases
        val childCases = internalCaseDAG!!.getParents(indexOfRemovedNode)
        for (child in childCases) {
            // Want to remove the edge from child case to parent case
            accumulatedEdgesToRemove.add(arrayOf(child.i, indexOfRemovedNode))

            // Recurse on child case
            removeNodeAndPropagate(child.i, accumulatedEdgesToRemove)
        }

        // Also want to remove any outgoing edges to other parents (but do NOT delete those
        // parents because they are still valid)
        val parentCases = internalCaseDAG!!.getChildren(indexOfRemovedNode)
        for (parent in parentCases) {
            accumulatedEdgesToRemove.add(arrayOf(indexOfRemovedNode, parent.i))
        }

        // Once all edges to/from this node have been removed, delete the node itself from the
        // DAG, and add it to the list of cases to be purged
        if (casesRemovedDueToMissingCases.indexOf(indexOfRemovedNode) == -1) {
            val storageIdOfRemovedNode = internalCaseDAG!!.removeNode(indexOfRemovedNode)!![1]
            idsToRemove.add(storageIdOfRemovedNode)
            casesRemovedDueToMissingCases.add(indexOfRemovedNode)
        }
    }

    // For use in tests
    val internalCaseGraph: DAG<String, IntArray, String>?
        get() = internalCaseDAG

    /**
     * When the underlying case DAG for this case purge filter was created, were there any invalid
     * edges (i.e. edges to non-existent nodes) that had to be removed?
     */
    fun invalidEdgesWereRemoved(): Boolean {
        return this.invalidEdgesWereRemovedFlag
    }

    val missingCasesString: String
        get() = flattenVectorOfStrings(this.missingCases)

    val removedCasesString: String
        get() = flattenVectorOfStrings(this.casesRemovedDueToMissingCases)

    override fun preFilter(id: Int, metaData: HashMap<String, Any>?): Int {
        return if (idsToRemove.contains(DataUtil.integer(id))) {
            PREFILTER_INCLUDE
        } else {
            PREFILTER_EXCLUDE
        }
    }

    override fun matches(e: Case): Boolean {
        // We're doing everything with pre-filtering
        return false
    }

    val casesToRemove: ArrayList<Int>
        get() = idsToRemove
}
