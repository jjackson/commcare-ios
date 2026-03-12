package org.javarosa.core.util

import org.commcare.util.CollectionUtils
import org.javarosa.core.model.instance.TreeReference

/**
 * Modeled after algorithm here https://stackoverflow.com/a/549312
 *
 * Given a ArrayList of TreeReference pairs where edges[0] references edges[1]
 * that we assume to contain a cycle, find the smallest cycle in the set.
 *
 * Created by willpride on 11/17/17.
 */
class ShortestCycleAlgorithm(private val edges: ArrayList<Array<TreeReference>>) {
    private val nodes = ArrayList<String>()
    private val childrenMap: MutableMap<String, ArrayList<String>> = OrderedHashtable()
    private var shortestCycle: ArrayList<String>? = null
    private val reachableMap: MutableMap<String, ArrayList<String>> = OrderedHashtable()
    private val walked = ArrayList<String>()

    init {
        for (references in edges) {
            val parentKey = references[1].toString()
            val childKey = references[0].toString()

            addChild(parentKey, childKey)
            if (!nodes.contains(parentKey)) {
                nodes.add(parentKey)
            }
        }

        for (node in nodes) {
            val shortestPath = depthFirstSearch(node, node, ArrayList())
            if (shortestPath != null && (shortestCycle == null || shortestPath.size < shortestCycle!!.size)) {
                shortestCycle = shortestPath
            }
        }
    }

    private fun addChild(parentKey: String, childKey: String) {
        if (!childrenMap.containsKey(parentKey)) {
            childrenMap[parentKey] = ArrayList()
        }
        val childList = childrenMap[parentKey]!!
        if (!childList.contains(childKey)) {
            childList.add(childKey)
        }
    }

    // Add the new node to the set of reachable nodes for all already-visited nodes
    private fun addReachableToVisited(visited: List<String>, reachable: String) {
        for (visit in visited) {
            addReachable(visit, reachable)
        }
    }

    private fun addReachable(parent: String, reachable: String) {
        if (!reachableMap.containsKey(parent)) {
            reachableMap[parent] = ArrayList()
        }
        val reachableList = reachableMap[parent]!!
        if (!reachableList.contains(reachable)) {
            reachableList.add(reachable)
        }
    }

    private fun depthFirstSearch(
        startNode: String,
        currentNode: String,
        visited: ArrayList<String>
    ): ArrayList<String>? {
        addReachableToVisited(visited, currentNode)
        if (visited.contains(currentNode)) {
            if (startNode == currentNode) {
                return visited
            }
            return null
        }

        visited.add(currentNode)
        val children = childrenMap[currentNode]
        if (children != null) {
            for (child in children) {
                // If we have already walked this node, get the set of reachable nodes from that walk
                // If that set does not contain any of the visited nodes in the current walk
                // Then this child cannot contain a cycle
                if (walked.contains(child)) {
                    val reachables = reachableMap[child]
                    if (reachables == null || !CollectionUtils.containsAny(reachables, visited)) {
                        continue
                    }
                }
                val shortestPath = depthFirstSearch(startNode, child, visited)
                if (shortestPath != null) {
                    return shortestPath
                }
            }
        }
        walked.add(currentNode)
        visited.remove(currentNode)
        return null
    }

    fun getCycleErrorMessage(): String {
        return "Logic is cyclical, referencing itself. The following questions are involved: \n" +
                getCycleString()
    }

    fun getCycleString(): String {
        val stringBuilder = StringBuilder()
        for (i in shortestCycle!!.indices) {
            stringBuilder.append(shortestCycle!![i])
            if (i == shortestCycle!!.size - 1) {
                stringBuilder.append(" references ")
                stringBuilder.append(shortestCycle!![0])
                stringBuilder.append(".")
            } else {
                stringBuilder.append(" references ")
                stringBuilder.append(shortestCycle!![i + 1])
                stringBuilder.append(", \n")
            }
        }
        return stringBuilder.toString()
    }

    /**
     * @return a GraphViz Digraph (DOT engine) which will visualize the dependencies between the
     * edges. Helpful for debugging
     */
    @Suppress("unused")
    private fun toDOTDigraph(): String {
        var graph = ""
        for (edge in edges) {
            graph += clean(edge[0].toString(false)) + " -> " + clean(edge[1].toString(false)) + ";\n"
        }

        return "digraph G{\n$graph\n}"
    }

    private fun clean(input: String): String {
        return input.replace("/", "")
            .replace("-", "_")
            .replace("\\(", "")
            .replace("\\)", "")
            .replace("@", "")
    }
}
