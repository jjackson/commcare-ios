package org.javarosa.form.api

import org.javarosa.core.model.FormDef
import org.javarosa.core.model.FormIndex
import org.javarosa.core.model.GroupDef
import org.javarosa.core.model.IFormElement
import org.javarosa.core.model.QuestionDef
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.instance.InvalidReferenceException
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.trace.EvaluationTrace
import org.javarosa.core.model.trace.TraceSerialization
import org.javarosa.xpath.XPathTypeMismatchException

import java.util.Enumeration
import java.util.Hashtable
import java.util.Vector

/**
 * The data model used during form entry. Represents the current state of the
 * form and provides access to the objects required by the view and the
 * controller.
 */
class FormEntryModel {
    private val form: FormDef
    private var currentFormIndex: FormIndex

    /**
     * One of "REPEAT_STRUCTURE_" in this class's static types,
     * represents what abstract structure repeat events should
     * be broadcast as.
     */
    private var repeatStructure: Int = -1

    constructor(form: FormDef) : this(form, REPEAT_STRUCTURE_LINEAR)

    /**
     * Creates a new entry model for the form with the appropriate
     * repeat structure
     *
     * @param repeatStructure The structure of repeats (the repeat signals which should
     *                        be sent during form entry)
     * @throws IllegalArgumentException If repeatStructure is not valid
     */
    constructor(form: FormDef, repeatStructure: Int) {
        this.form = form
        var resolvedRepeatStructure = repeatStructure
        if (resolvedRepeatStructure != REPEAT_STRUCTURE_LINEAR && resolvedRepeatStructure != REPEAT_STRUCTURE_NON_LINEAR) {
            throw IllegalArgumentException("$resolvedRepeatStructure: does not correspond to a valid repeat structure")
        }
        if (resolvedRepeatStructure == REPEAT_STRUCTURE_NON_LINEAR && containsRepeatGuesses(form)) {
            resolvedRepeatStructure = REPEAT_STRUCTURE_LINEAR
        }
        this.repeatStructure = resolvedRepeatStructure
        this.currentFormIndex = FormIndex.createBeginningOfFormIndex()
    }

    /**
     * Given a FormIndex, returns the event this FormIndex represents.
     *
     * @see FormEntryController
     */
    fun getEvent(index: FormIndex): Int {
        if (index.isBeginningOfFormIndex()) {
            return FormEntryController.EVENT_BEGINNING_OF_FORM
        } else if (index.isEndOfFormIndex()) {
            return FormEntryController.EVENT_END_OF_FORM
        }

        val element = form.getChild(index)
        if (element is GroupDef) {
            if (element.isRepeat()) {
                return if (repeatStructure != REPEAT_STRUCTURE_NON_LINEAR && form.getMainInstance()!!.resolveReference(form.getChildInstanceRef(index)!!) == null) {
                    FormEntryController.EVENT_PROMPT_NEW_REPEAT
                } else if (repeatStructure == REPEAT_STRUCTURE_NON_LINEAR && index.getElementMultiplicity() == TreeReference.INDEX_REPEAT_JUNCTURE) {
                    FormEntryController.EVENT_REPEAT_JUNCTURE
                } else {
                    FormEntryController.EVENT_REPEAT
                }
            } else {
                return FormEntryController.EVENT_GROUP
            }
        } else {
            return FormEntryController.EVENT_QUESTION
        }
    }

    internal fun getTreeElement(index: FormIndex): TreeElement {
        return form.getMainInstance()!!.resolveReference(index.getReference()!!)!!
    }

    /**
     * @return the event for the current FormIndex
     * @see FormEntryController
     */
    fun getEvent(): Int {
        return getEvent(currentFormIndex)
    }

    fun getFormTitle(): String {
        return form.getTitle()!!
    }

    /**
     * @return Returns the FormEntryPrompt for the specified FormIndex if the
     * index represents a question.
     */
    fun getQuestionPrompt(index: FormIndex): FormEntryPrompt {
        if (form.getChild(index) is QuestionDef) {
            return FormEntryPrompt(form, index)
        } else {
            throw RuntimeException(
                "Invalid query for Question prompt. Non-Question object at the form index $index in Form ${form.getName()}"
            )
        }
    }

