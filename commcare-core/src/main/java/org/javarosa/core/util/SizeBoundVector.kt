package org.javarosa.core.util

import java.util.Vector

/**
 * Only use for J2ME Compatible Vectors
 *
 * @author ctsims
 */
open class SizeBoundVector<E>(
    @JvmField protected var limit: Int
) : Vector<E>() {

    @JvmField
    protected var additional: Int = 0

    @JvmField
    var badImageReferenceCount: Int = 0

    @JvmField
    var badAudioReferenceCount: Int = 0

    @JvmField
    var badVideoReferenceCount: Int = 0

    @Synchronized
    override fun addElement(obj: E) {
        if (this.size == limit) {
            additional++
        } else {
            super.addElement(obj)
        }
    }

    fun getAdditional(): Int {
        return additional
    }

    fun addBadImageReference() {
        badImageReferenceCount++
    }

    fun addBadAudioReference() {
        badAudioReferenceCount++
    }

    fun addBadVideoReference() {
        badVideoReferenceCount++
    }
}
