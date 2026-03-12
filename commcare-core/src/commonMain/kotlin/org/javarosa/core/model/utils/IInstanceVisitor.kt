package org.javarosa.core.model.utils

import org.javarosa.core.model.instance.FormInstance

/**
 * An IInstanceVisitor visits every element in a DataModel
 * following the visitor design pattern.
 *
 * @author Clayton Sims
 */
interface IInstanceVisitor {
    /**
     * Performs any necessary operations on the IFormDataModel without
     * visiting any of the Model's potential children.
     */
    fun visit(dataInstance: FormInstance)
}
