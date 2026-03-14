package org.javarosa.core.model.test

import org.javarosa.core.model.Constants
import org.javarosa.core.model.QuestionDef
import org.javarosa.core.reference.InvalidReferenceException
import org.javarosa.core.reference.ReferenceManager
import org.javarosa.core.reference.ResourceReferenceFactory
import org.javarosa.core.reference.RootTranslator
import org.javarosa.core.services.PrototypeManager
import org.javarosa.core.test.FormParseInit
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.form.api.FormEntryPrompt
import org.javarosa.model.xform.XPathReference
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.ArrayList

class QuestionDefTest {
    var q: QuestionDef? = null
    var fep: FormEntryPrompt? = null
    var fpi: FormParseInit? = null

    @Before
    fun initStuff() {
        fpi = FormParseInit("/ImageSelectTester.xhtml")
        q = fpi!!.getFirstQuestionDef()
        fep = FormEntryPrompt(fpi!!.getFormDef()!!, fpi!!.getFormEntryModel().getFormIndex())
    }

    companion object {
        @JvmStatic
        val pf: PrototypeFactory

        init {
            PrototypeManager.registerPrototype("org.javarosa.model.xform.XPathReference")
            pf = ExtUtil.defaultPrototypes()
        }
    }

    private fun testSerialize(q: QuestionDef, msg: String) {
        //ExternalizableTest.testExternalizable(q, this, pf, "QuestionDef [$msg]")
    }

    @Test
    fun testConstructors() {
        var q: QuestionDef

        q = QuestionDef()
        if (q.getID() != -1) {
            fail("QuestionDef not initialized properly (default constructor)")
        }
        testSerialize(q, "a")

        q = QuestionDef(17, Constants.CONTROL_RANGE)
        if (q.getID() != 17) {
            fail("QuestionDef not initialized properly")
        }
        testSerialize(q, "b")
    }

    fun newRef(xpath: String): XPathReference {
        pf.addClass(XPathReference::class.java)
        return XPathReference(xpath)
    }

    @Test
    fun testAccessorsModifiers() {
        val q = QuestionDef()

        q.setID(45)
        if (q.getID() != 45) {
            fail("ID getter/setter broken")
        }
        testSerialize(q, "c")

        val ref = newRef("/data")
        q.setBind(ref)
        if (q.getBind() !== ref) {
            fail("Ref getter/setter broken")
        }
        testSerialize(q, "e")

        q.setControlType(Constants.CONTROL_SELECT_ONE)
        if (q.getControlType() != Constants.CONTROL_SELECT_ONE) {
            fail("Control type getter/setter broken")
        }
        testSerialize(q, "g")

        q.setAppearanceAttr("minimal")
        if ("minimal" != q.getAppearanceAttr()) {
            fail("Appearance getter/setter broken")
        }
        testSerialize(q, "h")
    }

    @Test
    fun testChild() {
        val q = QuestionDef()

        if (q.getChildren() != null) {
            fail("Question has children")
        }

        try {
            q.setChildren(ArrayList())
            fail("Set a question's children without exception")
        } catch (ise: IllegalStateException) {
            //expected
        }

        try {
            q.addChild(QuestionDef())
            fail("Added a child to a question without exception")
        } catch (ise: IllegalStateException) {
            //expected
        }
    }

    @Test
    fun testReferences() {
        val q = fpi!!.getFirstQuestionDef()
        var fep = fpi!!.getFormEntryModel().getQuestionPrompt()

        val l = fpi!!.getFormDef()!!.getLocalizer()!!
        l.setDefaultLocale(l.availableLocales[0])
        l.setLocale(l.availableLocales[0])

        val audioURI = fep.getAudioText()
        var ref: String

        ReferenceManager.instance().addReferenceFactory(ResourceReferenceFactory())
        ReferenceManager.instance().addRootTranslator(RootTranslator("jr://audio/", "jr://resource/"))
        try {
            val r = ReferenceManager.instance().DeriveReference(audioURI)
            ref = r.getURI()
            if (ref != "jr://resource/hah.mp3") {
                fail("Root translation failed.")
            }
        } catch (ire: InvalidReferenceException) {
            fail("There was an Invalid Reference Exception:" + ire.message)
            ire.printStackTrace()
        }

        ReferenceManager.instance().addRootTranslator(RootTranslator("jr://images/", "jr://resource/"))
        val nextQ = fpi!!.getNextQuestion()
        fep = fpi!!.getFormEntryModel().getQuestionPrompt()
        val imURI = fep.getImageText()
        try {
            val r = ReferenceManager.instance().DeriveReference(imURI)
            ref = r.getURI()
            if (ref != "jr://resource/four.gif") {
                fail("Root translation failed.")
            }
        } catch (ire: InvalidReferenceException) {
            fail("There was an Invalid Reference Exception:" + ire.message)
            ire.printStackTrace()
        }
    }

    fun retrieveQ(): QuestionDef? = q

    fun assignQ(q: QuestionDef) {
        this.q = q
    }
}
