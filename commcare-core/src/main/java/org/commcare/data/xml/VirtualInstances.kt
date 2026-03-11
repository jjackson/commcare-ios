package org.commcare.data.xml

import org.commcare.core.interfaces.VirtualDataInstanceStorage
import org.commcare.modern.util.Pair
import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.core.model.instance.ExternalDataInstanceSource
import org.javarosa.core.model.instance.TreeElement
import kotlin.jvm.JvmStatic

object VirtualInstances {

    const val SEARCH_INSTANCE_ROOT_NAME = "input"
    const val SEARCH_INSTANCE_NODE_NAME = "field"
    const val SEARCH_INPUT_NODE_NAME_ATTR = "name"

    const val SELCTED_CASES_INSTANCE_ROOT_NAME = "results"
    const val SELCTED_CASES_INSTANCE_NODE_NAME = "value"

    @JvmStatic
    fun makeSearchInputInstanceID(suffix: String): String {
        return "search-input:$suffix"
    }

    @JvmStatic
    fun buildSearchInputInstance(
        refId: String, userInputValues: Map<String, String?>
    ): ExternalDataInstance {
        val nodes = ArrayList<SimpleNode>()
        userInputValues.forEach { (key, value) ->
            val attributes = mapOf(SEARCH_INPUT_NODE_NAME_ATTR to key)
            nodes.add(SimpleNode.textNode(SEARCH_INSTANCE_NODE_NAME, attributes, value ?: ""))
        }
        val instanceId = makeSearchInputInstanceID(refId)
        val root = TreeBuilder.buildTree(instanceId, SEARCH_INSTANCE_ROOT_NAME, nodes)
        return ExternalDataInstance(getSearchInputReference(refId), instanceId, root)
    }

    @JvmStatic
    fun buildSelectedValuesInstance(
        instanceId: String, selectedValues: Array<String>
    ): ExternalDataInstance {
        val nodes = ArrayList<SimpleNode>()
        for (selectedValue in selectedValues) {
            nodes.add(SimpleNode.textNode(SELCTED_CASES_INSTANCE_NODE_NAME, selectedValue))
        }
        val root = TreeBuilder.buildTree(instanceId, SELCTED_CASES_INSTANCE_ROOT_NAME, nodes)
        return ExternalDataInstance(getSelectedEntitiesReference(instanceId), instanceId, root)
    }

    /**
     * Builds and stores the selected entitied into selected entities instance
     *
     * @param virtualDataInstanceStorage Instance Storage
     * @param selectedValues             Values to be stored into instance
     * @param instanceId                 instance id for the new instance
     * @return A pair of unique storage id for the instance and the newly generated instance
     */
    @JvmStatic
    fun storeSelectedValuesInInstance(
        virtualDataInstanceStorage: VirtualDataInstanceStorage,
        selectedValues: Array<String>,
        instanceId: String
    ): Pair<String, ExternalDataInstance> {
        val instance = buildSelectedValuesInstance(instanceId, selectedValues)
        val guid = virtualDataInstanceStorage.write(instance)

        // rebuild instance with the source
        val instanceSource = ExternalDataInstanceSource.buildVirtual(instance, guid)
        val selectedValuesInstance = instanceSource.toInstance()
        return Pair(guid, selectedValuesInstance)
    }

    @JvmStatic
    fun getSelectedEntitiesReference(referenceId: String): String {
        return getInstanceReference(ExternalDataInstance.JR_SELECTED_ENTITIES_REFERENCE, referenceId)
    }

    @JvmStatic
    fun getSearchInputReference(referenceId: String): String {
        return getInstanceReference(ExternalDataInstance.JR_SEARCH_INPUT_REFERENCE, referenceId)
    }

    @JvmStatic
    fun getRemoteReference(referenceId: String): String {
        return getInstanceReference(ExternalDataInstance.JR_REMOTE_REFERENCE, referenceId)
    }

    /**
     * Parses instance reference of format "refScheme/refId" to return the reference id
     *
     * @param reference An instance reference in form of "refScheme/refId"
     * @return reference id from the given reference
     */
    @JvmStatic
    fun getReferenceId(reference: String): String {
        return reference.substring(reference.lastIndexOf('/') + 1)
    }

    /**
     * Parses instance reference of format "refScheme/refId" to return the reference scheme
     *
     * @param reference An instance reference in form of "refScheme/refId"
     * @return reference scheme from the given reference
     */
    @JvmStatic
    fun getReferenceScheme(reference: String): String {
        return reference.substring(0, reference.lastIndexOf('/'))
    }

    /**
     * Constructs an instance reference in format "refScheme/refId"
     *
     * @param referenceScheme reference scheme for the instance reference
     * @param referenceId     reference id for the instance reference
     * @return an instance reference in format "refScheme/refId"
     */
    @JvmStatic
    fun getInstanceReference(referenceScheme: String, referenceId: String): String {
        return referenceScheme + "/" + referenceId
    }

    /**
     * Throw when the data instance with the given key doesn't exist in the DB
     */
    class InstanceNotFoundException(key: String, namespace: String) : RuntimeException(
        "Could not find data instance with ID $key (namespace=$namespace)." +
                "Redirecting to home screen. If this issue persists, please file a bug report."
    )
}
