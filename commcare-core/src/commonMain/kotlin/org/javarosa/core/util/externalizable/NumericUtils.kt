package org.javarosa.core.util.externalizable

/**
 * Pure-Kotlin numeric conversion utilities extracted from ExtUtil
 * so they can be used from commonMain code.
 */

fun numericToLong(o: Any): Long {
    return when (o) {
        is Byte -> o.toLong()
        is Short -> o.toLong()
        is Int -> o.toLong()
        is Long -> o
        is Char -> o.code.toLong()
        else -> throw ClassCastException()
    }
}

fun numericToInt(l: Long): Int {
    if (l < Int.MIN_VALUE || l > Int.MAX_VALUE)
        throw ArithmeticException("Value ($l) cannot fit into int")
    return l.toInt()
}

fun numericToShort(l: Long): Short {
    if (l < Short.MIN_VALUE || l > Short.MAX_VALUE)
        throw ArithmeticException("Value ($l) cannot fit into short")
    return l.toShort()
}

fun numericToByte(l: Long): Byte {
    if (l < Byte.MIN_VALUE || l > Byte.MAX_VALUE)
        throw ArithmeticException("Value ($l) cannot fit into byte")
    return l.toByte()
}
