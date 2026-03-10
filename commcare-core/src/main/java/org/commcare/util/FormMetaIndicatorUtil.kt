package org.commcare.util

import org.javarosa.core.model.FormDef
import org.javarosa.core.model.instance.TreeReference

/**
 * @author $|-|!˅@M
 */
object FormMetaIndicatorUtil {

    const val FORM_DESCRIPTOR = "Pragma-Form-Descriptor"

    @JvmStatic
    fun getPragma(key: String, formDef: FormDef, contextRef: TreeReference): String? {
        val value = formDef.getLocalizer()?.getText(key)
        if (value != null) {
            return formDef.fillTemplateString(value, contextRef)
        }
        return null
    }
}
