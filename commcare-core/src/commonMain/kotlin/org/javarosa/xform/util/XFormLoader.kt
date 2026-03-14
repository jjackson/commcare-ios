package org.javarosa.xform.util

import org.javarosa.core.model.FormDef

/**
 * Cross-platform XForm loader. Parses XForm XML bytes into a FormDef.
 *
 * JVM: delegates to XFormUtils + kxml2-based XFormParser.
 * iOS: not yet available — requires XFormParser port to commonMain.
 */
expect object XFormLoader {
    fun loadForm(xmlBytes: ByteArray): FormDef
}
