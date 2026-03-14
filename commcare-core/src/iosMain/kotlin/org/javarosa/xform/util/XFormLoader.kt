package org.javarosa.xform.util

import org.javarosa.core.model.FormDef
import org.javarosa.xform.parse.XFormParser

actual object XFormLoader {
    actual fun loadForm(xmlBytes: ByteArray): FormDef {
        val parser = XFormParser(xmlBytes)
        return parser.parse()
    }
}
