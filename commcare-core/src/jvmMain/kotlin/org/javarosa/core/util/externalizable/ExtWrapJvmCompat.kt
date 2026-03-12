@file:JvmName("ExtWrapJvmCompat")

package org.javarosa.core.util.externalizable

/**
 * JVM backward-compatible constructor wrappers for ExtWrap* classes.
 * These allow Java callers to continue using Class<*> parameters.
 */

// ExtWrapBase: Class<*> constructor
fun ExtWrapBase(type: Class<*>): ExtWrapBase = ExtWrapBase(type.kotlin)

// ExtWrapNullable: Class<*> constructor
fun ExtWrapNullable(type: Class<*>?): ExtWrapNullable {
    return if (type != null) ExtWrapNullable(type.kotlin) else ExtWrapNullable()
}

// ExtWrapList: Class<*> constructors
fun ExtWrapList(listElementType: Class<*>): ExtWrapList = ExtWrapList(listElementType.kotlin)

fun ExtWrapList(listElementType: Class<*>, listImplementation: Class<*>): ExtWrapList = ExtWrapList(
    listElementType.kotlin,
    ExtWrapList.LIST_FACTORIES[listImplementation.name] ?: { ArrayList() },
    listImplementation.name
)

fun ExtWrapList(type: ExternalizableWrapper, listImplementation: Class<*>): ExtWrapList = ExtWrapList(
    type,
    ExtWrapList.LIST_FACTORIES[listImplementation.name] ?: { ArrayList() },
    listImplementation.name
)

// ExtWrapMap: Class<*> constructors
fun ExtWrapMap(keyType: Class<*>, dataType: Class<*>): ExtWrapMap =
    ExtWrapMap(keyType.kotlin, dataType.kotlin)

fun ExtWrapMap(keyType: Class<*>, dataType: Class<*>, type: Int): ExtWrapMap =
    ExtWrapMap(keyType.kotlin, dataType.kotlin, type)

fun ExtWrapMap(keyType: Class<*>, dataType: ExternalizableWrapper): ExtWrapMap =
    ExtWrapMap(ExtWrapBase(keyType.kotlin), dataType)

// ExtWrapMapPoly: Class<*> constructors
fun ExtWrapMapPoly(keyType: Class<*>): ExtWrapMapPoly = ExtWrapMapPoly(keyType.kotlin)

fun ExtWrapMapPoly(keyType: Class<*>, ordered: Boolean): ExtWrapMapPoly =
    ExtWrapMapPoly(keyType.kotlin, ordered)

// ExtWrapMultiMap: Class<*> constructor
fun ExtWrapMultiMap(keyType: Class<*>): ExtWrapMultiMap = ExtWrapMultiMap(ExtWrapBase(keyType.kotlin))
