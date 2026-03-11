package org.javarosa.core.model

import org.javarosa.core.model.actions.ActionController
import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.emptyIfNull
import org.javarosa.core.util.externalizable.nullIfEmpty
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.model.xform.XPathReference
import org.javarosa.xform.parse.XFormConstants
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * The definition of a Question to be presented to users when
 * filling out a form.
 *
 * QuestionDef requires that any XPathReferences that are used
 * are contained in the FormDefRMS's PrototypeFactoryDeprecated in order
 * to be properly deserialized. If they aren't, an exception
 * will be thrown at the time of deserialization.
 *
 * @author Daniel Kayiwa/Drew Roos
 */
class QuestionDef : IFormElement {
    private var id: Int = 0

    // reference to the location in the model from which to load data for the question,
    // and store data back to when question is answered
    private var binding: XPathReference? = null

    // The type of widget. eg TextInput,Slider,List etc.
    private var controlType: Int = 0
    private var appearanceAttr: String? = null

    private var choices: ArrayList<SelectChoice>? = null
    private var dynamicChoices: ItemsetBinding? = null

    private var mQuestionStrings: HashMap<String, QuestionString> = HashMap()

    var extensions: ArrayList<QuestionDataExtension> = ArrayList()

    private var actionController: ActionController = ActionController()

    constructor() : this(Constants.NULL_ID, Constants.DATATYPE_TEXT)

    constructor(id: Int, controlType: Int) {
        setID(id)
        setControlType(controlType)
        mQuestionStrings = HashMap()
        extensions = ArrayList()

        //ctsims 7/8/2015 - Some of Will's code seems to assume that there's ~always a label
        //defined, which is causing problems with blank questions. Adding this for now to ensure things
        //work reliably
        mQuestionStrings[XFormConstants.LABEL_ELEMENT] = QuestionString(XFormConstants.LABEL_ELEMENT, null)
        actionController = ActionController()
    }

    fun putQuestionString(key: String, value: QuestionString) {
        mQuestionStrings[key] = value
    }

    fun getQuestionString(key: String): QuestionString? {
        return mQuestionStrings[key]
    }

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

    fun getControlType(): Int {
        return controlType
    }

    fun setControlType(controlType: Int) {
        this.controlType = controlType
    }

    override fun getAppearanceAttr(): String? {
        return appearanceAttr
    }

    override fun setAppearanceAttr(appearanceAttr: String?) {
        this.appearanceAttr = appearanceAttr
    }

    override fun getActionController(): ActionController {
        return this.actionController
    }

    fun getHelpTextID(): String? {
        return mQuestionStrings[XFormConstants.HELP_ELEMENT]?.textId
    }

    fun addSelectChoice(choice: SelectChoice) {
        if (choices == null) {
            choices = ArrayList()
        }
        choice.setIndex(choices!!.size)
        choices!!.add(choice)
    }

    fun removeSelectChoice(choice: SelectChoice) {
        val currentChoices = choices
        if (currentChoices == null) {
            choice.setIndex(0)
            return
        }

        if (currentChoices.contains(choice)) {
            currentChoices.remove(choice)
        }
    }

    fun getChoices(): ArrayList<SelectChoice>? {
        return choices
    }

    fun getChoice(i: Int): SelectChoice {
        return choices!![i]
    }

    fun getNumChoices(): Int {
        return choices?.size ?: 0
    }

    fun getChoiceForValue(value: String): SelectChoice? {
        for (i in 0 until getNumChoices()) {
            if (getChoice(i).value == value) {
                return getChoice(i)
            }
        }
        return null
    }

    fun getDynamicChoices(): ItemsetBinding? {
        return dynamicChoices
    }

    fun setDynamicChoices(ib: ItemsetBinding?) {
        ib?.setDestRef(this)
        this.dynamicChoices = ib
    }

    /**
     * Determine if a question's answer is xml tree data.
     *
     * @return does the answer to this question yields xml tree data, and not a simple string value?
     */
    fun isComplex(): Boolean {
        return dynamicChoices != null && dynamicChoices!!.copyMode
    }

    override fun getChildren(): ArrayList<IFormElement>? {
        return null
    }

    override fun setChildren(v: ArrayList<IFormElement>?) {
        throw IllegalStateException("Can't add children to question def")
    }

    override fun addChild(fe: IFormElement?) {
        throw IllegalStateException("Can't add children to question def")
    }

    override fun getChild(i: Int): IFormElement? {
        return null
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(dis: PlatformDataInputStream, pf: PrototypeFactory) {
        setID(SerializationHelpers.readInt(dis))
        binding = SerializationHelpers.readNullableTagged(dis, pf) as XPathReference?
        setAppearanceAttr(SerializationHelpers.readNullableString(dis, pf))
        setControlType(SerializationHelpers.readInt(dis))
        @Suppress("UNCHECKED_CAST")
        choices = nullIfEmpty(SerializationHelpers.readList(dis, pf) { SelectChoice() })
        for (i in 0 until getNumChoices()) {
            choices!![i].setIndex(i)
        }
        setDynamicChoices(SerializationHelpers.readNullableExternalizable(dis, pf) { ItemsetBinding() })
        mQuestionStrings = SerializationHelpers.readStringExtMap(dis, pf) { QuestionString() }
        @Suppress("UNCHECKED_CAST")
        extensions = SerializationHelpers.readListPoly(dis, pf) as ArrayList<QuestionDataExtension>
        actionController = SerializationHelpers.readNullableExternalizable(dis, pf) { ActionController() } ?: ActionController()
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(dos: PlatformDataOutputStream) {
        SerializationHelpers.writeNumeric(dos, getID().toLong())
        SerializationHelpers.writeNullableTagged(dos, binding)
        SerializationHelpers.writeNullable(dos, getAppearanceAttr())
        SerializationHelpers.writeNumeric(dos, getControlType().toLong())
        SerializationHelpers.writeList(dos, emptyIfNull(choices))
        SerializationHelpers.writeNullable(dos, dynamicChoices)
        SerializationHelpers.writeMap(dos, mQuestionStrings)
        SerializationHelpers.writeListPoly(dos, extensions)
        SerializationHelpers.writeNullable(dos, actionController)
    }

    override fun getDeepChildCount(): Int {
        return 1
    }

    override fun getTextID(): String? {
        return this.getQuestionString(XFormConstants.LABEL_ELEMENT)!!.textId
    }

    override fun getLabelInnerText(): String? {
        return this.getQuestionString(XFormConstants.LABEL_ELEMENT)!!.textInner
    }

    override fun setTextID(textID: String?) {
        var mutableTextID = textID
        if (DateUtils.stringContains(mutableTextID, ";")) {
            org.javarosa.core.util.platformStdErrPrintln("Warning: TextID contains ;form modifier:: \"${mutableTextID!!.substring(mutableTextID.indexOf(";"))}\"... will be stripped.")
            mutableTextID = mutableTextID.substring(0, mutableTextID.indexOf(";")) //trim away the form specifier
        }
        this.getQuestionString(XFormConstants.LABEL_ELEMENT)!!.textId = mutableTextID
    }

    fun addExtension(extension: QuestionDataExtension) {
        extensions.add(extension)
    }

}
