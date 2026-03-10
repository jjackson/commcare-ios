package org.javarosa.form.api.test;

import org.javarosa.form.api.FormEntrySession;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.ArrayList;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class FormEntrySessionTest {
    @Test
    public void testSplitByParens() {
        String input = "(a) (b c) (d)";
        ArrayList<String> expectedOutput = new ArrayList<>(Arrays.asList("(a)", "(b c)", "(d)"));
        Assert.assertEquals(expectedOutput, FormEntrySession.splitTopParens(input));

        input = "(a) ((b) (c)) ((d))";
        expectedOutput = new ArrayList<>(Arrays.asList("(a)", "((b) (c))", "((d))"));
        Assert.assertEquals(expectedOutput, FormEntrySession.splitTopParens(input));

        input = "(a) (\\(b c\\)) (\\(d\\))";
        expectedOutput = new ArrayList<>(Arrays.asList("(a)", "(\\(b c\\))", "(\\(d\\))"));
        Assert.assertEquals(expectedOutput, FormEntrySession.splitTopParens(input));
    }
}
