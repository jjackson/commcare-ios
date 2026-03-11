package org.javarosa.form.api

import org.javarosa.core.model.Constants
import org.javarosa.core.model.FormDef
import org.javarosa.core.model.FormIndex
import org.javarosa.core.model.ItemsetBinding
import org.javarosa.core.model.QuestionDef
import org.javarosa.core.model.QuestionString
import org.javarosa.core.model.SelectChoice
import org.javarosa.core.model.condition.Constraint
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.pivot.ConstraintHint
import org.javarosa.core.model.condition.pivot.UnpivotableExpressionException
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.data.SelectMultiData
import org.javarosa.core.model.data.SelectOneData
import org.javarosa.core.model.data.helper.Selection
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.NoLocalizedTextException
import org.javarosa.core.util.UnregisteredLocaleException
import org.javarosa.xform.parse.XFormParser


import org.javarosa.core.model.trace.PlatformTrace

/**
 * This class gives you all the information you need to display a question when
 * your current FormIndex references a QuestionEvent.
 *
 * @author Yaw Anokwa
 */
open class FormEntryPrompt : FormEntryCaption {

    private var mTreeElement: TreeElement? = null
    private var populatedDynamicChoices: ArrayList<SelectChoice>? = null

    /**
     * This empty constructor exists for convenience of any supertypes of this prompt
     */
    protected constructor()

    /**
     * Creates a FormEntryPrompt for the element at the given index in the form.
     */
    constructor(form: FormDef, index: FormIndex) : super(form, index) {
        if (element !is QuestionDef) {
            throw IllegalArgumentException("FormEntryPrompt can only be created for QuestionDef elements")
        }
        this.mTreeElement = form.getMainInstance()!!.resolveReference(index.getReference()!!)
    }

    fun getControlType(): Int {
        return getQuestion().getControlType()
    }

    fun getDataType(): Int {
        return mTreeElement!!.getDataType()
    }

    //note: code overlap with FormDef.copyItemsetAnswer
    fun getAnswerValue(): IAnswerData? {
        val q = getQuestion()

        val itemset = q.getDynamicChoices()
        if (itemset != null) {
            if (itemset.valueRef != null) {
                val choices = getSelectChoices()
                val preselectedValues = ArrayList<String>()

                //determine which selections are already present in the answer
                if (itemset.copyMode) {
                    val destRef = itemset.getDestRef()!!.contextualize(mTreeElement!!.getRef())!!
                    val subNodes = form!!.getEvaluationContext()!!.expandReference(destRef)!!
                    for (i in 0 until subNodes.size) {
                        val node = form!!.getMainInstance()!!.resolveReference(subNodes[i])
                        val value = itemset.getRelativeValue()!!.evalReadable(
                            form!!.getMainInstance(),
                            EvaluationContext(form!!.getEvaluationContext(), node!!.getRef())
                        )
                        preselectedValues.add(value!!)
                    }
                } else {
                    var sels = ArrayList<Selection>()
                    val data = mTreeElement!!.getValue()
                    if (data is SelectMultiData) {
                        @Suppress("UNCHECKED_CAST")
                        sels = data.getValue() as ArrayList<Selection>
                    } else if (data is SelectOneData) {
                        sels = ArrayList()
                        sels.add(data.getValue() as Selection)
                    }
                    for (i in 0 until sels.size) {
                        preselectedValues.add(sels[i].xmlValue!!)
                    }
                }

                //populate 'selection' with the corresponding choices (matching 'value') from the dynamic choiceset
                val selection = ArrayList<Selection>()
                for (i in 0 until preselectedValues.size) {
                    val value = preselectedValues[i]
                    var choice: SelectChoice? = null
                    for (j in 0 until choices!!.size) {
                        val ch = choices[j]
                        if (value == ch.value) {
                            choice = ch
                            break
                        }
                    }
                    //if it's a dynamic question, then there's a good choice what they selected last time
                    //will no longer be an option this go around
                    if (choice != null) {
                        selection.add(choice.selection())
                    }
                }

                //convert to IAnswerData
                return if (selection.size == 0) {
                    null
                } else if (q.getControlType() == Constants.CONTROL_SELECT_MULTI) {
                    SelectMultiData(selection)
                } else if (q.getControlType() == Constants.CONTROL_SELECT_ONE) {
                    SelectOneData(selection[0]) //do something if more than one selected?
                } else {
                    throw RuntimeException("can't happen")
                }
            } else {
                return null //cannot map up selections without <value>
            }
        } else { //static choices
            return mTreeElement!!.getValue()
        }
    }

