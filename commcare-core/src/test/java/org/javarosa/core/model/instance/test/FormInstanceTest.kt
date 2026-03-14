package org.javarosa.core.model.instance.test

import org.javarosa.core.model.FormIndex
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.test.FormParseInit
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.LivePrototypeFactory
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.form.api.FormEntryController
import org.javarosa.form.api.FormEntryModel
import org.javarosa.test_utils.ExprEvalUtils
import org.javarosa.xpath.parser.XPathSyntaxException
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.Date

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
class FormInstanceTest {

    companion object {
        private val pf = LivePrototypeFactory()
    }

    /**
     * Serialize/deserialize a form instance, ensuring the resulting roots are equal
     */
    @Test
    fun testInstanceSerialization() {
        val fpi = FormParseInit("/xform_tests/test_repeat_insert_duplicate_triggering.xml")
        val fec = fpi.getFormEntryController()
        fec.jumpToIndex(FormIndex.createBeginningOfFormIndex())

        val fd = fpi.getFormDef()!!
        fd.initialize(true, DummyInstanceInitializationFactory())

        val instance = fd.getMainInstance()!!
        val reSerializedInstance = reSerializeFormInstance(instance)

        assertTrue("Form instance root should be same after serialization",
                instance.getRoot() == reSerializedInstance.getRoot())
    }

    /**
     * serialize a form instance then return the new deserialized instance
     */
    private fun reSerializeFormInstance(originalInstance: FormInstance): FormInstance {
        var reSerializedInstance: FormInstance? = null
        try {
            reSerializedInstance = FormInstance::class.java.newInstance()
        } catch (e: Exception) {
            fail(e.message)
        }
        val out = PlatformDataOutputStream()

        try {
            originalInstance.writeExternal(out)
        } catch (e: IOException) {
            fail(e.message)
        }
        var instanceStream: PlatformDataInputStream? = null
        try {
            instanceStream = PlatformDataInputStream(out.toByteArray())
            reSerializedInstance!!.readExternal(instanceStream, pf)
        } catch (e: Exception) {
            fail(e.message)
        } finally {
            instanceStream?.close()
        }
        return reSerializedInstance!!
    }

    /**
     * Regression test that runs through form entry with a normally loaded
     * form and a form that has been serialized then deserialized and compare
     * answers on a question with a dateTime type.
     */
    @Test
    @Throws(XPathSyntaxException::class)
    fun testFormEntryAfterSerialization() {
        val fpi = FormParseInit("/xform_tests/test_repeat_insert_duplicate_triggering.xml")
        var fec = fpi.getFormEntryController()
        fec.jumpToIndex(FormIndex.createBeginningOfFormIndex())

        val fd = fpi.getFormDef()!!
        fd.initialize(true, DummyInstanceInitializationFactory())
        val instance = fd.getMainInstance()!!
        do {
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)
        var evalCtx = fd.getEvaluationContext()!!
        val modified = ExprEvalUtils.xpathEval(evalCtx, "/data/how_many/@date_modified") as Date

        val reSerializedInstance = reSerializeFormInstance(instance)

        fd.setInstance(reSerializedInstance)
        fd.initialize(true, DummyInstanceInitializationFactory())
        val femodel = FormEntryModel(fd)
        fec = FormEntryController(femodel)
        fec.jumpToIndex(FormIndex.createBeginningOfFormIndex())

        do {
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)
        evalCtx = fd.getEvaluationContext()!!
        val modified2 = ExprEvalUtils.xpathEval(evalCtx, "/data/how_many/@date_modified") as Date
        assertTrue(modified.time - modified2.time < 3000)
    }
}
