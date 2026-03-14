package org.commcare.test.utilities

import org.commcare.cases.instance.CaseDataInstance
import org.commcare.cases.instance.CaseInstanceTreeElement
import org.commcare.util.mocks.MockUserDataSandbox
import org.javarosa.core.model.instance.ConcreteInstanceRoot
import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.core.model.instance.InstanceInitializationFactory
import org.javarosa.core.model.instance.InstanceRoot

/**
 * Utility class for initializing abstract data instances from
 * sandboxed storage
 *
 * @author ctsims
 */
class TestInstanceInitializer(private val sandbox: MockUserDataSandbox) : InstanceInitializationFactory() {

    override fun getSpecializedExternalDataInstance(instance: ExternalDataInstance): ExternalDataInstance {
        return if (CaseInstanceTreeElement.MODEL_NAME == instance.getInstanceId()) {
            CaseDataInstance(instance)
        } else {
            instance
        }
    }

    override fun generateRoot(instance: ExternalDataInstance): InstanceRoot {
        val ref = instance.getReference()
        if (ref != null && ref.contains(CaseInstanceTreeElement.MODEL_NAME)) {
            val root = CaseInstanceTreeElement(instance.getBase(), sandbox.getCaseStorage())
            return ConcreteInstanceRoot(root)
        }
        return ConcreteInstanceRoot.NULL
    }
}
