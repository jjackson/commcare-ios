package org.javarosa.form.api.test

import org.javarosa.form.api.FormEntrySession
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
class FormEntrySessionTest {

    @Test
    fun testSplitByParens() {
        var input = "(a) (b c) (d)"
        var expectedOutput = arrayListOf("(a)", "(b c)", "(d)")
        assertEquals(expectedOutput, FormEntrySession.splitTopParens(input))

        input = "(a) ((b) (c)) ((d))"
        expectedOutput = arrayListOf("(a)", "((b) (c))", "((d))")
        assertEquals(expectedOutput, FormEntrySession.splitTopParens(input))

        input = "(a) (\\(b c\\)) (\\(d\\))"
        expectedOutput = arrayListOf("(a)", "(\\(b c\\))", "(\\(d\\))")
        assertEquals(expectedOutput, FormEntrySession.splitTopParens(input))
    }
}
