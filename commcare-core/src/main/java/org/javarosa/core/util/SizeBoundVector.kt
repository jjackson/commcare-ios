package org.javarosa.core.util


/**
 * Only use for J2ME Compatible Vectors
 *
 * @author ctsims
 */
open class SizeBoundVector<E>(
    @JvmField protected var limit: Int
) : ArrayList<E>() {

    @JvmField
    protected var additional: Int = 0

    @JvmField
    var badImageReferenceCount: Int = 0

    @JvmField
    var badAudioReferenceCount: Int = 0

    @JvmField
    var badVideoReferenceCount: Int = 0

    override fun contains(element: E): Boolean = super.contains(element)

    @Synchronized
    override fun add(element: E): Boolean {
        if (this.size == limit) {
            additional++
            return false
        } else {
            return super.add(element)
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
