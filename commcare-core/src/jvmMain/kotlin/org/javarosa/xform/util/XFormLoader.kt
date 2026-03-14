package org.javarosa.xform.util

import org.javarosa.core.io.createByteArrayInputStream
import org.javarosa.core.model.FormDef

actual object XFormLoader {
    actual fun loadForm(xmlBytes: ByteArray): FormDef {
        return XFormUtils.getFormFromInputStream(createByteArrayInputStream(xmlBytes))
    }
}
