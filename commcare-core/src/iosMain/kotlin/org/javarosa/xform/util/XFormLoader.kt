package org.javarosa.xform.util

import org.javarosa.core.model.FormDef

actual object XFormLoader {
    actual fun loadForm(xmlBytes: ByteArray): FormDef {
        throw UnsupportedOperationException(
            "XForm parsing is not yet available on iOS. " +
            "XFormParser (2800+ lines, kxml2 DOM) needs to be ported from jvmMain to commonMain."
        )
    }
}
