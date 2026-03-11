package org.javarosa.xform.parse

/**
 * String constants for XForm element names, extracted from XFormParser
 * to avoid depending on kxml2/xmlpull types.
 */
object XFormConstants {
    const val LABEL_ELEMENT: String = "label"
    const val HELP_ELEMENT: String = "help"
    const val HINT_ELEMENT: String = "hint"
    const val CONSTRAINT_ELEMENT: String = "alert"
}