    /**
     * @return Returns the FormEntryPrompt for the current FormIndex if the
     * index represents a question.
     */
    fun getQuestionPrompt(): FormEntryPrompt {
        return getQuestionPrompt(currentFormIndex)
    }

    /**
     * When you have a non-question event, a CaptionPrompt will have all the
     * information needed to display to the user.
     *
     * @return Returns the FormEntryCaption for the given FormIndex if is not a
     * question.
     */
    fun getCaptionPrompt(index: FormIndex): FormEntryCaption {
        return FormEntryCaption(form, index)
    }

    /**
     * When you have a non-question event, a CaptionPrompt will have all the
     * information needed to display to the user.
     *
     * @return Returns the FormEntryCaption for the current FormIndex if is not
     * a question.
     */
    fun getCaptionPrompt(): FormEntryCaption {
        return getCaptionPrompt(currentFormIndex)
    }

    /**
     * @return an array of Strings of the current languages. Null if there are
     * none.
     */
    fun getLanguages(): Array<String?>? {
        if (form.getLocalizer() != null) {
            return form.getLocalizer()!!.availableLocales
        }
        return null
    }

    /**
     * @return Returns the current FormIndex referenced by the FormEntryModel.
     */
    fun getFormIndex(): FormIndex {
        return currentFormIndex
    }

    internal fun setLanguage(language: String) {
        if (form.getLocalizer() != null) {
            form.getLocalizer()!!.setLocale(language)
        }
    }

    /**
     * @return Returns the currently selected language.
     */
    fun getLanguage(): String? {
        return form.getLocalizer()!!.locale
    }

    fun setQuestionIndex(index: FormIndex) {
        this.setQuestionIndex(index, true)
    }

    /**
     * Set the FormIndex for the current question.
     *
     * @param expandRepeats Expand any unexpanded repeat groups
     */
    fun setQuestionIndex(index: FormIndex, expandRepeats: Boolean) {
        if (currentFormIndex != index) {
            if (expandRepeats) {
                createModelIfNecessary(index)
            }
            currentFormIndex = index
        }
    }

    fun getForm(): FormDef {
        return form
    }

    /**
     * Returns a hierarchical list of FormEntryCaption objects for the given
     * FormIndex
     *
     * @return list of FormEntryCaptions in hierarchical order
     */
    fun getCaptionHierarchy(index: FormIndex): Array<FormEntryCaption> {
        val captions = Vector<FormEntryCaption>()
        var remaining: FormIndex? = index
        while (remaining != null) {
            remaining = remaining.nextLevel
            val localIndex = index.diff(remaining) ?: continue
            val element = form.getChild(localIndex)
            if (element != null) {
                var caption: FormEntryCaption? = null
                if (element is GroupDef)
                    caption = FormEntryCaption(getForm(), localIndex)
                else if (element is QuestionDef)
                    caption = FormEntryPrompt(getForm(), localIndex)

                if (caption != null) {
                    captions.addElement(caption)
                }
            }
        }
        val captionArray = arrayOfNulls<FormEntryCaption>(captions.size)
        captions.copyInto(captionArray)
        @Suppress("UNCHECKED_CAST")
        return captionArray as Array<FormEntryCaption>
    }

    /**
     * Returns a hierarchical list of FormEntryCaption objects for the current
     * FormIndex
     *
     * @return list of FormEntryCaptions in hierarchical order
     */
    fun getCaptionHierarchy(): Array<FormEntryCaption> {
        return getCaptionHierarchy(currentFormIndex)
    }

    /**
     * Determine if the current FormIndex is relevant. Only relevant indexes
     * should be returned when filling out a form.
     *
     * @return true if current element at FormIndex is relevant
     */
    fun isIndexRelevant(index: FormIndex): Boolean {
        val ref = form.getChildInstanceRef(index)
        val isAskNewRepeat = getEvent(index) == FormEntryController.EVENT_PROMPT_NEW_REPEAT
        val isRepeatJuncture = getEvent(index) == FormEntryController.EVENT_REPEAT_JUNCTURE

        var relevant: Boolean
        if (isAskNewRepeat) {
            relevant = form.isRepeatRelevant(ref!!) && form.canCreateRepeat(ref, index)
        } else if (isRepeatJuncture) {
            relevant = form.isRepeatRelevant(ref!!)
        } else {
            val node = form.getMainInstance()!!.resolveReference(ref!!)
            relevant = node != null && node.isRelevant
        }

        if (relevant) {
            var ancestorIndex = index
            while (!ancestorIndex.isTerminal()) {
                val ancestorNode =
                    form.getMainInstance()!!.resolveReference(ancestorIndex.getLocalReference()!!)

                if (!ancestorNode!!.isRelevant) {
                    relevant = false
                    break
                }
                ancestorIndex = ancestorIndex.nextLevel!!
            }
        }

        return relevant
    }

