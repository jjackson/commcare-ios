package org.javarosa.form.api

import org.javarosa.core.model.FormIndex
import org.javarosa.core.model.GroupDef
import org.javarosa.core.model.IFormElement
import org.javarosa.core.model.QuestionDef
import org.javarosa.core.model.actions.Action
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.instance.InvalidReferenceException
import org.javarosa.core.model.instance.TreeElement


import org.javarosa.core.model.trace.PlatformTrace
import kotlin.jvm.JvmStatic

/**
 * This class is used to navigate through an xform and appropriately manipulate
 * the FormEntryModel's state.
 */
class FormEntryController {
    private val model: FormEntryModel
    private val formEntrySession: FormEntrySessionRecorder

    /**
     * Creates a new form entry controller for the model provided
     */
    constructor(model: FormEntryModel) : this(model, DummyFormEntrySession())

    private constructor(model: FormEntryModel, formEntrySession: FormEntrySessionRecorder) {
        this.model = model
        this.formEntrySession = formEntrySession
    }

    fun getModel(): FormEntryModel {
        return model
    }

    /**
     * Attempts to save answer at the current FormIndex into the datamodel.
     */
    fun answerQuestion(data: IAnswerData?): Int {
        return answerQuestion(model.getFormIndex(), data)
    }

    /**
     * Attempts to save the answer at the specified FormIndex into the
     * datamodel.
     *
     * @return OK if save was successful, error if a constraint was violated.
     */
    @PlatformTrace
    fun answerQuestion(index: FormIndex, data: IAnswerData?): Int {
        val q = model.getQuestionPrompt(index).getQuestion()

        if (model.getEvent(index) != EVENT_QUESTION) {
            throw RuntimeException("Non-Question object at the form index.")
        }

        val element = model.getTreeElement(index)

        // A question is complex when it has a copy tag that needs to be
        // evaluated by copying in the correct xml subtree.  XXX: The code to
        // answer complex questions is incomplete, but luckily this feature is
        // rarely used.
        val complexQuestion = q.isComplex()

        val hasConstraints = false

        if (element.isRequired() && data == null) {
            return ANSWER_REQUIRED_BUT_EMPTY
        }

        if (complexQuestion) {
            if (hasConstraints) {
                //TODO: itemsets: don't currently evaluate constraints for
                //itemset/copy -- haven't figured out how handle it yet
                throw RuntimeException("Itemsets do not currently evaluate constraints. Your constraint will not work, please remove it before proceeding.")
            } else {
                try {
                    model.getForm().copyItemsetAnswer(q, element, data!!)
                } catch (ire: InvalidReferenceException) {
                    ire.printStackTrace()
                    val referenceMessage = if (ire.invalidReference != null) " Reference: " + ire.invalidReference else ""
                    throw RuntimeException("Invalid reference while copying itemset answer: " + ire.message + referenceMessage)
                }
                q.getActionController().triggerActionsFromEvent(Action.EVENT_QUESTION_VALUE_CHANGED, model.getForm())
                return ANSWER_OK
            }
        } else {
            if (!model.getForm().evaluateConstraint(index.getReference()!!, data)) {
                // constraint checking failed
                return ANSWER_CONSTRAINT_VIOLATED
            }
            commitAnswer(element, index, data)
            q.getActionController().triggerActionsFromEvent(Action.EVENT_QUESTION_VALUE_CHANGED, model.getForm())
            return ANSWER_OK
        }
    }

    fun checkQuestionConstraint(index: FormIndex, data: IAnswerData?): Int {
        val q = model.getQuestionPrompt(index).getQuestion()

        if (model.getEvent(index) != EVENT_QUESTION) {
            throw RuntimeException("Non-Question object at the form index.")
        }

        val element = model.getTreeElement(index)

        if (element.isRequired() && data == null) {
            return ANSWER_REQUIRED_BUT_EMPTY
        }

        // A question is complex when it has a copy tag that needs to be
        // evaluated by copying in the correct xml subtree.  XXX: The code to
        // answer complex questions is incomplete, but luckily this feature is
        // rarely used.
        val complexQuestion = q.isComplex()

        return if (complexQuestion) {
            // TODO PLM: unsure how to check constraints of 'complex' questions
            ANSWER_OK
        } else {
            if (!model.getForm().evaluateConstraint(index.getReference()!!, data)) {
                // constraint checking failed
                ANSWER_CONSTRAINT_VIOLATED
            } else {
                ANSWER_OK
            }
        }
    }

    /**
     * saveAnswer attempts to save the current answer into the data model
     * without doing any constraint checking. Only use this if you know what
     * you're doing. For normal form filling you should always use
     * answerQuestion or answerCurrentQuestion.
     *
     * @return true if saved successfully, false otherwise.
     */
    fun saveAnswer(index: FormIndex, data: IAnswerData?): Boolean {
        if (model.getEvent(index) != EVENT_QUESTION) {
            throw RuntimeException("Non-Question object at the form index.")
        }
        val element = model.getTreeElement(index)
        return commitAnswer(element, index, data)
    }

