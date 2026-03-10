package org.javarosa.core.util

import java.util.HashSet
import java.util.LinkedList

/**
 * Directed A-cyclic (NOT ENFORCED) graph datatype.
 *
 * Genericized with three types: An unique index value (representing the node), a generic
 * set of data to associate with that node, and a piece of data to associate with each
 * edge
 *
 * @author ctsims
 */
class DAG<I, N, E> {
    // TODO: This is a really unsafe datatype. Needs an absurd amount of updating for representation
    // invariance, synchronicity, cycle detection, etc.

    private val nodes: HashMap<I, N> = HashMap()
    private val edges: HashMap<I, ArrayList<Edge<I, E>>> = HashMap()
    private val inverseEdges: HashMap<I, ArrayList<Edge<I, E>>> = HashMap()

    fun addNode(i: I, n: N) {
        nodes[i] = n
    }

    fun removeNode(i: I): N? {
        return nodes.remove(i)
    }

    /**
     * Connect Source -> Destination
     */
    fun setEdge(source: I, destination: I, edgeData: E) {
        addToEdges(edges, source, destination, edgeData)
        addToEdges(inverseEdges, destination, source, edgeData)
    }

    private fun addToEdges(
        edgeList: HashMap<I, ArrayList<Edge<I, E>>>,
        source: I,
        dest: I,
        edgeData: E
    ) {
        val edge: ArrayList<Edge<I, E>> = if (edgeList.containsKey(source)) {
            edgeList[source]!!
        } else {
            ArrayList()
        }
        edge.add(Edge(dest, edgeData))
        edgeList[source] = edge
    }

    /**
     * Removes the given edge from both edge lists
     */
    fun removeEdge(sourceIndex: I, destinationIndex: I) {
        removeEdge(edges, sourceIndex, destinationIndex)
        removeEdge(inverseEdges, destinationIndex, sourceIndex)
    }

    /**
     * If an edge from sourceIndex to destinationIndex exists in the given edge list, remove it
     */
    private fun removeEdge(
        edgeList: HashMap<I, ArrayList<Edge<I, E>>>,
        sourceIndex: I,
        destinationIndex: I
    ) {
        val edgesFromSource = edgeList[sourceIndex]
        if (edgesFromSource != null) {
            for (edge in edgesFromSource) {
                if (edge.i == destinationIndex) {
                    // Remove the edge
                    edgesFromSource.remove(edge)

                    // If removing this edge has made it such that this source index no longer has
                    // any edges from it, remove that entire index from the edges hashtable
                    if (edgesFromSource.size == 0) {
                        edgeList.remove(sourceIndex)
                    }

                    return
                }
            }
        }
    }

    fun getParents(index: I): ArrayList<Edge<I, E>> {
        return if (inverseEdges.containsKey(index)) {
            inverseEdges[index]!!
        } else {
            ArrayList()
        }
    }

    fun getChildren(index: I): ArrayList<Edge<I, E>> {
        return if (!edges.containsKey(index)) {
            ArrayList()
        } else {
            edges[index]!!
        }
    }

    fun getNode(index: I): N? {
        return nodes[index]
    }

    /**
     * @return Indices for all nodes in the graph which are not the target of
     * any edges in the graph
     */
    fun getSources(): ArrayDeque<I> {
        val sources = ArrayDeque<I>()
        val en: MutableIterator<I> = nodes.keys.iterator()
        while (en.hasNext()) {
            val i = en.next()
            if (!inverseEdges.containsKey(i)) {
                sources.add(i)
            }
        }
        return sources
    }

    /**
     * @return Indices for all nodes that do not have any outgoing edges
     */
    fun getSinks(): ArrayDeque<I> {
        val roots = ArrayDeque<I>()
        val en: MutableIterator<I> = nodes.keys.iterator()
        while (en.hasNext()) {
            val i = en.next()
            if (!edges.containsKey(i)) {
                roots.add(i)
            }
        }
        return roots
    }

    /**
     * Find all nodes reachable from the given set of source nodes
     *
     * @param sourceNodes The set of starting nodes
     * @return A set containing all reachable nodes
     */
    fun findConnectedRecords(sourceNodes: Set<I>): Set<I> {
        val visited = HashSet<I>()
        val queue = LinkedList(sourceNodes)
        while (!queue.isEmpty()) {
            val current = queue.poll()
            if (visited.contains(current)) {
                continue
            }
            visited.add(current)
            enqueueUnvisitedNeighbors(edges, current, queue, visited)
            enqueueUnvisitedNeighbors(inverseEdges, current, queue, visited)
        }
        return visited
    }

    // Adds unvisited neighboring nodes of the given record to the queue for further traversal
    private fun enqueueUnvisitedNeighbors(
        edges: HashMap<I, ArrayList<Edge<I, E>>>,
        current: I,
        queue: LinkedList<I>,
        visited: Set<I>
    ) {
        if (edges.containsKey(current)) {
            val neighbors = edges[current]!!
            for (neighbor in neighbors) {
                if (!visited.contains(neighbor.i)) {
                    queue.add(neighbor.i)
                }
            }
        }
    }

    class Edge<I, E>(
        @JvmField val i: I,
        @JvmField val e: E
    )

    fun getNodes(): MutableIterator<MutableMap.MutableEntry<I, N>> {
        return nodes.iterator()
    }

    fun getNodesCount(): Int {
        return nodes.size
    }

    fun getIndices(): MutableIterator<I> {
        return nodes.keys.iterator()
    }

    fun getEdges(): HashMap<I, ArrayList<Edge<I, E>>> {
        return this.edges
    }

    fun containsCycle(): Boolean {
        val visited = HashSet<I>()
        val currentPath = HashSet<I>()

        for (i in nodes.keys) {
            if (nodePathContainsCycle(i, visited, currentPath)) {
                return true
            }
        }
        return false
    }

    private fun nodePathContainsCycle(i: I, visited: HashSet<I>, currentPath: HashSet<I>): Boolean {
        if (!visited.contains(i)) {
            visited.add(i)
            currentPath.add(i)

            if (edges.containsKey(i)) {
                for (e in edges[i]!!) {
                    if (!visited.contains(e.i) && nodePathContainsCycle(e.i, visited, currentPath)) {
                        return true
                    } else if (currentPath.contains(e.i)) {
                        return true
                    }
                }
            }
        }
        currentPath.remove(i)
        return false
    }
}
