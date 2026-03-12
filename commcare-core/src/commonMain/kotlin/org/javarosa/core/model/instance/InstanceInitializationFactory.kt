package org.javarosa.core.model.instance

/**
 * @author ctsims
 */
open class InstanceInitializationFactory {

    /**
     * Specializes the instance to an ExternalDataInstance class extension.
     * E.g. one might want to use the CaseDataInstance if the instanceId is
     * "casedb"
     */
    open fun getSpecializedExternalDataInstance(instance: ExternalDataInstance): ExternalDataInstance {
        return instance
    }

    open fun generateRoot(instance: ExternalDataInstance): InstanceRoot {
        return ConcreteInstanceRoot.NULL
    }
}
