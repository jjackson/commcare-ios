package org.commcare.resources.model

/**
 * Created by amstone326 on 5/30/18.
 */
class ResourceInitializationException(
    r: Resource,
    reason: Exception
) : Exception(
    String.format(
        "Initialization failed for resource with id %s (%s) due to the following exception: %s",
        r.getResourceId(), r.getDescriptor(), reason.message
    )
) {
    private val resource: Resource = r

    fun getResource(): Resource {
        return resource
    }
}
