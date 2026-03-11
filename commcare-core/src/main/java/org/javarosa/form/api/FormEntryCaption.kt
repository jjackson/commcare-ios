package org.javarosa.form.api

import org.commcare.cases.util.StringUtils
import org.javarosa.core.model.FormDef
import org.javarosa.core.model.FormIndex
import org.javarosa.core.model.GroupDef
import org.javarosa.core.model.IFormElement
import org.javarosa.core.services.locale.Localization
import org.javarosa.core.services.locale.Localizer
import kotlin.jvm.JvmField


/**
 * This class gives you all the information you need to display a caption when
 * your current FormIndex references a GroupEvent, RepeatPromptEvent, or
 * RepeatEvent.
 *
 * @author Simon Kelly
 */
open class FormEntryCaption {

    @JvmField
    protected var form: FormDef? = null

    @JvmField
    protected var index: FormIndex? = null

    /**
     * Note: DummyFormEntryPrompt (Java test) accesses this.element directly as a field.
     * @JvmField is required for Java field access. However, @JvmField cannot be applied
     * to open properties. Since FormEntryPrompt (which is Kotlin) is the only production
     * subclass, and DummyFormEntryPrompt (Java test) extends FormEntryPrompt, we use
     * protected visibility. DummyFormEntryPrompt accesses it through the Java field
     * generated from this property.
     */
    @JvmField
    protected var element: IFormElement? = null

    private var textID: String? = null

    /**
     * This empty constructor exists for convenience of any supertypes of this
     * prompt
     */
    constructor()

    /**
     * Creates a FormEntryCaption for the element at the given index in the form.
     */
    constructor(form: FormDef, index: FormIndex) {
        this.form = form
        this.index = index
        this.element = form.getChild(index)
        this.textID = this.element!!.getTextID()
    }

    /**
     * Convenience method
     * Get longText form of text for THIS element (if available)
     * !!Falls back to default form if 'long' form does not exist.!!
     * Use getSpecialFormQuestionText() if you want short form only.
     *
     * @return longText form
     */
    fun getLongText(): String? {
        return getQuestionText(getTextID())
    }

    /**
     * Convenience method
     * Get shortText form of text for THIS element (if available)
     * !!Falls back to default form if 'short' form does not exist.!!
     * Use getSpecialFormQuestionText() if you want short form only.
     *
     * @return shortText form
     */
    fun getShortText(): String? {
        var returnText = getSpecialFormQuestionText(getTextID(), TEXT_FORM_SHORT)
        if (returnText == null) {
            returnText = getLongText()
        }
        return returnText
    }

    /**
     * Convenience method
     * Get audio URI from Text form for THIS element (if available)
     *
     * @return audio URI form stored in current locale of Text, returns null if not available
     */
    fun getAudioText(): String? {
        return getSpecialFormQuestionText(getTextID(), TEXT_FORM_AUDIO)
    }

    /**
     * Convenience method
     * Get image URI form of text for THIS element (if available)
     *
     * @return URI of image form stored in current locale of Text, returns null if not available
     */
    fun getImageText(): String? {
        return getSpecialFormQuestionText(getTextID(), TEXT_FORM_IMAGE)
    }

    /**
     * Convenience method
     * Get video URI form of text for THIS element (if available)
     *
     * @return URI of video form stored in current locale of Text, returns null if not available
     */
    fun getVideoText(): String? {
        return this.getSpecialFormQuestionText(this.getTextID(), "video")
    }

    /**
     * Convenience method
     * Get the markdown styled text (if available)
     *
     * @return Text with MarkDown styling
     */
    fun getMarkdownText(): String? {
        return getSpecialFormQuestionText(getTextID(), TEXT_FORM_MARKDOWN)
    }

    /**
     * Attempts to return question text for this element.
     * Will check for text in the following order:
     * Localized Text (long form) -> Localized Text (no special form)
     * If no textID is specified, method will return THIS element's labelInnerText.
     *
     * @param textID - The textID of the text you're trying to retrieve. if `textID == null` will get LabelInnerText for current element
     * @return Question Text. `null` if no text for this element exists (after all fallbacks).
     * @throws RuntimeException if this method is called on an element that is NOT a QuestionDef
     */
    open fun getQuestionText(textID: String?): String? {
        var tid = textID
        if ("" == tid) {
            tid = null //to make things look clean
        }

        //check for the null id case and return labelInnerText if it is so.
        if (tid == null) return substituteStringArgs(element!!.getLabelInnerText())

        //otherwise check for 'long' form of the textID, then for the default form and return
        var returnText: String?
        returnText = getIText(tid, "long")
        if (returnText == null) returnText = getIText(tid, null)

        return substituteStringArgs(returnText)
    }

