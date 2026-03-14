package org.commcare.backend.model

import org.commcare.suite.model.Text
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.xpath.XPathException
import org.junit.Assert
import org.junit.Test

/**
 * Created by ctsims on 12/14/2016.
 */
class TextTests {
    @Test
    fun testXPathExceptionHandling() {
        var thrown = false
        val arguments = HashMap<String, Text>()
        val t = Text.XPathText("date('steve')", arguments)
        try {
            t.evaluate(EvaluationContext(null))
        } catch (e: Exception) {
            if (e !is XPathException) {
                Assert.fail("Invalid exception thrown during XPath text usage")
            }
            e.message
            thrown = true
        }
        if (!thrown) {
            Assert.fail("XPath failure in text run did not fail fast")
        }
    }
}
