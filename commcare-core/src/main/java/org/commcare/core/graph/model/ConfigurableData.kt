package org.commcare.core.graph.model

/**
 * Interface to be implemented by any classes in this package that store configuration data
 * using a String => String mapping.
 *
 * @author jschweers
 */
interface ConfigurableData {
    fun setConfiguration(key: String, value: String)
    fun getConfiguration(key: String): String?
    fun getConfiguration(key: String, defaultValue: String): String
}
