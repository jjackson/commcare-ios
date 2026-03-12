package org.commcare.cases.instance

import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.instance.utils.TreeUtilities
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * An external data instance that respects CaseDB template specifications.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
class CaseDataInstance : ExternalDataInstance {

    constructor() : super()

    /**
     * Copy constructor
     */
    constructor(instance: ExternalDataInstance) : super(instance)

    /**
     * Does the reference follow the statically defined CaseDB spec?
     */
    override fun hasTemplatePath(ref: TreeReference): Boolean {
        loadTemplateSpecLazily()
        return followsTemplateSpec(ref, caseDbSpecTemplate, 1)
    }

    override fun useCaseTemplate(): Boolean {
        return true
    }

    override fun copy(): CaseDataInstance {
        return CaseDataInstance(this)
    }

    companion object {
        private var caseDbSpecTemplate: TreeElement? = null
        private const val CASEDB_WILD_CARD = "CASEDB_WILD_CARD"

        private fun loadTemplateSpecLazily() {
            val errorMsg = "Failed to load casedb template spec xml file " +
                    "while checking if case related xpath follows the template structure."
            if (caseDbSpecTemplate == null) {
                try {
                    caseDbSpecTemplate =
                        TreeUtilities.xmlToTreeElement("/casedb_instance_structure.xml")
                } catch (e: InvalidStructureException) {
                    throw RuntimeException(errorMsg)
                } catch (e: PlatformIOException) {
                    throw RuntimeException(errorMsg)
                }
            }
        }

        private fun followsTemplateSpec(
            refToCheck: TreeReference,
            currTemplateNode: TreeElement?,
            currRefDepth: Int
        ): Boolean {
            if (currTemplateNode == null) {
                return false
            }

            if (currRefDepth == refToCheck.size()) {
                return true
            }

            val name = refToCheck.getName(currRefDepth) ?: return false

            return if (refToCheck.getMultiplicity(currRefDepth) == TreeReference.INDEX_ATTRIBUTE) {
                val templateAttr = currTemplateNode.getAttribute(null, name)
                followsTemplateSpec(refToCheck, templateAttr, currRefDepth + 1)
            } else {
                var nextTemplateNode = currTemplateNode.getChild(name, 0)
                if (nextTemplateNode == null) {
                    // didn't find a node of the given name in the template, check
                    // if a wild card exists at this level of the template.
                    nextTemplateNode = currTemplateNode.getChild(CASEDB_WILD_CARD, 0)
                }
                followsTemplateSpec(refToCheck, nextTemplateNode, currRefDepth + 1)
            }
        }
    }
}
