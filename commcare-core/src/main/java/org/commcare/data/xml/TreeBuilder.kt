package org.commcare.data.xml

import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.instance.TreeElement
import kotlin.jvm.JvmStatic

/**
 * Class to build a TreeElement tree for use with DataInstance classes
 */
object TreeBuilder {

    /**
     * Build a TreeElement populated with children
     */
    @JvmStatic
    fun buildTree(instanceId: String, rootElementName: String, children: List<SimpleNode>): TreeElement {
        val root = TreeElement(rootElementName, 0)
        root.setInstanceName(instanceId)
        root.setAttribute(null, "id", instanceId)
        addChildren(instanceId, root, children)
        return root
    }

    private fun addChildren(instanceId: String, parent: TreeElement, children: List<SimpleNode>) {
        val multiplicities = HashMap<String, Int>()
        for (node in children) {
            val name = node.name
            val multiplicity = if (multiplicities.containsKey(name)) {
                multiplicities[name]!! + 1
            } else {
                0
            }
            multiplicities[name] = multiplicity

            val element = TreeElement(name, multiplicity)
            element.setInstanceName(instanceId)
            node.attributes.forEach { (attributeName, value) ->
                element.setAttribute(null, attributeName, value)
            }
            if (node.value != null) {
                element.setValue(UncastData(node.value))
            } else if (node.children != null) {
                addChildren(instanceId, element, node.children)
            }
            parent.addChild(element)
        }
    }
}
