package org.commcare.resources.model

/**
 * An UnreliableSourceException is a special type of UnresolvedResourceException which signals
 * that a resource was not available when the attmept was made to resolve it, but that resource
 * may be available in the future, due to potentially lossy channels like HTTP/bluetooth, etc.
 *
 * This exception should only be caught by name (compared to a URE) when an attempt will be
 * made to retry the resource resolution.
 *
 * @author ctsims
 */
class UnreliableSourceException(r: Resource, message: String?) : UnresolvedResourceException(r, message)
