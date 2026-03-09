package org.javarosa.core.model

import org.javarosa.core.model.actions.ActionController
import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.ExtWrapListPoly
import org.javarosa.core.util.externalizable.ExtWrapMap
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.model.xform.XPathReference
import org.javarosa.xform.parse.XFormParser
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Hashtable
import java.util.Vector

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

    private var choices: Vector<SelectChoice>? = null
    private var dynamicChoices: ItemsetBinding? = null

    private var mQuestionStrings: Hashtable<String, QuestionString> = Hashtable()

    var extensions: Vector<QuestionDataExtension> = Vector()

    private var actionController: ActionController = ActionController()

    constructor() : this(Constants.NULL_ID, Constants.DATATYPE_TEXT)

    constructor(id: Int, controlType: Int) {
        setID(id)
        setControlType(controlType)
        mQuestionStrings = Hashtable()
        extensions = Vector()

        //ctsims 7/8/2015 - Some of Will's code seems to assume that there's ~always a label
        //defined, which is causing problems with blank questions. Adding this for now to ensure things
        //work reliably
        mQuestionStrings[XFormParser.LABEL_ELEMENT] = QuestionString(XFormParser.LABEL_ELEMENT, null)
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
        return mQuestionStrings[XFormParser.HELP_ELEMENT]?.textId
    }

    fun addSelectChoice(choice: SelectChoice) {
        if (choices == null) {
            choices = Vector()
        }
        choice.setIndex(choices!!.size)
        choices!!.addElement(choice)
    }

    fun removeSelectChoice(choice: SelectChoice) {
        val currentChoices = choices
        if (currentChoices == null) {
            choice.setIndex(0)
            return
        }

        if (currentChoices.contains(choice)) {
            currentChoices.removeElement(choice)
        }
    }

    fun getChoices(): Vector<SelectChoice>? {
        return choices
    }

    fun getChoice(i: Int): SelectChoice {
        return choices!!.elementAt(i)
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

    override fun getChildren(): Vector<IFormElement>? {
        return null
    }

    override fun setChildren(v: Vector<IFormElement>?) {
        throw IllegalStateException("Can't add children to question def")
    }

    override fun addChild(fe: IFormElement?) {
        throw IllegalStateException("Can't add children to question def")
    }

    override fun getChild(i: Int): IFormElement? {
        return null
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(dis: DataInputStream, pf: PrototypeFactory) {
        setID(ExtUtil.readInt(dis))
        binding = ExtUtil.read(dis, ExtWrapNullable(ExtWrapTagged()), pf) as XPathReference?
        setAppearanceAttr(ExtUtil.read(dis, ExtWrapNullable(String::class.java), pf) as String?)
        setControlType(ExtUtil.readInt(dis))
        @Suppress("UNCHECKED_CAST")
        choices = ExtUtil.nullIfEmpty(ExtUtil.read(dis, ExtWrapList(SelectChoice::class.java), pf) as Vector<SelectChoice>)
        for (i in 0 until getNumChoices()) {
            choices!!.elementAt(i).setIndex(i)
        }
        setDynamicChoices(ExtUtil.read(dis, ExtWrapNullable(ItemsetBinding::class.java), pf) as ItemsetBinding?)
        @Suppress("UNCHECKED_CAST")
        mQuestionStrings = ExtUtil.read(dis, ExtWrapMap(String::class.java, QuestionString::class.java), pf) as Hashtable<String, QuestionString>
        @Suppress("UNCHECKED_CAST")
        extensions = ExtUtil.read(dis, ExtWrapListPoly(), pf) as Vector<QuestionDataExtension>
        actionController = ExtUtil.read(dis, ExtWrapNullable(ActionController::class.java), pf) as ActionController? ?: ActionController()
    }

    @Throws(IOException::class)
    override fun writeExternal(dos: DataOutputStream) {
        ExtUtil.writeNumeric(dos, getID().toLong())
        val currentBinding = binding
        ExtUtil.write(dos, ExtWrapNullable(if (currentBinding == null) null else ExtWrapTagged(currentBinding)))
        ExtUtil.write(dos, ExtWrapNullable(getAppearanceAttr()))
        ExtUtil.writeNumeric(dos, getControlType().toLong())
        ExtUtil.write(dos, ExtWrapList(ExtUtil.emptyIfNull(choices)))
        ExtUtil.write(dos, ExtWrapNullable(dynamicChoices))
        ExtUtil.write(dos, ExtWrapMap(mQuestionStrings))
        ExtUtil.write(dos, ExtWrapListPoly(extensions))
        ExtUtil.write(dos, ExtWrapNullable(actionController))
    }

    override fun getDeepChildCount(): Int {
        return 1
    }

    override fun getTextID(): String? {
        return this.getQuestionString(XFormParser.LABEL_ELEMENT)!!.textId
    }

    override fun getLabelInnerText(): String? {
        return this.getQuestionString(XFormParser.LABEL_ELEMENT)!!.textInner
    }

    override fun setTextID(textID: String?) {
        var mutableTextID = textID
        if (DateUtils.stringContains(mutableTextID, ";")) {
            System.err.println("Warning: TextID contains ;form modifier:: \"${mutableTextID!!.substring(mutableTextID.indexOf(";"))}\"... will be stripped.")
            mutableTextID = mutableTextID.substring(0, mutableTextID.indexOf(";")) //trim away the form specifier
        }
        this.getQuestionString(XFormParser.LABEL_ELEMENT)!!.textId = mutableTextID
    }

    fun addExtension(extension: QuestionDataExtension) {
        extensions.addElement(extension)
    }

}
