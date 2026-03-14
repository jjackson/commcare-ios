package org.javarosa.xform.parse

/**
 * Platform-specific initialization for XFormParser.
 * JVM: registers JvmXPathFunctions and JvmPlatformInit.
 * iOS: no-op (or registers iOS-specific functions).
 */
internal expect fun platformParserInit()
