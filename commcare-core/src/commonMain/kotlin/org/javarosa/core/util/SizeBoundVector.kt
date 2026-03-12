package org.javarosa.core.util

import kotlin.jvm.JvmField

/**
 * Only use for J2ME Compatible Vectors
 *
 * @author ctsims
 */
open class SizeBoundVector<E>(
    @JvmField protected var limit: Int
) : MutableList<E> {

    private val backingList: ArrayList<E> = ArrayList()

    @JvmField
    protected var additional: Int = 0

    @JvmField
    var badImageReferenceCount: Int = 0

    @JvmField
    var badAudioReferenceCount: Int = 0

    @JvmField
    var badVideoReferenceCount: Int = 0

    // MutableList delegation
    override val size: Int get() = backingList.size
    override fun contains(element: E): Boolean = backingList.contains(element)
    override fun containsAll(elements: Collection<E>): Boolean = backingList.containsAll(elements)
    override fun get(index: Int): E = backingList[index]
    override fun indexOf(element: E): Int = backingList.indexOf(element)
    override fun isEmpty(): Boolean = backingList.isEmpty()
    override fun iterator(): MutableIterator<E> = backingList.iterator()
    override fun lastIndexOf(element: E): Int = backingList.lastIndexOf(element)
    override fun listIterator(): MutableListIterator<E> = backingList.listIterator()
    override fun listIterator(index: Int): MutableListIterator<E> = backingList.listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> = backingList.subList(fromIndex, toIndex)

    override fun add(element: E): Boolean {
        if (this.size == limit) {
            additional++
            return false
        } else {
            return backingList.add(element)
        }
    }

    override fun add(index: Int, element: E) {
        if (this.size == limit) {
            additional++
        } else {
            backingList.add(index, element)
        }
    }

    override fun addAll(index: Int, elements: Collection<E>): Boolean = backingList.addAll(index, elements)
    override fun addAll(elements: Collection<E>): Boolean = backingList.addAll(elements)
    override fun clear() = backingList.clear()
    override fun remove(element: E): Boolean = backingList.remove(element)
    override fun removeAll(elements: Collection<E>): Boolean = backingList.removeAll(elements)
    override fun removeAt(index: Int): E = backingList.removeAt(index)
    override fun retainAll(elements: Collection<E>): Boolean = backingList.retainAll(elements)
    override fun set(index: Int, element: E): E = backingList.set(index, element)

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