    /**
     * For the current index: Checks whether the index represents a node which
     * should exist given a non-interactive repeat, along with a count for that
     * repeat which is beneath the dynamic level specified.
     *
     * If this index does represent such a node, the new model for the repeat is
     * created behind the scenes and the index for the initial question is
     * returned.
     *
     * Note: This method will not prevent the addition of new repeat elements in
     * the interface, it will merely use the xforms repeat hint to create new
     * nodes that are assumed to exist
     *
     * @param index To be evaluated as to whether the underlying model is
     *              hinted to exist
     */
    fun createModelIfNecessary(index: FormIndex) {
        if (index.isInForm()) {
            val e = getForm().getChild(index)
            if (e is GroupDef) {
                createModelForGroup(e, index, getForm())
            }
        }
    }

    fun incrementIndex(index: FormIndex, descend: Boolean): FormIndex {
        val indexes = Vector<Int>()
        val multiplicities = Vector<Int>()
        val elements = Vector<IFormElement>()

        if (index.isEndOfFormIndex()) {
            return index
        } else if (index.isBeginningOfFormIndex()) {
            if (form.getChildren() == null || form.getChildren()!!.size == 0) {
                return FormIndex.createEndOfFormIndex()
            }
        } else {
            form.collapseIndex(index, indexes, multiplicities, elements)
        }

        incrementHelper(indexes, multiplicities, elements, descend)

        return if (indexes.size == 0) {
            FormIndex.createEndOfFormIndex()
        } else {
            form.buildIndex(indexes, multiplicities, elements)
        }
    }

    private fun incrementHelper(
        indexes: Vector<Int>,
        multiplicities: Vector<Int>,
        elements: Vector<IFormElement>,
        descend: Boolean
    ) {
        val i = indexes.size - 1
        var exitRepeat = false
        var shouldDescend = descend

        if (i == -1 || elements.elementAt(i) is GroupDef) {
            if (i >= 0) {
                val group = elements.elementAt(i) as GroupDef
                if (group.isRepeat()) {
                    if (repeatStructure == REPEAT_STRUCTURE_NON_LINEAR) {
                        if (multiplicities.lastElement() == TreeReference.INDEX_REPEAT_JUNCTURE) {
                            shouldDescend = false
                            exitRepeat = true
                        }
                    } else {
                        if (form.getMainInstance()!!.resolveReference(form.getChildInstanceRef(elements, multiplicities)!!) == null) {
                            shouldDescend = false
                            exitRepeat = true
                        }
                    }
                }
            }

            if (shouldDescend && (i == -1 || elements.elementAt(i).getChildren()!!.size > 0)) {
                indexes.addElement(0)
                multiplicities.addElement(0)
                elements.addElement((if (i == -1) form else elements.elementAt(i)).getChild(0)!!)

                if (repeatStructure == REPEAT_STRUCTURE_NON_LINEAR) {
                    if (elements.lastElement() is GroupDef && (elements.lastElement() as GroupDef).isRepeat()) {
                        multiplicities.setElementAt(TreeReference.INDEX_REPEAT_JUNCTURE, multiplicities.size - 1)
                    }
                }

                return
            }
        }

        var j = i
        while (j >= 0) {
            if (!exitRepeat && elements.elementAt(j) is GroupDef && (elements.elementAt(j) as GroupDef).isRepeat()) {
                if (repeatStructure == REPEAT_STRUCTURE_NON_LINEAR) {
                    multiplicities.setElementAt(TreeReference.INDEX_REPEAT_JUNCTURE, j)
                } else {
                    multiplicities.setElementAt(multiplicities.elementAt(j) + 1, j)
                }
                return
            }

            val parent: IFormElement = if (j == 0) form else elements.elementAt(j - 1)
            val curIndex = indexes.elementAt(j)

            if (curIndex + 1 >= parent.getChildren()!!.size) {
                indexes.removeElementAt(j)
                multiplicities.removeElementAt(j)
                elements.removeElementAt(j)
                j--
                exitRepeat = false
            } else {
                indexes.setElementAt(curIndex + 1, j)
                multiplicities.setElementAt(0, j)
                elements.setElementAt(parent.getChild(curIndex + 1)!!, j)

                if (repeatStructure == REPEAT_STRUCTURE_NON_LINEAR) {
                    if (elements.lastElement() is GroupDef && (elements.lastElement() as GroupDef).isRepeat()) {
                        multiplicities.setElementAt(TreeReference.INDEX_REPEAT_JUNCTURE, multiplicities.size - 1)
                    }
                }

                return
            }
        }
    }