    fun getAnswerText(): String? {
        val data = this.getAnswerValue()

        if (data == null) {
            return null
        } else {
            var text: String

            //csims@dimagi.com - Aug 11, 2010 - Added special logic to
            //capture and display the appropriate value for selections
            //and multi-selects.
            if (data is SelectOneData) {
                text = this.getSelectItemText(data.getValue() as Selection)!!
            } else if (data is SelectMultiData) {
                var returnValue = ""
                @Suppress("UNCHECKED_CAST")
                val values = data.getValue() as ArrayList<Selection>
                for (value in values) {
                    returnValue += this.getSelectItemText(value) + " "
                }
                text = returnValue
            } else {
                text = data.getDisplayText()!!
            }

            if (getControlType() == Constants.CONTROL_SECRET) {
                var obfuscated = ""
                for (i in 0 until text.length) {
                    obfuscated += "*"
                }
                text = obfuscated
            }
            return text
        }
    }

    fun getConstraintText(): String? {
        return getConstraintText(null)
    }

    fun getConstraintText(attemptedValue: IAnswerData?): String? {
        // new constraint spec uses "alert" form XForm spec 8.2.4
        // http://www.w3.org/TR/xforms/#ui-commonelems
        val newConstraintMsg = this.localizeText(getQuestion().getQuestionString(XFormParser.CONSTRAINT_ELEMENT))
        if (newConstraintMsg != null) {
            return newConstraintMsg
        }
        //default to old logic
        return getConstraintText(null, attemptedValue)
    }

    fun getConstraintText(textForm: String?, attemptedValue: IAnswerData?): String? {
        // if doesn't exist, use the old logic
        if (mTreeElement!!.getConstraint() == null) {
            return null
        } else {
            val ec = EvaluationContext(form!!.exprEvalContext, mTreeElement!!.getRef())
            if (textForm != null) {
                ec.setOutputTextForm(textForm)
            }
            if (attemptedValue != null) {
                ec.isConstraint = true
                ec.candidateValue = attemptedValue
            }
            return mTreeElement!!.getConstraint()!!.getConstraintMessage(ec, form!!.getMainInstance(), textForm)
        }
    }

    fun getSelectChoices(): ArrayList<SelectChoice>? {
        return getSelectChoices(true)
    }

    @PlatformTrace
    open fun getSelectChoices(shouldAttemptDynamicPopulation: Boolean): ArrayList<SelectChoice>? {
        val q = getQuestion()
        val itemset = q.getDynamicChoices()
        if (itemset != null) {
            if (populatedDynamicChoices == null && shouldAttemptDynamicPopulation) {
                form!!.populateDynamicChoices(itemset, mTreeElement!!.getRef())
                populatedDynamicChoices = itemset.getChoices()
            }
            return populatedDynamicChoices
        } else {
            // static choices
            return q.getChoices()
        }
    }

    fun getOldSelectChoices(): ArrayList<SelectChoice>? {
        return getSelectChoices(false)
    }

    /**
     * @return If this prompt has all of the same display content as a previous prompt that had
     * the given question text and select choices
     */
    fun hasSameDisplayContent(
        questionTextForOldPrompt: String?,
        selectChoicesForOldPrompt: ArrayList<SelectChoice>?
    ): Boolean {
        return questionTextIsUnchanged(questionTextForOldPrompt) &&
                selectChoicesAreUnchanged(selectChoicesForOldPrompt)
    }

    private fun selectChoicesAreUnchanged(choicesForOld: ArrayList<SelectChoice>?): Boolean {
        val choicesForThis = getSelectChoices()
        return if (choicesForOld == null) {
            choicesForThis == null
        } else {
            choicesForOld == choicesForThis
        }
    }

    private fun questionTextIsUnchanged(oldQuestionText: String?): Boolean {
        val newQuestionText = getQuestionText()
        return if (newQuestionText == null) {
            oldQuestionText == null
        } else {
            newQuestionText == oldQuestionText
        }
    }

    fun isRequired(): Boolean {
        return mTreeElement!!.isRequired()
    }

    fun isReadOnly(): Boolean {
        return !mTreeElement!!.isEnabled()
    }

    fun getQuestion(): QuestionDef {
        return element as QuestionDef
    }

    /**
     * Get hint text (helper text displayed along with question).
     * ONLY RELEVANT to Question elements!
     * Will throw runTimeException if this is called for anything that isn't a Question.
     * Returns null if no hint text is available
     */
    fun getHintText(): String? {
        if (element !is QuestionDef) {
            throw RuntimeException("Can't get HintText for Elements that are not Questions!")
        }

        val qd = element as QuestionDef
        return localizeText(qd.getQuestionString(XFormParser.HINT_ELEMENT))
    }

    /**
     * Determine if this prompt has any help, whether text or multimedia.
     */
    fun hasHelp(): Boolean {
        if (this.getQuestion().getQuestionString(XFormParser.HELP_ELEMENT) != null) {
            return true
        }

        val forms = ArrayList<String>()
        forms.add(TEXT_FORM_AUDIO)
        forms.add(TEXT_FORM_IMAGE)
        forms.add(TEXT_FORM_VIDEO)
        for (formType in forms) {
            val media = getHelpMultimedia(formType)
            if (media != null && "" != media) {
                return true
            }
        }

        return false
    }