    /**
     * commitAnswer actually saves the data into the datamodel.
     *
     * @return true if saved successfully, false otherwise
     */
    private fun commitAnswer(element: TreeElement, index: FormIndex, data: IAnswerData?): Boolean {
        if (data != null) {
            formEntrySession.addValueSet(index, data.uncast().getString()!!)
        } else {
            formEntrySession.addQuestionSkip(index)
        }

        if (data != null || element.getValue() != null) {
            // we should check if the data to be saved is already the same as
            // the data in the model, but we can't (no IAnswerData.equals())
            model.getForm().setValue(data, index.getReference()!!, element)

            return true
        } else {
            return false
        }
    }

    /**
     * Expand any unexpanded repeats at the given FormIndex.
     */
    fun expandRepeats(index: FormIndex) {
        model.createModelIfNecessary(index)
    }

    /**
     * Navigates forward in the form.
     *
     * @return the next event that should be handled by a view.
     */
    fun stepToNextEvent(expandRepeats: Boolean): Int {
        return stepEvent(true, expandRepeats)
    }

    fun stepToNextEvent(): Int {
        return stepToNextEvent(true)
    }

    /**
     * Find the FormIndex that comes after the given one.
     */
    fun getNextIndex(index: FormIndex, expandRepeats: Boolean): FormIndex {
        return getAdjacentIndex(index, true, expandRepeats)
    }

    /**
     * Find the FormIndex that comes after the given one, expanding any repeats encountered.
     */
    fun getNextIndex(index: FormIndex): FormIndex {
        return getAdjacentIndex(index, true, true)
    }

    /**
     * Navigates backward in the form.
     *
     * @return the next event that should be handled by a view.
     */
    fun stepToPreviousEvent(): Int {
        // second parameter doesn't matter because stepping backwards never involves descending into repeats
        return stepEvent(false, false)
    }

    /**
     * Moves the current FormIndex to the next/previous relevant position.
     *
     * @param expandRepeats Expand any unexpanded repeat groups
     * @return event associated with the new position
     */
    private fun stepEvent(forward: Boolean, expandRepeats: Boolean): Int {
        var index = model.getFormIndex()
        index = getAdjacentIndex(index, forward, expandRepeats)
        return jumpToIndex(index, expandRepeats)
    }

    /**
     * Find a FormIndex next to the given one.
     *
     * NOTE: Leave public for Touchforms
     *
     * @param forward If true, get the next FormIndex, else get the previous one.
     */
    fun getAdjacentIndex(index: FormIndex, forward: Boolean, expandRepeats: Boolean): FormIndex {
        var currentIndex = index
        var descend = true
        var relevant: Boolean
        var inForm: Boolean

        do {
            if (forward) {
                currentIndex = model.incrementIndex(currentIndex, descend)
            } else {
                currentIndex = model.decrementIndex(currentIndex)
            }

            //reset all step rules
            descend = true
            relevant = true
            inForm = currentIndex.isInForm()
            if (inForm) {
                relevant = model.isIndexRelevant(currentIndex)

                //If this the current index is a group and it is not relevant
                //do _not_ dig into it.
                if (!relevant && model.getEvent(currentIndex) == EVENT_GROUP) {
                    descend = false
                }
            }
        } while (inForm && !relevant)

        if (expandRepeats) {
            expandRepeats(currentIndex)
        }

        return currentIndex
    }

    /**
     * Jumps to a given FormIndex. Expands any repeat groups.
     *
     * @return EVENT for the specified Index.
     */
    fun jumpToIndex(index: FormIndex): Int {
        return jumpToIndex(index, true)
    }

    /**
     * Jumps to a given FormIndex.
     *
     * @param expandRepeats Expand any unexpanded repeat groups
     * @return EVENT for the specified Index.
     */
    fun jumpToIndex(index: FormIndex, expandRepeats: Boolean): Int {
        model.setQuestionIndex(index, expandRepeats)
        return model.getEvent(index)
    }

    /**
     * Used by touchforms
     */
    @Suppress("unused")
    fun descendIntoNewRepeat(): FormIndex {
        jumpToIndex(model.getForm().descendIntoRepeat(model.getFormIndex(), -1))
        newRepeat(model.getFormIndex())
        return model.getFormIndex()
    }

    /**
     * Used by touchforms
     */
    @Suppress("unused")
    fun descendIntoRepeat(n: Int): FormIndex {
        jumpToIndex(model.getForm().descendIntoRepeat(model.getFormIndex(), n))
        return model.getFormIndex()
    }

    /**
     * Creates a new repeated instance of the group referenced by the specified
     * FormIndex.
     */
    fun newRepeat(questionIndex: FormIndex) {
        try {
            model.getForm().createNewRepeat(questionIndex)
            formEntrySession.addNewRepeat(questionIndex)
        } catch (ire: InvalidReferenceException) {
            val referenceMessage = if (ire.invalidReference != null) " Reference: " + ire.invalidReference else ""
            throw RuntimeException("Invalid reference while copying itemset answer: " + ire.message + referenceMessage)
        }
    }

