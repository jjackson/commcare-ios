package org.javarosa.core.test

import org.javarosa.core.model.FormDef
import org.javarosa.core.model.FormIndex
import org.javarosa.core.model.GroupDef
import org.javarosa.core.model.QuestionDef
import org.javarosa.form.api.FormEntryController
import org.javarosa.form.api.FormEntryModel
import org.javarosa.xform.parse.QuestionExtensionParser
import org.javarosa.xform.util.XFormUtils

/**
 * This class sets up everything you need to perform tests on the models and form elements found in JR (such
 * as QuestionDef, FormDef, Selections, etc).  It exposes hooks to the FormEntryController,FormEntryModel and
 * FormDef (all the toys you need to test IFormElements, provide answers to questions and test constraints, etc)
 *
 * REMEMBER to set the
 * PLM: ^^^ AAAhhh, set what?!! What do I need to remember to set?
 */
class FormParseInit {
    private var xform: FormDef? = null
    private lateinit var fec: FormEntryController
    private lateinit var femodel: FormEntryModel

    constructor(formPath: String) {
        initFormDef(formPath, null)
        initFormEntryObjects()
    }

    constructor(formPath: String, extensionParsers: ArrayList<QuestionExtensionParser>) {
        initFormDef(formPath, extensionParsers)
        initFormEntryObjects()
    }

    constructor(fd: FormDef) {
        xform = fd
        initFormEntryObjects()
    }

    private fun initFormDef(formName: String, extensionParsers: ArrayList<QuestionExtensionParser>?) {
        val inputStream = this.javaClass.getResourceAsStream(formName)

        if (inputStream == null) {
            val errorMessage = "Error: the file '$formName' could not be found!"
            System.err.println(errorMessage)
            throw RuntimeException(errorMessage)
        }
        xform = if (extensionParsers != null) {
            XFormUtils.getFormFromInputStream(inputStream, extensionParsers)
        } else {
            XFormUtils.getFormFromInputStream(inputStream)
        }

        if (xform == null) {
            println("\n\n==================================\nERROR: XForm has failed validation!!")
        }
    }

    private fun initFormEntryObjects() {
        femodel = FormEntryModel(xform!!)
        fec = FormEntryController(femodel)
    }

    /**
     * @return the first questionDef found in the form.
     */
    fun getFirstQuestionDef(): QuestionDef? {
        // go to the beginning of the form
        fec.jumpToIndex(FormIndex.createBeginningOfFormIndex())
        do {
            val fep = femodel.getCaptionPrompt()
            if (fep.getFormElement() is QuestionDef) {
                return fep.getFormElement() as QuestionDef
            }
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)

        return null
    }

    /**
     * Gets the current question based off of
     *
     * @return the question after getFirstQuestionDef()
     */
    fun getCurrentQuestion(): QuestionDef? {
        val fep = femodel.getCaptionPrompt()
        if (fep.getFormElement() is QuestionDef) {
            return fep.getFormElement() as QuestionDef
        }
        return null
    }

    /**
     * @return the next question in the form (QuestionDef), or null if the end of the form has been reached.
     */
    fun getNextQuestion(): QuestionDef? {
        // jump to next event and check for end of form
        if (fec.stepToNextEvent() == FormEntryController.EVENT_END_OF_FORM) {
            return null
        }

        val fep = getFormEntryModel().getCaptionPrompt()

        do {
            if (fep.getFormElement() is QuestionDef)
                return fep.getFormElement() as QuestionDef
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)

        return null
    }

    /**
     * @return the FormDef for this form
     */
    fun getFormDef(): FormDef? = xform

    fun getFormEntryModel(): FormEntryModel = fec.getModel()

    fun getFormEntryController(): FormEntryController = fec

    /*
     * Makes an 'extremely basic' print out of the xform model.
     */
    fun printStuff(): String {
        var stuff = ""
        // go to the beginning of the form
        fec.jumpToIndex(FormIndex.createBeginningOfFormIndex())
        do {
            val fep = femodel.getCaptionPrompt()
            var choiceFlag = false

            if (fep.getFormElement() is QuestionDef) {
                stuff += "\t[Type:QuestionDef, "
                val s = (fep.getFormElement() as QuestionDef).getChoices()
                stuff += "ContainsChoices: ${if (s != null && s.size > 0) "true " else "false"}, "
                if (s != null && s.size > 0) choiceFlag = true
            } else if (fep.getFormElement() is FormDef) {
                stuff += "\t[Type:FormDef, "
            } else if (fep.getFormElement() is GroupDef) {
                stuff += "\t[Type:GroupDef, "
            } else {
                stuff += "\t[Type:Unknown]\n"
                continue
            }

            stuff += "ID:${fep.getFormElement()!!.getID()}, TextID:${fep.getFormElement()!!.getTextID()},InnerText:${fep.getFormElement()!!.getLabelInnerText()}"
            if (choiceFlag) {
                stuff += "] \n\t\t---Choices:${(fep.getFormElement() as QuestionDef).getChoices()}\n"
            } else {
                stuff += "]\n"
            }
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)

        return stuff
    }
}
