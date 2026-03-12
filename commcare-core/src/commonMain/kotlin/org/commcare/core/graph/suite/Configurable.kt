package org.commcare.core.graph.suite

import org.commcare.suite.model.Text

/**
 * Interface to be implemented by any classes in this package that store configuration data
 * using a String => Text mapping.
 *
 * @author jschweers
 */
interface Configurable {
    fun getConfigurationKeys(): Iterator<*>
    fun getConfiguration(key: String): Text?
    fun setConfiguration(key: String, value: Text)
}
