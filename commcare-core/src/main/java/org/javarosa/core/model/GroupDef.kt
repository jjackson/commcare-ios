package org.javarosa.core.model

import org.javarosa.core.model.actions.ActionController
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapListPoly
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.model.xform.XPathReference
import java.io.DataInputStream
import java.io.DataOutputStream
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
    override fun readExternal(dis: DataInputStream, pf: PrototypeFactory) {
        setID(ExtUtil.readInt(dis))
        setAppearanceAttr(ExtUtil.read(dis, ExtWrapNullable(String::class.java), pf) as String?)
        setBind(ExtUtil.read(dis, ExtWrapTagged(), pf) as XPathReference)
        setTextID(ExtUtil.read(dis, ExtWrapNullable(String::class.java), pf) as String?)
        setLabelInnerText(ExtUtil.read(dis, ExtWrapNullable(String::class.java), pf) as String?)
        setIsRepeat(ExtUtil.readBool(dis))
        @Suppress("UNCHECKED_CAST")
        setChildren(ExtUtil.read(dis, ExtWrapListPoly(), pf) as ArrayList<IFormElement>)

        noAddRemove = ExtUtil.readBool(dis)
        count = ExtUtil.read(dis, ExtWrapNullable(ExtWrapTagged()), pf) as XPathReference?

        chooseCaption = ExtUtil.nullIfEmpty(ExtUtil.readString(dis))
        addCaption = ExtUtil.nullIfEmpty(ExtUtil.readString(dis))
        delCaption = ExtUtil.nullIfEmpty(ExtUtil.readString(dis))
        doneCaption = ExtUtil.nullIfEmpty(ExtUtil.readString(dis))
        addEmptyCaption = ExtUtil.nullIfEmpty(ExtUtil.readString(dis))
        doneEmptyCaption = ExtUtil.nullIfEmpty(ExtUtil.readString(dis))
        entryHeader = ExtUtil.nullIfEmpty(ExtUtil.readString(dis))
        delHeader = ExtUtil.nullIfEmpty(ExtUtil.readString(dis))
        mainHeader = ExtUtil.nullIfEmpty(ExtUtil.readString(dis))
    }

    /**
     * Write the group definition object to the supplied stream.
     */
    @Throws(PlatformIOException::class)
    override fun writeExternal(dos: DataOutputStream) {
        ExtUtil.writeNumeric(dos, getID().toLong())
        ExtUtil.write(dos, ExtWrapNullable(getAppearanceAttr()))
        ExtUtil.write(dos, ExtWrapTagged(getBind()!!))
        ExtUtil.write(dos, ExtWrapNullable(getTextID()))
        ExtUtil.write(dos, ExtWrapNullable(getLabelInnerText()))
        ExtUtil.writeBool(dos, isRepeat())
        ExtUtil.write(dos, ExtWrapListPoly(getChildren()))

        ExtUtil.writeBool(dos, noAddRemove)
        val currentCount = count
        ExtUtil.write(dos, ExtWrapNullable(if (currentCount != null) ExtWrapTagged(currentCount) else null))

        ExtUtil.writeString(dos, ExtUtil.emptyIfNull(chooseCaption))
        ExtUtil.writeString(dos, ExtUtil.emptyIfNull(addCaption))
        ExtUtil.writeString(dos, ExtUtil.emptyIfNull(delCaption))
        ExtUtil.writeString(dos, ExtUtil.emptyIfNull(doneCaption))
        ExtUtil.writeString(dos, ExtUtil.emptyIfNull(addEmptyCaption))
        ExtUtil.writeString(dos, ExtUtil.emptyIfNull(doneEmptyCaption))
        ExtUtil.writeString(dos, ExtUtil.emptyIfNull(entryHeader))
        ExtUtil.writeString(dos, ExtUtil.emptyIfNull(delHeader))
        ExtUtil.writeString(dos, ExtUtil.emptyIfNull(mainHeader))
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