    /**
     * Same as getQuestionText(String textID), but for the current element textID;
     *
     * @return Question Text
     */
    fun getQuestionText(): String? {
        return getQuestionText(getTextID())
    }

    /**
     * This method is generally used to retrieve special forms of a
     * textID, e.g. "audio", "video", etc.
     *
     * @param textID - The textID of the text you're trying to retrieve.
     * @param form   - special text form of textID you're trying to retrieve.
     * @return Special Form Question Text. `null` if no text for this element exists (with the specified special form).
     * @throws RuntimeException if this method is called on an element that is NOT a QuestionDef
     */
    open fun getSpecialFormQuestionText(textID: String?, form: String?): String? {
        if (textID == null || textID == "") return null

        val returnText = getIText(textID, form)

        return substituteStringArgs(returnText)
    }

    /**
     * Same as getSpecialFormQuestionText(String textID,String form) except that the
     * textID defaults to the textID of the current element.
     *
     * @param form - special text form of textID you're trying to retrieve.
     * @return Special Form Question Text. `null` if no text for this element exists (with the specified special form).
     * @throws RuntimeException if this method is called on an element that is NOT a QuestionDef
     */
    fun getSpecialFormQuestionText(form: String?): String? {
        return getSpecialFormQuestionText(getTextID(), form)
    }

    /**
     * @param textID - the textID of the text you'd like to retrieve
     * @param form   - the special form (e.g. "audio","long", etc) of the text
     * @return the IText for the parameters specified.
     */
    protected open fun getIText(textID: String?, form: String?): String? {
        var returnText: String? = null
        if (textID == null || textID == "") return null
        if (form != null && form != "") {
            try {
                returnText = localizer().getRawText(localizer().locale, "$textID;$form")
            } catch (npe: NullPointerException) {
            }
        } else {
            try {
                returnText = localizer().getRawText(localizer().locale, textID)
            } catch (npe: NullPointerException) {
            }
        }
        return returnText
    }

    //TODO: this is explicitly missing integration with the new multi-media support
    //TODO: localize the default captions
    fun getRepeatText(typeKey: String): String? {
        val g = element as GroupDef
        if (!g.isRepeat()) {
            throw RuntimeException("not a repeat")
        }

        val title = getLongText()
        val count = getNumRepetitions()

        var caption: String? = null
        if ("mainheader" == typeKey) {
            caption = getCaptionText(g.mainHeader)
            if (caption == null) {
                return title
            }
        } else if ("add" == typeKey) {
            caption = getCaptionText(g.addCaption)
            if (caption == null) {
                return Localization.getWithDefault(
                    "repeat.dialog.add.another",
                    arrayOf(title ?: ""),
                    "Add another $title"
                )
            }
        } else if ("add-empty" == typeKey) {
            caption = getCaptionText(g.addEmptyCaption)
            if (caption == null) {
                caption = getCaptionText(g.addCaption)
            }
            if (caption == null) {
                return Localization.getWithDefault(
                    "repeat.dialog.add.new",
                    arrayOf(title ?: ""),
                    "Add a new $title"
                )
            }
        } else if ("del" == typeKey) {
            caption = getCaptionText(g.delCaption)
            if (caption == null) {
                return "Delete $title"
            }
        } else if ("done" == typeKey) {
            caption = getCaptionText(g.doneCaption)
            if (caption == null) {
                return "Done"
            }
        } else if ("done-empty" == typeKey) {
            caption = getCaptionText(g.doneEmptyCaption)
            if (caption == null) {
                caption = g.doneCaption
            }
            if (caption == null) {
                return "Skip"
            }
        } else if ("delheader" == typeKey) {
            caption = getCaptionText(g.delHeader)
            if (caption == null) {
                return "Delete which $title?"
            }
        }

        val vars = HashMap<String, Any>()
        vars["name"] = title ?: ""
        vars["n"] = count
        return form!!.fillTemplateString(caption!!, index!!.getReference()!!, vars)
    }