    fun decrementIndex(index: FormIndex): FormIndex {
        val indexes = Vector<Int>()
        val multiplicities = Vector<Int>()
        val elements = Vector<IFormElement>()

        if (index.isBeginningOfFormIndex()) {
            return index
        } else if (index.isEndOfFormIndex()) {
            if (form.getChildren() == null || form.getChildren()!!.size == 0) {
                return FormIndex.createBeginningOfFormIndex()
            }
        } else {
            form.collapseIndex(index, indexes, multiplicities, elements)
        }

        decrementHelper(indexes, multiplicities, elements)

        return if (indexes.size == 0) {
            FormIndex.createBeginningOfFormIndex()
        } else {
            form.buildIndex(indexes, multiplicities, elements)
        }
    }

    private fun decrementHelper(
        indexes: Vector<Int>,
        multiplicities: Vector<Int>,
        elements: Vector<IFormElement>
    ) {
        var i = indexes.size - 1

        if (i != -1) {
            val curIndex = indexes.elementAt(i)
            val curMult = multiplicities.elementAt(i)

            if (repeatStructure == REPEAT_STRUCTURE_NON_LINEAR &&
                elements.lastElement() is GroupDef && (elements.lastElement() as GroupDef).isRepeat() &&
                multiplicities.lastElement() != TreeReference.INDEX_REPEAT_JUNCTURE
            ) {
                multiplicities.setElementAt(TreeReference.INDEX_REPEAT_JUNCTURE, i)
                return
            } else if (repeatStructure != REPEAT_STRUCTURE_NON_LINEAR && curMult > 0) {
                multiplicities.setElementAt(curMult - 1, i)
            } else if (curIndex > 0) {
                indexes.setElementAt(curIndex - 1, i)
                multiplicities.setElementAt(0, i)
                elements.setElementAt((if (i == 0) form else elements.elementAt(i - 1)).getChild(curIndex - 1)!!, i)

                if (setRepeatNextMultiplicity(elements, multiplicities))
                    return
            } else {
                indexes.removeElementAt(i)
                multiplicities.removeElementAt(i)
                elements.removeElementAt(i)
                return
            }
        }

        var element: IFormElement = if (i < 0) form else elements.elementAt(i)
        while (element !is QuestionDef) {
            if (element.getChildren() == null || element.getChildren()!!.size == 0) {
                return
            }
            val subIndex = element.getChildren()!!.size - 1
            element = element.getChild(subIndex)!!

            indexes.addElement(subIndex)
            multiplicities.addElement(0)
            elements.addElement(element)

            if (setRepeatNextMultiplicity(elements, multiplicities))
                return
        }
    }

    private fun setRepeatNextMultiplicity(elements: Vector<IFormElement>, multiplicities: Vector<Int>): Boolean {
        val nodeRef = form.getChildInstanceRef(elements, multiplicities)
        val node = if (nodeRef != null) form.getMainInstance()!!.resolveReference(nodeRef) else null
        if (node == null || node.isRepeatable) {
            val mult: Int
            if (node == null) {
                mult = 0
            } else {
                val name = node.getName()!!
                val parentNode = form.getMainInstance()!!.resolveReference(nodeRef!!.getParentRef()!!)
                mult = parentNode!!.getChildMultiplicity(name)
            }
            multiplicities.setElementAt(
                if (repeatStructure == REPEAT_STRUCTURE_NON_LINEAR)
                    TreeReference.INDEX_REPEAT_JUNCTURE
                else
                    mult,
                multiplicities.size - 1
            )
            return true
        } else {
            return false
        }
    }

