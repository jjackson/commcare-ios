package org.javarosa.xform.util

import org.javarosa.core.util.Interner
import org.kxml2.io.KXmlParser

/**
 * @author ctsims
 */
class InterningKXmlParser(private val stringCache: Interner<String>) : KXmlParser() {

    fun release() {
        //Anything?
    }

    override fun getAttributeName(arg0: Int): String {
        return stringCache.intern(super.getAttributeName(arg0))
    }

    override fun getAttributeNamespace(arg0: Int): String {
        return stringCache.intern(super.getAttributeNamespace(arg0))
    }

    override fun getAttributePrefix(arg0: Int): String? {
        val value = super.getAttributePrefix(arg0) ?: return null
        return stringCache.intern(value)
    }

    override fun getAttributeValue(arg0: Int): String {
        return stringCache.intern(super.getAttributeValue(arg0))
    }

    override fun getNamespace(arg0: String?): String {
        return stringCache.intern(super.getNamespace(arg0))
    }

    override fun getNamespaceUri(arg0: Int): String {
        return stringCache.intern(super.getNamespaceUri(arg0))
    }

    override fun getText(): String {
        return stringCache.intern(super.getText())
    }

    override fun getName(): String {
        return stringCache.intern(super.getName())
    }
}
