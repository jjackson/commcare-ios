package org.javarosa.core.model.util.restorable

import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeReference

interface Restorable {

    fun templateData(dm: FormInstance, parentRef: TreeReference)
}
