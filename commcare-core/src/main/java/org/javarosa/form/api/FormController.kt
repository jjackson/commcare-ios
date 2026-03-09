package org.javarosa.form.api

import org.javarosa.core.model.FormDef
import org.javarosa.core.model.FormIndex
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.services.transport.payload.ByteArrayPayload
import org.javarosa.model.xform.XFormSerializingVisitor
import org.javarosa.model.xform.XPathReference

import java.io.IOException
import java.util.Vector

/**
 * This class is a wrapper for Javarosa's FormEntryController. In theory, if you wanted to replace
 * javarosa as the form engine, you should only need to replace the methods in this file. Also, we
 * haven't wrapped every method provided by FormEntryController, only the ones we've needed so far.
 * Feel free to add more as necessary.
 *
 * @author carlhartung
 */
open class FormController(
    @JvmField protected val mFormEntryController: FormEntryController,
    private val mReadOnly: Boolean
) {

    /**
     * returns the event for the current FormIndex.
     */
    fun getEvent(): Int {
        return mFormEntryController.getModel().getEvent()
    }

    /**
     * returns the event for the given FormIndex.
     */
    fun getEvent(index: FormIndex): Int {
        return mFormEntryController.getModel().getEvent(index)
    }

    /**
     * @return true if this form session is in read only mode
     */
    fun isFormReadOnly(): Boolean {
        return mReadOnly
    }

    /**
     * @return current FormIndex.
     */
    fun getFormIndex(): FormIndex {
        return mFormEntryController.getModel().getFormIndex()
    }

    /**
     * Return the languages supported by the currently loaded form.
     *
     * @return Array of Strings containing the languages embedded in the XForm.
     */
    fun getLanguages(): Array<String?>? {
        return mFormEntryController.getModel().getLanguages()
    }

    /**
     * @return A String containing the title of the current form.
     */
    fun getFormTitle(): String {
        return mFormEntryController.getModel().getFormTitle()
    }

    fun getFormID(): Int {
        return mFormEntryController.getModel().getForm().getID()
    }

    /**
     * Returns a caption prompt for the given index. This is used to create a multi-question per
     * screen view.
     */
    fun getCaptionPrompt(index: FormIndex): FormEntryCaption {
        return mFormEntryController.getModel().getCaptionPrompt(index)
    }

    /**
     * Return the caption for the current FormIndex. This is usually used for a repeat prompt.
     */
    fun getCaptionPrompt(): FormEntryCaption {
        return mFormEntryController.getModel().getCaptionPrompt()
    }

    fun postProcessInstance() {
        mFormEntryController.getModel().getForm().postProcessInstance()
    }

    fun getInstance(): FormInstance {
        return mFormEntryController.getModel().getForm().getInstance()!!
    }

    /**
     * Tests if the FormIndex 'index' is located inside a group that is marked as a "field-list"
     *
     * @return true if index is in a "field-list". False otherwise.
     */
    private fun indexIsInFieldList(index: FormIndex): Boolean {
        val fieldListHost = getHostWithAppearance(index, FormEntryController.FIELD_LIST)
        return fieldListHost != null
    }

    /**
     * Tests if the current FormIndex is located inside a group that is marked as a "field-list"
     *
     * @return true if index is in a "field-list". False otherwise.
     */
    fun indexIsInFieldList(): Boolean {
        return indexIsInFieldList(getFormIndex())
    }

    /**
     * Tests if the FormIndex 'index' is located inside a group that is marked with "compact" appearance
     *
     * @return true if index is in a group with appearance "compact". False otherwise.
     */
    fun indexIsInCompact(index: FormIndex): Boolean {
        return getHostWithAppearance(index, FormEntryController.COMPACT) != null
    }

    /**
     * Attempts to save answer at the current FormIndex into the data model.
     */
    fun answerQuestion(data: IAnswerData?): Int {
        return mFormEntryController.answerQuestion(data)
    }

    /**
     * Attempts to save answer into the given FormIndex into the data model.
     */
    fun answerQuestion(index: FormIndex, data: IAnswerData?): Int {
        return mFormEntryController.answerQuestion(index, data)
    }

    fun checkCurrentQuestionConstraint(index: FormIndex): Int {
        return mFormEntryController.checkQuestionConstraint(index, getQuestionPrompt(index).getAnswerValue())
    }

    /**
     * saveAnswer attempts to save the current answer into the data model without doing any
     * constraint checking. Only use this if you know what you're doing. For normal form filling you
     * should always use answerQuestion or answerCurrentQuestion.
     *
     * @return true if saved successfully, false otherwise.
     */
    fun saveAnswer(index: FormIndex, data: IAnswerData?): Boolean {
        return mFormEntryController.saveAnswer(index, data)
    }

    /**
     * Navigates forward in the form, expanding any repeats encountered.
     *
     * @return the next event that should be handled by a view.
     */
    fun stepToNextEvent(stepOverGroup: Boolean): Int {
        val nextIndex = getNextFormIndex(getFormIndex(), stepOverGroup, true)
        return jumpToIndex(nextIndex)
    }

    /**
     * Get the FormIndex after the given one.
     */
    fun getNextFormIndex(index: FormIndex, stepOverGroup: Boolean): FormIndex {
        return getNextFormIndex(index, stepOverGroup, true)
    }

    /**
     * Get the FormIndex after the given one.
     */
    fun getNextFormIndex(index: FormIndex, stepOverGroup: Boolean, expandRepeats: Boolean): FormIndex {
        //TODO: this won't actually catch the case where there are nested field lists properly
        if (mFormEntryController.getModel().getEvent(index) == FormEntryController.EVENT_GROUP && indexIsInFieldList(index) && stepOverGroup) {
            return getIndexPastGroup(index)
        } else {
            val nextIndex = mFormEntryController.getNextIndex(index, expandRepeats)
            if (mFormEntryController.getModel().getEvent(nextIndex) == FormEntryController.EVENT_PROMPT_NEW_REPEAT && this.mReadOnly) {
                return getNextFormIndex(nextIndex, stepOverGroup, expandRepeats)
            }
            return nextIndex
        }
    }

    /**
     * From the given FormIndex which must be a group element,
     * find the next index which is outside of that group.
     *
     * @return FormIndex
     */
    private fun getIndexPastGroup(index: FormIndex): FormIndex {
        // Walk until the next index is outside of this one.
        var walker = index
        while (FormIndex.isSubElement(index, walker)) {
            walker = getNextFormIndex(walker, false)
        }
        return walker
    }

    /**
     * Navigates backward in the form.
     * Used by Formplayer
     * @return FormIndex that we're currently on
     */
    fun getPreviousFormIndex(): FormIndex {
        stepToPreviousEvent()
        return getFormIndex()
    }

    /**
     * Navigates backward in the form.
     *
     * @return the event that should be handled by a view.
     */
    fun stepToPreviousEvent(): Int {
        /*
         * Right now this will always skip to the beginning of a group if that group is represented
         * as a 'field-list'. Should a need ever arise to step backwards by only one step in a
         * 'field-list', this method will have to be updated.
         */

        val event = mFormEntryController.stepToPreviousEvent()

        if (event == FormEntryController.EVENT_PROMPT_NEW_REPEAT &&
            this.mReadOnly
        ) {
            return stepToPreviousEvent()
        }

        // If after we've stepped, we're in a field-list, jump back to the beginning of the group
        val host = getHostWithAppearance(getFormIndex(), FormEntryController.FIELD_LIST)
        if (host != null) {
            return mFormEntryController.jumpToIndex(host)
        }

        return mFormEntryController.getModel().getEvent()
    }

    /**
     * Retrieves the index of the Group which hosts child index and is marked with given appearanceTag, null otherwise
     */
    private fun getHostWithAppearance(child: FormIndex, appearanceTag: String): FormIndex? {
        val event = mFormEntryController.getModel().getEvent(child)

        if (event == FormEntryController.EVENT_QUESTION || event == FormEntryController.EVENT_GROUP || event == FormEntryController.EVENT_REPEAT) {
            // caption[0..len-1]
            // caption[len-1] == the event itself
            // caption[len-2] == the groups containing this group
            val captions = mFormEntryController.getModel().getCaptionHierarchy(child)

            //This starts at the beginning of the hierarchy, so it'll catch the top-level
            //host index.
            for (caption in captions) {
                val parentIndex = caption.getIndex()!!
                if (mFormEntryController.isHostWithAppearance(parentIndex, appearanceTag)) {
                    return parentIndex
                }
            }

            //none of this node's parents are marked with appearanceTag
            return null
        } else {
            // Non-host elements can't have hosts marked as appearanceTag.
            return null
        }
    }

    /**
     * Jumps to a given FormIndex.
     *
     * @return EVENT for the specified Index.
     */
    fun jumpToIndex(index: FormIndex): Int {
        return mFormEntryController.jumpToIndex(index)
    }

    /**
     * Creates a new repeated instance of the group referenced by the current FormIndex.
     */
    fun newRepeat() {
        mFormEntryController.newRepeat()
    }

    /**
     * Sets the current language.
     */
    fun setLanguage(language: String) {
        mFormEntryController.setLanguage(language)
    }

    /**
     * Expand any unexpanded repeats at the given FormIndex
     */
    fun expandRepeats(index: FormIndex) {
        mFormEntryController.expandRepeats(index)
    }

    /**
     * getQuestionPrompts for the current index
     */
    fun getQuestionPrompts(): Array<FormEntryPrompt> {
        return mFormEntryController.getQuestionPrompts()
    }

    /**
     * Returns an array of relevant question prompts that should be displayed as a single screen.
     * If the given form index is a question, it is returned. Otherwise if the
     * given index is a field list (and _only_ when it is a field list)
     */
    fun getQuestionPrompts(currentIndex: FormIndex): Array<FormEntryPrompt> {
        return mFormEntryController.getQuestionPrompts(currentIndex)
    }

    fun getQuestionPrompt(index: FormIndex): FormEntryPrompt {
        return mFormEntryController.getModel().getQuestionPrompt(index)
    }

    fun getQuestionPrompt(): FormEntryPrompt {
        return mFormEntryController.getModel().getQuestionPrompt()
    }

    /**
     * Returns an array of FormEntryCaptions for current FormIndex.
     */
    fun getGroupsForCurrentIndex(): Array<FormEntryCaption> {
        // return an empty array if you ask for something impossible
        if (!(mFormEntryController.getModel().getEvent() == FormEntryController.EVENT_QUESTION
                    || mFormEntryController.getModel().getEvent() == FormEntryController.EVENT_PROMPT_NEW_REPEAT
                    || mFormEntryController.getModel().getEvent() == FormEntryController.EVENT_GROUP)
        ) {
            return emptyArray()
        }

        // the first caption is the question, so we skip it if it's an EVENT_QUESTION
        // otherwise, the first caption is a group so we start at index 0
        val lastquestion = if (mFormEntryController.getModel().getEvent() == FormEntryController.EVENT_PROMPT_NEW_REPEAT
            || mFormEntryController.getModel().getEvent() == FormEntryController.EVENT_GROUP
        ) {
            0
        } else {
            1
        }

        val v = mFormEntryController.getModel().getCaptionHierarchy()
        val groups = Array(v.size - lastquestion) { i -> v[i] }
        return groups
    }

    /**
     * The closest group the prompt belongs to.
     *
     * @return FormEntryCaption
     */
    private fun getLastGroup(): FormEntryCaption? {
        val groups = mFormEntryController.getModel().getCaptionHierarchy()
        return if (groups.isEmpty()) {
            null
        } else {
            groups[groups.size - 1]
        }
    }

    /**
     * The repeat count of closest group the prompt belongs to.
     */
    fun getLastRepeatCount(): Int {
        return if (getLastGroup() != null) {
            getLastGroup()!!.getMultiplicity()
        } else {
            -1
        }
    }

    /**
     * The text of closest group the prompt belongs to.
     */
    fun getLastGroupText(): String? {
        return if (getLastGroup() != null) {
            getLastGroup()!!.getLongText()
        } else {
            null
        }
    }

    /**
     * Find the portion of the form that is to be submitted
     */
    private fun getSubmissionDataReference(): XPathReference {
        return XPathReference("/")
    }

    /**
     * Extract the portion of the form that should be uploaded to the server.
     */
    @Throws(IOException::class)
    fun getSubmissionXml(): ByteArrayPayload {
        val instance = getInstance()
        val serializer = XFormSerializingVisitor()

        return serializer.createSerializedPayload(
            instance,
            getSubmissionDataReference()
        ) as ByteArrayPayload
    }

    /**
     * Traverse the submission looking for the first matching tag in depth-first order.
     */
    private fun findDepthFirst(parent: TreeElement, name: String): TreeElement? {
        val len = parent.getNumChildren()
        for (i in 0 until len) {
            val e = parent.getChildAt(i)!!
            if (name == e.getName()) {
                return e
            } else if (e.getNumChildren() != 0) {
                val v = findDepthFirst(e, name)
                if (v != null) return v
            }
        }
        return null
    }

    /**
     * Get the OpenRosa required metadata of the portion of the form being submitted
     */
    fun getSubmissionMetadata(): InstanceMetadata {
        val formDef = mFormEntryController.getModel().getForm()
        val rootElement = formDef.getInstance()!!.getRoot()

        val trueSubmissionElement = rootElement

        // and find the depth-first meta block in this...
        val e = findDepthFirst(trueSubmissionElement, "meta")

        var instanceId: String? = null

        if (e != null) {
            val v: Vector<AbstractTreeElement>

            // instance id...
            v = e.getChildrenWithName(INSTANCE_ID)
            if (v.size == 1) {
                instanceId = v[0].getValue()!!.uncast().toString()
            }
        }

        return InstanceMetadata(instanceId)
    }

    fun getFormEntrySessionString(): String {
        return mFormEntryController.getFormEntrySessionString()
    }

    fun getFormEntryController(): FormEntryController {
        return mFormEntryController
    }

    /**
     * OpenRosa metadata of a form instance.
     *
     * Contains the values for the required metadata
     * fields and nothing else.
     *
     * @author mitchellsundt@gmail.com
     */
    class InstanceMetadata internal constructor(val instanceId: String?)

    companion object {
        /**
         * OpenRosa metadata tag names.
         */
        private const val INSTANCE_ID = "instanceID"
    }
}
