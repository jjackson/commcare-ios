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

fun nullIfEmpty(ba: ByteArray?): ByteArray? {
    return if (ba == null || ba.isEmpty()) null else ba
}

fun emptyIfNull(ba: ByteArray?): ByteArray {
    return ba ?: ByteArray(0)
}

fun <T> nullIfEmpty(v: ArrayList<T>?): ArrayList<T>? {
    return if (v == null || v.isEmpty()) null else v
}

fun emptyIfNull(v: ArrayList<*>?): ArrayList<*> {
    return v ?: ArrayList<Any?>()
}
