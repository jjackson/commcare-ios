package org.javarosa.core.util

import kotlin.jvm.JvmStatic

/**
 * Cross-platform abstraction for reading system properties.
 * On JVM, delegates to System.getProperty().
 * On iOS, returns null (no system properties).
 */
expect fun platformGetSystemProperty(key: String): String?
