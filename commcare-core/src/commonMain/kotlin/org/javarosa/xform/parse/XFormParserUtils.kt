package org.javarosa.xform.parse

import org.javarosa.core.model.instance.TreeReference
import org.javarosa.model.xform.XPathReference

/**
 * Cross-platform utility for resolving absolute references from XPath bindings.
 * Extracted from XFormParser to allow usage in commonMain code (e.g. ItemSetParsingUtils).
 */
fun getAbsRef(ref: XPathReference?, parentRef: TreeReference): XPathReference {
    if (!parentRef.isAbsolute) {
        throw RuntimeException("getAbsRef: parentRef must be absolute")
    }

    val tref: TreeReference = if (ref != null) {
        ref.reference
    } else {
        TreeReference.selfRef() // only happens for <group>s with no binding
    }

    val refPreContextualization = tref
    val contextualized = tref.parent(parentRef)
        ?: throw XFormParseException(
            "Binding path [" + refPreContextualization.toString(true) +
                    "] not allowed with parent binding of [" + parentRef + "]"
        )

    return XPathReference(contextualized)
}
