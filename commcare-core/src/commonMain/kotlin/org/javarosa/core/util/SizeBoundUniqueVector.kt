package org.javarosa.core.util

/**
 * Only use for J2ME Compatible Vectors
 *
 * A SizeBoundVector that enforces that all member items be unique. You must
 * implement the .equals() method of class E
 *
 * @author wspride
 */
class SizeBoundUniqueVector<E>(sizeLimit: Int) : SizeBoundVector<E>(sizeLimit) {

    override fun add(element: E): Boolean {
        return when {
            this.size == limit -> {
                additional++
                true
            }
            this.contains(element) -> false
            else -> {
                super.add(element)
                true
            }
        }
    }
}
