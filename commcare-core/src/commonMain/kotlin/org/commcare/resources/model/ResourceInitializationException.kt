package org.commcare.resources.model

/**
 * Created by amstone326 on 5/30/18.
 */
class ResourceInitializationException(
    r: Resource,
    reason: Exception
) : Exception(
    "Initialization failed for resource with id ${r.getResourceId()} (${r.getDescriptor()}) due to the following exception: ${reason.message}"
) {
    private val resource: Resource = r

    fun getResource(): Resource {
        return resource
    }
}
