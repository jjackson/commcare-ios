package org.javarosa.core.util.externalizable

/**
 * Cross-platform string utilities for serialization.
 * Replaces JVM-only ExtUtil.nullIfEmpty and ExtUtil.emptyIfNull.
 */

fun nullIfEmpty(s: String?): String? {
    return if (s == null || s.isEmpty()) null else s
}

fun emptyIfNull(s: String?): String {
    return s ?: ""
}
