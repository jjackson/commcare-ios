package org.javarosa.xpath

import org.javarosa.core.model.instance.TreeReference

open class XPathMissingInstanceException : XPathException {
    @JvmField
    var instanceName: String? = null
    @JvmField
    var ref: TreeReference? = null

    /**
     * Indicates that an instance is declared, but doesn't actually have any data in it
     * @param instanceName the name of the empty instance
     * @param message
     */
    constructor(instanceName: String?, message: String?) : super(message) {
        this.instanceName = instanceName
    }

    /**
     * Indicates that an instance was required to be present to evaluate a ref, but could not be
     * found at all
     * @param refThatNeededInstance the ref that we were attempting to resolve, but could not
     *                             because no instance was found
     */
    constructor(refThatNeededInstance: TreeReference?) : super("No instance was found with which to resolve reference: $refThatNeededInstance") {
        this.ref = refThatNeededInstance
    }
}
