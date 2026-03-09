package org.javarosa.core.model.instance.utils

import org.commcare.cases.instance.CaseInstanceTreeElement
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.InstanceBase
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.UnrecognisedInstanceRootException
import org.javarosa.core.model.instance.utils.TreeUtilities.xmlToTreeElement
import org.javarosa.xml.util.InvalidStructureException
import java.io.IOException
import java.util.Hashtable

/**
 * Collection of static instance loading methods
 *
 * @author Phillip Mates
 */
object InstanceUtils {

    @JvmStatic
    @Throws(InvalidStructureException::class, IOException::class)
    fun loadFormInstance(formFilepath: String): FormInstance {
        val root = xmlToTreeElement(formFilepath)
        return FormInstance(root, null)
    }

    /**
     * Sets instance properties to the given instance root
     *
     * @param instanceRoot instance root
     * @param instanceId   instance id to set
     * @param instanceBase instance base to set
     */
    @JvmStatic
    fun setUpInstanceRoot(
        instanceRoot: AbstractTreeElement?,
        instanceId: String?,
        instanceBase: InstanceBase?
    ) {
        if (instanceRoot == null) {
            return
        }
        when (instanceRoot) {
            is TreeElement -> {
                instanceRoot.setInstanceName(instanceId)
                instanceRoot.setParent(instanceBase)
            }
            is CaseInstanceTreeElement -> {
                instanceRoot.rebase(instanceBase)
            }
            else -> {
                val error = "Unrecognised Instance root of type ${instanceRoot.javaClass.name}" +
                        " for instance $instanceId"
                throw UnrecognisedInstanceRootException(error)
            }
        }
    }

    /**
     * @param limitingList a list of instance names to restrict the returning set to; null
     *                     if no limiting is being used
     * @param instances    the full set of data instances
     * @return a hashtable representing the data instances that are in scope for this Entry,
     * potentially limited by [limitingList]
     */
    @JvmStatic
    @JvmSuppressWildcards
    fun getLimitedInstances(
        limitingList: Set<String>?,
        instances: Hashtable<String, DataInstance<*>>
    ): Hashtable<String, DataInstance<*>> {
        val copy = Hashtable<String, DataInstance<*>>()
        val en = instances.keys()
        while (en.hasMoreElements()) {
            val key = en.nextElement()

            // This is silly, all of these are external data instances. TODO: save their
            // construction details instead.
            val cur = instances[key]
            if (limitingList == null || limitingList.contains(cur?.getInstanceId())) {
                // Make sure we either aren't using a limiting list, or the instanceid is in the list
                if (cur is ExternalDataInstance) {
                    // Copy the EDI so when it gets populated we don't keep it dependent on this object's lifecycle!!
                    copy[key] = ExternalDataInstance(cur.getReference(), cur.getInstanceId())
                } else if (cur != null) {
                    copy[key] = cur
                }
            }
        }

        return copy
    }
}
