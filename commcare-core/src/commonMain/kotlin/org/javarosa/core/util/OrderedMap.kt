package org.javarosa.core.util

/**
 * Marker interface for maps that maintain insertion order as a semantic property.
 * Used by ExtUtil.hashtableEquals to distinguish ordered maps from unordered ones
 * (two ordered maps with same entries in different order are NOT equal).
 *
 * Implemented by OrderedHashtable on JVM.
 */
interface OrderedMap
