package org.javarosa.core.model.instance.utils

import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.utils.IInstanceVisitor

/**
 * ITreeVisitor is a visitor interface for the elements of the
 * FormInstance tree elements. In the case of composite elements,
 * method dispatch for composite members occurs following dispatch
 * for the composing member.
 *
 * @author Clayton Sims
 */
interface ITreeVisitor : IInstanceVisitor {
    override fun visit(tree: FormInstance)

    fun visit(element: AbstractTreeElement)
}