    /**
     * Get help text (helper text displayed when requested by user).
     * ONLY RELEVANT to Question elements!
     * Will throw runTimeException if this is called for anything that isn't a Question.
     * Returns null if no hint text is available
     */
    fun getHelpText(): String? {
        if (element !is QuestionDef) {
            throw RuntimeException("Can't get HelpText for Elements that are not Questions!")
        }

        val qd = element as QuestionDef
        return localizeText(qd.getQuestionString(XFormParser.HELP_ELEMENT))
    }

    /**
     * Helper for getHintText, getHelpText, getConstraintText. Tries to localize text form textID,
     * falls back to innerText if not available.
     * It may throw XPathException.
     */
    private fun localizeText(mQuestionString: QuestionString?): String? {
        if (mQuestionString == null) {
            return null
        }

        var fallbackText = mQuestionString.textFallback
        try {
            if (mQuestionString.textId != null) {
                fallbackText = getQuestionText(mQuestionString.textId)
            } else {
                fallbackText = substituteStringArgs(mQuestionString.textInner)
            }
        } catch (nlt: NoLocalizedTextException) {
            //use fallback
        } catch (ule: UnregisteredLocaleException) {
            org.javarosa.core.util.platformStdErrPrintln("Warning: No Locale set yet (while attempting to localizeText())")
        }

        return fallbackText
    }

    /**
     * Get a particular type of multimedia help associated with this question.
     *
     * @param form TEXT_FORM_AUDIO, etc.
     */
    fun getHelpMultimedia(form: String): String? {
        if (element !is QuestionDef) {
            throw RuntimeException("Can't get HelpText for Elements that are not Questions!")
        }
        val textID = (element as QuestionDef).getHelpTextID() ?: return null
        return this.getSpecialFormQuestionText(textID, form)
    }

    /**
     * Attempts to return the specified Item (from a select or 1select) text.
     * Will check for text in the following order:
     * Localized Text (long form) -> Localized Text (no special form)
     * If no textID is available, method will return this item's labelInnerText.
     *
     * @param sel the selection (item), if `null` will throw a IllegalArgumentException
     * @return Question Text. `null` if no text for this element exists (after all fallbacks).
     * @throws IllegalArgumentException if Selection is `null`
     */
    fun getSelectItemText(sel: Selection?): String? {
        //throw tantrum if this method is called when it shouldn't be or sel==null
        if (getFormElement() !is QuestionDef)
            throw RuntimeException("Can't retrieve question text for non-QuestionDef form elements!")
        if (sel == null) throw IllegalArgumentException("Cannot use null as an argument!")

        //Just in case the selection hasn't had a chance to be initialized yet.
        if (sel.index == -1) {
            sel.attachChoice(this.getQuestion())
        }

        //check for the null id case and return labelInnerText if it is so.
        val tid = sel.choice!!.textID
        if (tid == null || "" == tid) {
            return substituteStringArgs(sel.choice!!.labelInnerText)
        }

        //otherwise check for 'long' form of the textID, then for the default form and return
        var returnText: String?
        returnText = getIText(tid, "long")
        if (returnText == null) returnText = getIText(tid, null)

        return substituteStringArgs(returnText)
    }

    fun getSelectChoiceText(selection: SelectChoice): String? {
        return getSelectItemText(selection.selection())
    }

    /**
     * This method is generally used to retrieve special forms for a
     * (select or 1select) item, e.g. "audio", "video", etc.
     *
     * @param sel  - The Item whose text you're trying to retrieve.
     * @param form - Special text form of Item you're trying to retrieve.
     * @return Special Form Text. `null` if no text for this element exists (with the specified special form).
     * @throws IllegalArgumentException if `sel == null`
     */
    fun getSpecialFormSelectItemText(sel: Selection?, form: String?): String? {
        if (sel == null)
            throw IllegalArgumentException("Cannot use null as an argument for Selection!")

        //Just in case the selection hasn't had a chance to be initialized yet.
        if (sel.index == -1) {
            sel.attachChoice(this.getQuestion())
        }

        val textID = sel.choice!!.textID
        if (textID == null || textID == "") return null

        val returnText = getIText(textID, form)

        return substituteStringArgs(returnText)
    }

    fun getSpecialFormSelectChoiceText(sel: SelectChoice, form: String?): String? {
        return getSpecialFormSelectItemText(sel.selection(), form)
    }

    fun getSelectItemMarkdownText(sel: SelectChoice): String? {
        return getSpecialFormSelectChoiceText(sel, FormEntryCaption.TEXT_FORM_MARKDOWN)
    }

    @Throws(UnpivotableExpressionException::class)
    fun requestConstraintHint(hint: ConstraintHint) {
        //NOTE: Technically there's some rep exposure, here. People could use this mechanism to expose the instance.
        //We could hide it by dispatching hints through a final abstract class instead.
        val c = mTreeElement!!.getConstraint()
        if (c != null) {
            hint.init(
                EvaluationContext(form!!.exprEvalContext, mTreeElement!!.getRef()),
                c.constraint,
                this.form!!.getMainInstance()
            )
        } else {
            //can't pivot what ain't there.
            throw UnpivotableExpressionException()
        }
    }
}
