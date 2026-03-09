package org.commcare.cases.query

import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.TreeReference

/**
 * This cache keeps track of the *full* set of TreeReferences which will
 * be dereferenced within the scope of a "limited" query. This can be used
 * by DataInstances to identify that they do not need to load full data models
 * within the scope of a given query, but only those needed to meet the limited
 * query scope.
 *
 * This cache should only get created in a query context which is guaranteed to be
 * transient: IE: After an escalation. Generally speaking you should force a child
 * context to spawn before populating this cache unless you are sure the query context
 * is transient.
 *
 * Created by ctsims on 9/18/2017.
 */
class ScopeLimitedReferenceRequestCache : QueryCache {

    private val DEFAULT_INSTANCE_KEY = "/"

    // Maps instance names to the set of tree references that are "in scope" (i.e. that we must be
    // able to evaluate) for that instance in the current context
    private val instanceNameToReferenceSetMap = HashMap<String, MutableSet<TreeReference>>()

    /**
     * Replaces the tree element cache used to hold the final partial tree elements, since
     * the in-instance cache isn't safe to store those partial elements
     */
    private val treeElementCache = HashMap<String, HashMap<Int, TreeElement>>()

    /**
     * A list of instance names for instances which have flagged that they cannot utilize
     * the limited scope.
     */
    private val excludedInstances = HashSet<String>()

    private val instanceScopeLimitCache = HashMap<String, Array<String>>()

    fun addTreeReferencesToLimitedScope(references: Set<TreeReference>) {
        for (reference in references) {
            val instanceName = reference.instanceName ?: DEFAULT_INSTANCE_KEY

            val existingRefs = instanceNameToReferenceSetMap.getOrPut(instanceName) { HashSet() }
            existingRefs.add(reference)
        }
    }

    /**
     * @return true if an instance has a limited scope to report, and has not explicitly informed
     * the cache that it should be excluded from future requests.
     */
    fun isInstancePotentiallyScopeLimited(instanceName: String): Boolean {
        return instanceNameToReferenceSetMap.containsKey(instanceName) &&
                !excludedInstances.contains(instanceName)
    }

    /**
     * Get all of the in-scope tree references for the provided instance
     */
    fun getInScopeReferences(instanceName: String): Set<TreeReference>? {
        return instanceNameToReferenceSetMap[instanceName]
    }

    /**
     * Signal that the scope limit is unhelpful for the provided instance. Will prevent
     * that instance from being included in future requests to isInstancePotentiallyScopeLimited
     */
    fun setScopeLimitUnhelpful(instanceName: String) {
        excludedInstances.add(instanceName)
    }

    fun getCachedElementIfExists(instanceName: String, recordId: Int): TreeElement? {
        val instanceCache = treeElementCache[instanceName] ?: return null
        return instanceCache[recordId]
    }

    fun cacheElement(instanceName: String, recordId: Int, element: TreeElement) {
        treeElementCache[instanceName]?.put(recordId, element)
    }

    /**
     * Provide a cue from within an instance that it has determined a payload that it can use to
     * process requests from limited scope.
     *
     * This signal is a guarantee that the instance will be able to dereference the
     * tree references it is responsible for in the limited scope using a partial load rather
     * than a full model load.
     */
    fun setInternalScopeLimit(instanceName: String, columnNameCacheLoads: Array<String>) {
        this.instanceScopeLimitCache[instanceName] = columnNameCacheLoads
        treeElementCache[instanceName] = HashMap()
    }

    /**
     * If the cache has already been provided with a payload for this limited scope request,
     * return it. Otherwise returns null.
     */
    fun getInternalScopedLimit(instanceName: String): Array<String>? {
        return instanceScopeLimitCache[instanceName]
    }
}
