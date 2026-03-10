package org.javarosa.xml.dom

import org.javarosa.core.util.Interner
import org.kxml2.io.KXmlParser

/**
 * JVM-only KXmlParser subclass that interns string results.
 * Used during Document.parse() to ensure DOM nodes contain interned strings.
 * This extends KXmlParser directly so it can be passed to Document.parse().
 */
internal class InterningKXmlParserJvm(
    private val stringCache: Interner<String>
) : KXmlParser() {

    override fun getAttributeName(index: Int): String =
        stringCache.intern(super.getAttributeName(index))

    override fun getAttributeNamespace(index: Int): String =
        stringCache.intern(super.getAttributeNamespace(index))

    override fun getAttributePrefix(index: Int): String? {
        val value = super.getAttributePrefix(index) ?: return null
        return stringCache.intern(value)
    }

    override fun getAttributeValue(index: Int): String =
        stringCache.intern(super.getAttributeValue(index))

    override fun getNamespace(prefix: String?): String =
        stringCache.intern(super.getNamespace(prefix))

    override fun getText(): String =
        stringCache.intern(super.getText())

    override fun getName(): String =
        stringCache.intern(super.getName())
}
