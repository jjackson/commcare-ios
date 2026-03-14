package org.javarosa.core.model.test

import org.javarosa.core.model.FormIndex
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.IFunctionHandler
import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.test.FormParseInit
import org.javarosa.form.api.FormEntryController
import org.javarosa.xpath.XPathUnhandledException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.ArrayList

/**
 * @author Will Pride
 */
class CustomFuncTest {
    private var fpi: FormParseInit? = null

    private val errorDelta = 0.001

    /**
     * Try to use a form that has a custom function defined without extending
     * the context with a custom function handler.
     */
    @Test
    fun testFormFailure() {
        fpi = FormParseInit("/CustomFunctionTest.xhtml")

        val fec = fpi!!.getFormEntryController()
        fec.jumpToIndex(FormIndex.createBeginningOfFormIndex())

        do {
            val q = fpi!!.getCurrentQuestion() ?: continue

            try {
                fec.answerQuestion(IntegerData(1))
            } catch (e: XPathUnhandledException) {
                // we expect the test to fail on parsing
                return
            }
            fail("Should have failed parsing here")
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)
    }

    /**
     * Successfully use a form that has a custom function by extending the
     * context with a custom function handler.
     */
    @Test
    fun testFormSuccess() {
        fpi = FormParseInit("/CustomFunctionTest.xhtml")

        // Custom func to double the numeric argument passed in.
        val myDouble = object : IFunctionHandler {
            override fun getName(): String = "my_double"

            override fun eval(args: Array<Any?>?, ec: EvaluationContext?): Any {
                val myDouble = args!![0] as Double
                assertEquals(2.0, myDouble * 2.0, errorDelta)
                return myDouble * 2.0
            }

            override fun getPrototypes(): ArrayList<*> {
                val proto = arrayOf<Class<*>>(Double::class.java)
                val v = ArrayList<Array<Class<*>>>()
                v.add(proto)
                return v
            }

            override fun rawArgs(): Boolean = false
        }

        fpi!!.getFormDef()!!.exprEvalContext!!.addFunctionHandler(myDouble)

        val fec = fpi!!.getFormEntryController()

        do {
            val q = fpi!!.getCurrentQuestion() ?: continue
            fec.answerQuestion(IntegerData(1))
        } while (fec.stepToNextEvent() != FormEntryController.EVENT_END_OF_FORM)
    }
}
