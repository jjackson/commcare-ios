package org.javarosa.core.model

import org.javarosa.core.model.actions.ActionController
import org.javarosa.core.services.storage.Persistable
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.model.xform.XPathReference

/**
 * An IFormDataElement is an element of the physical interaction for
 * a form, an example of an implementing element would be the definition
 * of a Question.
 *
 * @author Drew Roos
 */
interface IFormElement : Persistable, Externalizable {

    /**
     * get the TextID for this element used for localization purposes
     *
     * @return the TextID (bare, no ;form appended to it!!)
     */
    fun getTextID(): String?

    /**
     * Set the textID for this element for use with localization.
     *
     * @param id the plain TextID WITHOUT any form specification (e.g. ;long)
     */
    fun setTextID(id: String?)

    /**
     * @return A vector containing any children that this element
     * might have. Null if the element is not able to have child
     * elements.
     */
    fun getChildren(): ArrayList<IFormElement>?

    /**
     * @param v the children of this element, if it is capable of having
     *          child elements.
     * @throws IllegalStateException if the element is incapable of
     *                               having children.
     */
    fun setChildren(v: ArrayList<IFormElement>?)

    /**
     * @param fe The child element to be added
     * @throws IllegalStateException if the element is incapable of
     *                               having children.
     */
    fun addChild(fe: IFormElement?)

    fun getChild(i: Int): IFormElement?

    /**
     * @return A recursive count of how many elements are ancestors of this element.
     */
    fun getDeepChildCount(): Int

    /**
     * @return The data reference for this element
     */
    fun getBind(): XPathReference?

    /**
     * This method returns the regular
     * innertext between label tags (if present) (<label>innertext</label>).
     *
     * @return <label> innertext or null (if innertext is not present).
     */
    fun getLabelInnerText(): String?

    fun getAppearanceAttr(): String?

    fun setAppearanceAttr(appearanceAttr: String?)

    fun getActionController(): ActionController?
}
