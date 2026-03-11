package org.javarosa.core.util

/**
 * Simple MessageFormat replacement for KMP.
 * Replaces {0}, {1}, etc. with the provided arguments.
 */
fun formatMessage(pattern: String, vararg args: Any?): String {
    var result = pattern
    for (i in args.indices) {
        result = result.replace("{$i}", args[i]?.toString() ?: "null")
    }
    return result
}