    private fun getCaptionText(textIdOrText: String?): String? {
        if (!StringUtils.isEmpty(textIdOrText)) {
            val returnText = getIText(textIdOrText, null)
            if (returnText != null) {
                return substituteStringArgs(returnText)
            }
        }
        return substituteStringArgs(textIdOrText)
    }

    //this should probably be somewhere better
    fun getNumRepetitions(): Int {
        return form!!.getNumRepetitions(index!!)
    }

    fun getRepetitionText(newrep: Boolean): String? {
        return getRepetitionText("header", index!!, newrep)
    }

    private fun getRepetitionText(type: String, index: FormIndex, newrep: Boolean): String? {
        if (element is GroupDef && (element as GroupDef).isRepeat() && index.getElementMultiplicity() >= 0) {
            val g = element as GroupDef

            val title = getLongText()
            val ix = index.getElementMultiplicity() + 1
            val count = getNumRepetitions()

            var caption: String? = null
            if ("header" == type) {
                caption = getCaptionText(g.entryHeader)
            } else if ("choose" == type) {
                caption = getCaptionText(g.chooseCaption)
                if (caption == null) {
                    caption = getCaptionText(g.entryHeader)
                }
            }
            if (caption == null) {
                return if (title == null) "$ix/$count" else "$title $ix/$count"
            }

            val vars = HashMap<String, Any>()
            vars["name"] = title ?: ""
            vars["i"] = ix
            vars["n"] = count
            vars["new"] = newrep
            return form!!.fillTemplateString(caption, index.getReference()!!, vars)
        } else {
            return null
        }
    }

    fun getRepetitionsText(): ArrayList<String> {
        val g = element as GroupDef
        if (!g.isRepeat()) {
            throw RuntimeException("not a repeat")
        }

        val numRepetitions = getNumRepetitions()
        val reps = ArrayList<String>()
        for (i in 0 until numRepetitions) {
            reps.add(getRepetitionText("choose", form!!.descendIntoRepeat(index!!, i), false)!!)
        }
        return reps
    }

    class RepeatOptions {
        var header: String? = null
        var add: String? = null
        var delete: String? = null
        var done: String? = null
        var delete_header: String? = null
    }

    /**
     * Used by touchforms
     */
    @Suppress("unused")
    fun getRepeatOptions(): RepeatOptions {
        val ro = RepeatOptions()
        val hasRepetitions = getNumRepetitions() > 0

        ro.header = getRepeatText("mainheader")

        ro.add = null
        if (form!!.canCreateRepeat(form!!.getChildInstanceRef(index!!)!!, index!!)) {
            ro.add = getRepeatText(if (hasRepetitions) "add" else "add-empty")
        }
        ro.delete = null
        ro.delete_header = null
        if (hasRepetitions) {
            ro.delete = getRepeatText("del")
            ro.delete_header = getRepeatText("delheader")
        }
        ro.done = getRepeatText(if (hasRepetitions) "done" else "done-empty")

        return ro
    }

    fun getAppearanceHint(): String? {
        return element!!.getAppearanceAttr()
    }

    protected open fun substituteStringArgs(templateStr: String?): String? {
        if (templateStr == null) {
            return null
        }
        return form!!.fillTemplateString(templateStr, index!!.getReference()!!)
    }

    fun getMultiplicity(): Int {
        return index!!.getElementMultiplicity()
    }

    fun getFormElement(): IFormElement? {
        return element
    }

    /**
     * @return true if this represents a `<repeat>` element
     */
    fun repeats(): Boolean {
        return if (element is GroupDef) {
            (element as GroupDef).isRepeat()
        } else {
            false
        }
    }

    fun getIndex(): FormIndex? {
        return index
    }

    open fun localizer(): Localizer {
        return this.form!!.getLocalizer()!!
    }

    protected open fun getTextID(): String? {
        return this.textID
    }

    companion object {
        const val TEXT_FORM_LONG: String = "long"
        const val TEXT_FORM_SHORT: String = "short"
        const val TEXT_FORM_AUDIO: String = "audio"
        const val TEXT_FORM_IMAGE: String = "image"
        const val TEXT_FORM_VIDEO: String = "video"
        const val TEXT_FORM_MARKDOWN: String = "markdown"
    }
}
