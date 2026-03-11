package org.commcare.data.xml

import kotlin.jvm.JvmStatic

/**
 * Metadata class used in conjunction with [TreeBuilder] to allow creating simple
 * [org.javarosa.core.model.instance.TreeElement] trees.
 *
 * ```
 * SimpleNode.parentNode("node", mapOf("attr1" to "v1"), listOf(
 *     SimpleNode.textNode("child", emptyMap(), "some text"),
 *     SimpleNode.textNode("child", emptyMap(), "other text")
 * ))
 * ```
 */
class SimpleNode private constructor(
    val name: String,
    val attributes: Map<String, String>,
    val value: String?,
    val children: List<SimpleNode>?
) {
    companion object {
        @JvmStatic
        fun textNode(name: String, text: String): SimpleNode {
            return textNode(name, emptyMap(), text)
        }

        @JvmStatic
        fun textNode(name: String, attributes: Map<String, String>, text: String): SimpleNode {
            return SimpleNode(name, attributes, text, null)
        }

        @JvmStatic
        fun parentNode(name: String, attributes: Map<String, String>, children: List<SimpleNode>): SimpleNode {
            return SimpleNode(name, attributes, null, children)
        }
    }
}
