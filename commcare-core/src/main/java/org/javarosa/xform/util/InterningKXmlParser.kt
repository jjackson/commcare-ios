package org.javarosa.xform.util

import org.javarosa.core.util.Interner
import org.javarosa.xml.PlatformXmlParser

/**
 * A PlatformXmlParser decorator that interns string results to reduce memory usage.
 * Wraps a delegate parser and passes all results through a string interner.
 *
 * @author ctsims
 */
class InterningKXmlParser(
    private val delegate: PlatformXmlParser,
    private val stringCache: Interner<String>
) : PlatformXmlParser {

    fun release() {
        //Anything?
    }

    override fun next(): Int = delegate.next()
    override val eventType: Int get() = delegate.eventType
    override val isWhitespace: Boolean get() = delegate.isWhitespace
    override val depth: Int get() = delegate.depth
    override val attributeCount: Int get() = delegate.attributeCount

    override val namespace: String? get() {
        val ns = delegate.namespace ?: return null
        return stringCache.intern(ns)
    }

    override fun getAttributeValue(namespace: String?, name: String): String? {
        val value = delegate.getAttributeValue(namespace, name) ?: return null
        return stringCache.intern(value)
    }

    override fun getAttributeName(index: Int): String {
        return stringCache.intern(delegate.getAttributeName(index))
    }

    override fun getAttributeNamespace(index: Int): String {
        return stringCache.intern(delegate.getAttributeNamespace(index))
    }

    override fun getAttributePrefix(index: Int): String? {
        val value = delegate.getAttributePrefix(index) ?: return null
        return stringCache.intern(value)
    }

    override fun getAttributeValue(index: Int): String {
        return stringCache.intern(delegate.getAttributeValue(index))
    }

    override fun getNamespace(prefix: String?): String {
        return stringCache.intern(delegate.getNamespace(prefix))
    }

    override val text: String? get() {
        val t = delegate.text ?: return null
        return stringCache.intern(t)
    }

    override val name: String? get() {
        val n = delegate.name ?: return null
        return stringCache.intern(n)
    }

    override fun nextText(): String {
        return stringCache.intern(delegate.nextText())
    }

    override fun nextTag(): Int = delegate.nextTag()

    override val positionDescription: String get() = delegate.positionDescription

    override val prefix: String? get() {
        val p = delegate.prefix ?: return null
        return stringCache.intern(p)
    }
}