    /**
     * Creates a new repeated instance of the group referenced by the current
     * FormIndex.
     */
    fun newRepeat() {
        newRepeat(model.getFormIndex())
    }

    /**
     * Deletes a repeated instance of a group referenced by the specified
     * FormIndex.
     */
    fun deleteRepeat(questionIndex: FormIndex): FormIndex {
        return model.getForm().deleteRepeat(questionIndex)
    }

    /**
     * Used by touchforms
     */
    @Suppress("unused")
    fun deleteRepeat(n: Int) {
        deleteRepeat(model.getForm().descendIntoRepeat(model.getFormIndex(), n))
    }

    /**
     * Sets the current language.
     */
    fun setLanguage(language: String) {
        model.setLanguage(language)
    }

    fun getFormEntrySessionString(): String {
        return formEntrySession.toString()
    }

    /**
     * getQuestionPrompts for the current index
     */
    fun getQuestionPrompts(): Array<FormEntryPrompt> {
        return getQuestionPrompts(getModel().getFormIndex())
    }

    /**
     * Returns an array of relevant question prompts that should be displayed as a single screen.
     * If the given form index is a question, it is returned. Otherwise if the
     * given index is a field list (and _only_ when it is a field list)
     */
    fun getQuestionPrompts(currentIndex: FormIndex): Array<FormEntryPrompt> {
        val element: IFormElement = this.getModel().getForm().getChild(currentIndex)

        //If we're in a group, we will collect of the questions in this group
        if (element is GroupDef) {
            //Assert that this is a valid condition (only field lists return prompts)
            if (!this.isHostWithAppearance(currentIndex, FIELD_LIST)) {
                throw RuntimeException(
                    "Cannot get question prompts from a non-field-list group at index " +
                            currentIndex + " for form " + getModel().getForm().getName()
                )
            }

            // Questions to collect
            val questionList = ArrayList<FormEntryPrompt>()

            //Step over all events in this field list and collect them
            var walker = currentIndex

            var event = this.getModel().getEvent(currentIndex)
            while (FormIndex.isSubElement(currentIndex, walker)) {
                if (event == EVENT_QUESTION) {
                    questionList.add(this.getModel().getQuestionPrompt(walker))
                }

                if (event == EVENT_PROMPT_NEW_REPEAT) {
                    //TODO: What if there is a non-deterministic repeat up in the field list?
                }

                //this handles relevance for us
                walker = this.getNextIndex(walker)
                event = this.getModel().getEvent(walker)
            }

            return questionList.toTypedArray()
        } else {
            // We have a question, so just get the one prompt
            return arrayOf(this.getModel().getQuestionPrompt(currentIndex))
        }
    }

    /**
     * A convenience method for determining if the current FormIndex is a group that is marked
     * with the appearance appearanceTag. This is useful for returning
     * from the form hierarchy view to a selected index.
     */
    fun isHostWithAppearance(index: FormIndex, appearanceTag: String): Boolean {
        // if this isn't a group or is a repeat, return right away
        if (this.getModel().getForm().getChild(index) !is GroupDef ||
            (this.getModel().getForm().getChild(index) as GroupDef).isRepeat()
        ) {
            return false
        }

        //TODO: Is it possible we need to make sure this group isn't inside of another group which
        //is itself a field list? That would make the top group the field list host, not the
        //descendant group

        val gd = this.getModel().getForm().getChild(index) as GroupDef // exceptions?
        return appearanceTag.equals(gd.getAppearanceAttr(), ignoreCase = true)
    }

    // Used by Formplayer
    @Suppress("unused")
    fun isFieldListHost(index: FormIndex): Boolean {
        return isHostWithAppearance(index, FIELD_LIST)
    }

    companion object {
        const val ANSWER_OK: Int = 0
        const val ANSWER_REQUIRED_BUT_EMPTY: Int = 1
        const val ANSWER_CONSTRAINT_VIOLATED: Int = 2

        const val EVENT_BEGINNING_OF_FORM: Int = 0
        const val EVENT_END_OF_FORM: Int = 1
        const val EVENT_PROMPT_NEW_REPEAT: Int = 2
        const val EVENT_QUESTION: Int = 4
        const val EVENT_GROUP: Int = 8
        const val EVENT_REPEAT: Int = 16
        const val EVENT_REPEAT_JUNCTURE: Int = 32

        // 'appearance' attributes for a group
        const val FIELD_LIST: String = "field-list"
        const val COMPACT: String = "compact"

        const val STEP_OVER_GROUP: Boolean = true
        const val STEP_INTO_GROUP: Boolean = false

        /**
         * Builds controller that records form entry actions to human readable
         * format that allows for replaying
         */
        @JvmStatic
        fun buildRecordingController(model: FormEntryModel): FormEntryController {
            return FormEntryController(model, FormEntrySession())
        }
    }
}