    /**
     * This method does a recursive check of whether there are any repeat guesses
     * in the element or its subtree. This is a necessary step when initializing
     * the model to be able to identify whether new repeats can be used.
     *
     * @param parent The form element to begin checking
     * @return true if the element or any of its descendants is a repeat
     * which has a count guess, false otherwise.
     */
    private fun containsRepeatGuesses(parent: IFormElement): Boolean {
        if (parent is GroupDef) {
            if (parent.isRepeat() && parent.getCountReference() != null) {
                return true
            }
        }

        val children = parent.getChildren() ?: return false
        val en: Enumeration<*> = children.elements()
        while (en.hasMoreElements()) {
            if (containsRepeatGuesses(en.nextElement() as IFormElement)) {
                return true
            }
        }
        return false
    }

    /**
     * Retrieve the serialized debug trace for the element at the specified
     * form index for the provided category of trigger
     *
     * Will enable debugging for the current form (currently doesn't disable
     * afterwards)
     *
     * @param index      The form index to be evaluated
     * @param category   The category of trigger/debug info being requested, like
     *                   calculate, relevant, etc.
     * @return the output of the provided serializer
     */
    fun getDebugInfo(index: FormIndex, category: String): String? {
        this.getForm().enableDebugTraces()

        val indexDebug: Hashtable<String, EvaluationTrace>? =
            this.getForm().getDebugTraceMap()[index.getReference()!!]
        if (indexDebug == null || indexDebug[category] == null) {
            return null
        }
        return TraceSerialization.serializeEvaluationTrace(
            indexDebug[category],
            TraceSerialization.TraceInfoType.FULL_PROFILE, false
        )
    }

    fun isNonCountedRepeat(): Boolean {
        return getForm().isNonCountedRepeat(getFormIndex())
    }

    companion object {
        /**
         * Repeats should be a prompted linear set of questions, either
         * with a fixed set of repetitions, or a prompt for creating a
         * new one.
         */
        const val REPEAT_STRUCTURE_LINEAR: Int = 1

        /**
         * Repeats should be a custom juncture point with centralized
         * "Create/Remove/Interact" hub.
         */
        const val REPEAT_STRUCTURE_NON_LINEAR: Int = 2

        private fun createModelForGroup(g: GroupDef, index: FormIndex, form: FormDef) {
            if (g.isRepeat() && g.getCountReference() != null) {
                val countRef = g.getConextualizedCountReference(index.getReference()!!)
                val count: IAnswerData? = form.getMainInstance()!!.resolveReference(countRef)!!.getValue()
                if (count != null) {
                    val fullcount: Int
                    try {
                        fullcount = IntegerData().cast(count.uncast()).getValue() as Int
                    } catch (iae: IllegalArgumentException) {
                        throw XPathTypeMismatchException(
                            "The repeat count value \"" +
                                    count.uncast().getString() + "\" at " +
                                    g.getConextualizedCountReference(index.getReference()!!).toString() +
                                    " must be a number!"
                        )
                    }

                    createModelIfBelowMaxCount(index, form, fullcount)
                }
            }
        }

        private fun createModelIfBelowMaxCount(index: FormIndex, form: FormDef, fullcount: Int) {
            val ref = form.getChildInstanceRef(index)
            val element = form.getMainInstance()!!.resolveReference(ref!!)
            if (element == null) {
                val instanceIndexOfDeepestRepeat = index.getLastRepeatInstanceIndex()
                if (instanceIndexOfDeepestRepeat == -1) {
                    throw RuntimeException("Attempting to expand a repeat for a form index where no repeats were present: $index")
                }
                if (instanceIndexOfDeepestRepeat < fullcount) {
                    try {
                        form.createNewRepeat(index)
                    } catch (ire: InvalidReferenceException) {
                        ire.printStackTrace()
                        throw RuntimeException("Invalid Reference while creating new repeat!" + ire.message)
                    }
                }
            }
        }
    }
}
