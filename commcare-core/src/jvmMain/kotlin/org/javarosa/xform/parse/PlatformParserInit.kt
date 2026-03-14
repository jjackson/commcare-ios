package org.javarosa.xform.parse

internal actual fun platformParserInit() {
    org.javarosa.xpath.expr.JvmXPathFunctions.ensureRegistered()
    org.commcare.core.JvmPlatformInit.ensureRegistered()
}
