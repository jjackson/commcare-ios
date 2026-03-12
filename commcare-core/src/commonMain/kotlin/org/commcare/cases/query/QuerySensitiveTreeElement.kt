package org.commcare.cases.query

import org.javarosa.core.model.instance.AbstractTreeElement

/**
 * NOTE: Each time a method is implemented here, it should be added to QuerySensitiveTreeElementWrapper
 *
 * Created by ctsims on 9/19/2017.
 */
interface QuerySensitiveTreeElement : AbstractTreeElement {

    /**
     * Retrieves the TreeElement representing the attribute at
     * the provided namespace and name, or null if none exists.
     *
     * If 'null' is provided for the namespace, it will match the first
     * attribute with the matching name.
     */
    fun getAttribute(context: QueryContext, namespace: String?, name: String): AbstractTreeElement?

    fun getChildMultiplicity(context: QueryContext, name: String): Int

    /**
     * Get a child element with the given name and occurrence position (multiplicity)
     *
     * @param name         the name of the child element to select
     * @param multiplicity is the n-th occurrence of an element with a given name
     */
    fun getChild(context: QueryContext, name: String, multiplicity: Int): AbstractTreeElement?
}
