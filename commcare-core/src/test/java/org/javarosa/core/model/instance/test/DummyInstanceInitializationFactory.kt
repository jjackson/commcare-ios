package org.javarosa.core.model.instance.test

import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.core.model.instance.InstanceInitializationFactory
import org.javarosa.core.model.instance.InstanceRoot

/**
 * Dummy instance initialization factory used in testing. Doesn't actually
 * support loading external instances, so if it is ever invoked, it raises an
 * error.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
class DummyInstanceInitializationFactory : InstanceInitializationFactory() {

    override fun getSpecializedExternalDataInstance(instance: ExternalDataInstance): ExternalDataInstance {
        return instance
    }

    override fun generateRoot(instance: ExternalDataInstance): InstanceRoot {
        throw RuntimeException("Loading external instances isn't supported " +
            "using this instance initialization factory.")
    }
}
