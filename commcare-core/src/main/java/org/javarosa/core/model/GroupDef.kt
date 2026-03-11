package org.javarosa.core.model

import org.javarosa.core.model.actions.ActionController
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.core.util.externalizable.emptyIfNull
import org.javarosa.core.util.externalizable.nullIfEmpty
import org.javarosa.model.xform.XPathReference
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * The definition of a group in a form or questionaire.
 *
 * @author Daniel Kayiwa
 */
class GroupDef : IFormElement {
    // A list of questions on a group.
    private var children: ArrayList<IFormElement> = ArrayList()
    // True if this is a "repeat", false if it is a "group"
    private var mIsRepeat: Boolean = false
    // The group number.
    private var id: Int = Constants.NULL_ID
    // reference to a location in the model to store data in
    private var binding: XPathReference? = null

    private var labelInnerText: String? = null
    private var appearanceAttr: String? = null
    private var textID: String? = null

    //custom phrasings for repeats
    @JvmField var chooseCaption: String? = null
    @JvmField var addCaption: String? = null
    @JvmField var delCaption: String? = null
    @JvmField var doneCaption: String? = null
    @JvmField var addEmptyCaption: String? = null
    @JvmField var doneEmptyCaption: String? = null
    @JvmField var entryHeader: String? = null
    @JvmField var delHeader: String? = null
    @JvmField var mainHeader: String? = null

    /**
     * When set the user can only create as many children as specified by the
     * 'count' reference.
     */
    @JvmField var noAddRemove: Boolean = false

    /**
     * Points to a reference that stores the expected number of entries for the
     * group.
     */
    @JvmField var count: XPathReference? = null

    override fun getID(): Int {
        return id
    }

    override fun setID(id: Int) {
        this.id = id
    }

    override fun getBind(): XPathReference? {
        return binding
    }

    fun setBind(binding: XPathReference?) {
        this.binding = binding
    }

    override fun getChildren(): ArrayList<IFormElement> {
        return children
    }

    override fun setChildren(children: ArrayList<IFormElement>?) {
        this.children = children ?: ArrayList()
    }

    override fun addChild(fe: IFormElement?) {
        children.add(fe!!)
    }

    override fun getChild(i: Int): IFormElement? {
        return if (children == null || i >= children.size) {
            null
        } else {
            children[i]
        }
    }

    /**
     * @return true if this represents a <repeat> element
     */
    fun isRepeat(): Boolean {
        return mIsRepeat
    }

    fun setIsRepeat(repeat: Boolean) {
        this.mIsRepeat = repeat
    }

    override fun getLabelInnerText(): String? {
        return labelInnerText
    }

    fun setLabelInnerText(lit: String?) {
        labelInnerText = lit
    }

    override fun getAppearanceAttr(): String? {
        return appearanceAttr
    }

    override fun setAppearanceAttr(appearanceAttr: String?) {
        this.appearanceAttr = appearanceAttr
    }

    override fun getActionController(): ActionController? {
        return null
    }

    /**
     * @return Reference pointing to the number of entries this group should
     * have.
     */
    fun getCountReference(): XPathReference? {
        return count
    }

    /**
     * Contextualize the reference pointing to the repeat number limit in terms
     * of the inputted context. Used to contextualize the count reference in
     * terms of the current repeat item.
     *
     * @param context Used to resolve relative parts of the 'count' reference.
     *                Usually the current repeat item reference is passed in
     *                for this value.
     * @return An absolute reference that points to the numeric limit of repeat
     * items that should be created.
     */
    fun getConextualizedCountReference(context: TreeReference): TreeReference {
        return DataInstance.unpackReference(count!!).contextualize(context)!!
    }

    override fun toString(): String {
        return "<group>"
    }

    override fun getDeepChildCount(): Int {
        var total = 0
        val e = children.iterator()
        while (e.hasNext()) {
            total += (e.next() as IFormElement).getDeepChildCount()
        }
        return total
    }

    /**
     * Reads a group definition object from the supplied stream.
     */
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(dis: PlatformDataInputStream, pf: PrototypeFactory) {
        setID(SerializationHelpers.readInt(dis))
        setAppearanceAttr(SerializationHelpers.readNullableString(dis, pf))
        setBind(SerializationHelpers.readTagged(dis, pf) as XPathReference)
        setTextID(SerializationHelpers.readNullableString(dis, pf))
        setLabelInnerText(SerializationHelpers.readNullableString(dis, pf))
        setIsRepeat(SerializationHelpers.readBool(dis))
        @Suppress("UNCHECKED_CAST")
        setChildren(SerializationHelpers.readListPoly(dis, pf) as ArrayList<IFormElement>)

        noAddRemove = SerializationHelpers.readBool(dis)
        count = SerializationHelpers.readNullableTagged(dis, pf) as XPathReference?

        chooseCaption = nullIfEmpty(SerializationHelpers.readString(dis))
        addCaption = nullIfEmpty(SerializationHelpers.readString(dis))
        delCaption = nullIfEmpty(SerializationHelpers.readString(dis))
        doneCaption = nullIfEmpty(SerializationHelpers.readString(dis))
        addEmptyCaption = nullIfEmpty(SerializationHelpers.readString(dis))
        doneEmptyCaption = nullIfEmpty(SerializationHelpers.readString(dis))
        entryHeader = nullIfEmpty(SerializationHelpers.readString(dis))
        delHeader = nullIfEmpty(SerializationHelpers.readString(dis))
        mainHeader = nullIfEmpty(SerializationHelpers.readString(dis))
    }

    /**
     * Write the group definition object to the supplied stream.
     */
    @Throws(PlatformIOException::class)
    override fun writeExternal(dos: PlatformDataOutputStream) {
        SerializationHelpers.writeNumeric(dos, getID().toLong())
        SerializationHelpers.writeNullable(dos, getAppearanceAttr())
        SerializationHelpers.writeTagged(dos, getBind()!!)
        SerializationHelpers.writeNullable(dos, getTextID())
        SerializationHelpers.writeNullable(dos, getLabelInnerText())
        SerializationHelpers.writeBool(dos, isRepeat())
        SerializationHelpers.writeListPoly(dos, getChildren())

        SerializationHelpers.writeBool(dos, noAddRemove)
        SerializationHelpers.writeNullableTagged(dos, count)

        SerializationHelpers.writeString(dos, emptyIfNull(chooseCaption))
        SerializationHelpers.writeString(dos, emptyIfNull(addCaption))
        SerializationHelpers.writeString(dos, emptyIfNull(delCaption))
        SerializationHelpers.writeString(dos, emptyIfNull(doneCaption))
        SerializationHelpers.writeString(dos, emptyIfNull(addEmptyCaption))
        SerializationHelpers.writeString(dos, emptyIfNull(doneEmptyCaption))
        SerializationHelpers.writeString(dos, emptyIfNull(entryHeader))
        SerializationHelpers.writeString(dos, emptyIfNull(delHeader))
        SerializationHelpers.writeString(dos, emptyIfNull(mainHeader))
    }

    override fun getTextID(): String? {
        return textID
    }

    override fun setTextID(textID: String?) {
        if (textID == null) {
            this.textID = null
            return
        }
        var mutableTextID = textID
        if (DateUtils.stringContains(mutableTextID, ";")) {
            System.err.println("Warning: TextID contains ;form modifier:: \"${mutableTextID.substring(mutableTextID.indexOf(";"))}\"... will be stripped.")
            mutableTextID = mutableTextID.substring(0, mutableTextID.indexOf(";")) //trim away the form specifier
        }
        this.textID = mutableTextID
    }
}
