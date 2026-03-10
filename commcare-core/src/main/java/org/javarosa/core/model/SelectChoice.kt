package org.javarosa.core.model

import org.javarosa.core.model.data.helper.Selection
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xform.parse.XFormParseException
import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

class SelectChoice : Externalizable {

    var labelInnerText: String? = null
        private set
    var textID: String? = null
    private var isLocalizable: Boolean = false
    var value: String? = null
        private set
    @JvmField
    var evaluatedSortProperty: String? = null
    private var index: Int = -1

    // if this choice represents part of an <itemset>, and the itemset uses 'copy'
    // answer mode, this points to the node to be copied if this selection is chosen
    // this field only has meaning for dynamic choices, thus is unserialized
    @JvmField
    var copyNode: TreeElement? = null

    // for deserialization only
    constructor()

    constructor(labelID: String?, value: String?) : this(labelID, null, value, true)

    /**
     * @param labelID        can be null
     * @param labelInnerText can be null
     * @param value          should not be null
     * @throws XFormParseException if value is null
     */
    constructor(labelID: String?, labelInnerText: String?, value: String?, isLocalizable: Boolean) {
        this.isLocalizable = isLocalizable
        this.textID = labelID
        this.labelInnerText = labelInnerText
        if (value != null) {
            this.value = value
        } else {
            throw XFormParseException("SelectChoice{id,innerText}:{$labelID,$labelInnerText}, has null Value!")
        }
    }

    constructor(labelOrID: String?, value: String?, isLocalizable: Boolean) : this(
        if (isLocalizable) labelOrID else null,
        if (isLocalizable) null else labelOrID,
        value,
        isLocalizable
    )

    fun setSortProperty(s: String?) {
        this.evaluatedSortProperty = s
    }

    fun setIndex(index: Int) {
        this.index = index
    }

    fun getIndex(): Int {
        if (index == -1) {
            throw RuntimeException("trying to access choice index before it has been set!")
        }
        return index
    }

    fun isLocalizable(): Boolean {
        return this.isLocalizable
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        isLocalizable = ExtUtil.readBool(`in`)
        labelInnerText = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        textID = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        value = ExtUtil.nullIfEmpty(ExtUtil.readString(`in`))
        //index will be set by questiondef
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeBool(out, isLocalizable)
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(labelInnerText))
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(textID))
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(value))
        //don't serialize index; it will be restored from questiondef
    }

    fun selection(): Selection {
        return Selection(this)
    }

    override fun toString(): String {
        return (if (textID != null && textID != "") "{$textID}" else "") +
                (labelInnerText ?: "") + " => " + value
    }

    override fun hashCode(): Int {
        var result: Int
        result = if (textID != null) textID.hashCode() else 0
        result = result xor value.hashCode()
        result = result xor index
        result = result xor (if (isLocalizable) 1 else 0)
        result = result xor (if (labelInnerText != null) labelInnerText.hashCode() else 0)
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is SelectChoice) {
            return false
        }

        val otherTextID = other.textID
        if (otherTextID == null) {
            if (this.textID != null) {
                return false
            }
        } else if (otherTextID != this.textID) {
            return false
        }

        if (other.value != this.value) {
            return false
        }

        if (other.getIndex() != this.index) {
            return false
        }

        if (other.isLocalizable() != this.isLocalizable) {
            return false
        }

        val otherLabelText = other.labelInnerText
        if (otherLabelText == null) {
            if (this.labelInnerText != null) {
                return false
            }
        } else if (otherLabelText != this.labelInnerText) {
            return false
        }

        return true
    }
}
