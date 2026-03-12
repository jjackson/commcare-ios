package org.javarosa.core.util.externalizable

/**
 * Register JVM-specific list factories (e.g., LinkedList) that aren't available in commonMain.
 */
internal fun registerJvmListFactories() {
    ExtWrapList.LIST_FACTORIES["java.util.LinkedList"] = { java.util.LinkedList() }
}
