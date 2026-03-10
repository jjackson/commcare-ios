package org.javarosa.core.model.data.test;

import org.javarosa.core.model.QuestionDef;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.SelectMultiData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.helper.Selection;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SelectMultiDataTests {
    private static QuestionDef question;

    private static Selection one;
    private static Selection two;
    private static Selection three;

    private static ArrayList<Selection> firstTwo;
    private static ArrayList<Selection> lastTwo;
    private static ArrayList invalid;

    @BeforeClass
    public static void setUp() {
        question = new QuestionDef();

        for (int i = 0; i < 4; i++) {
            question.addSelectChoice(new SelectChoice("", "Selection" + i, "Selection " + i, false));
        }

        one = new Selection("Selection 1");
        one.attachChoice(question);
        two = new Selection("Selection 2");
        two.attachChoice(question);
        three = new Selection("Selection 3");
        three.attachChoice(question);

        firstTwo = new ArrayList<>();
        firstTwo.add(one);
        firstTwo.add(two);

        lastTwo = new ArrayList<>();
        lastTwo.add(two);
        lastTwo.add(three);

        invalid = new ArrayList();
        invalid.add(three);
        invalid.add(12);
        invalid.add(one);
    }

    @Test
    public void testGetData() {
        SelectOneData data = new SelectOneData(one);
        assertEquals("SelectOneData's getValue returned an incorrect SelectOne", data.getValue(), one);
    }

    @Test
    public void testSetData() {
        SelectMultiData data = new SelectMultiData(firstTwo);
        data.setValue(lastTwo);

        assertTrue("SelectMultiData did not set value properly. Maintained old value.", !(data.getValue().equals(firstTwo)));
        assertEquals("SelectMultiData did not properly set value ", data.getValue(), lastTwo);

        data.setValue(firstTwo);
        assertTrue("SelectMultiData did not set value properly. Maintained old value.", !(data.getValue().equals(lastTwo)));
        assertEquals("SelectMultiData did not properly reset value ", data.getValue(), firstTwo);
    }

    @Test
    public void testNullData() {
        boolean exceptionThrown = false;
        SelectMultiData data = new SelectMultiData();
        data.setValue(firstTwo);
        try {
            data.setValue(null);
        } catch (NullPointerException e) {
            exceptionThrown = true;
        }
        assertTrue("SelectMultiData failed to throw an exception when setting null data", exceptionThrown);
        assertTrue("SelectMultiData overwrote existing value on incorrect input", data.getValue().equals(firstTwo));
    }

    @Test
    public void testVectorImmutability() {
        SelectMultiData data = new SelectMultiData(firstTwo);
        Selection[] copy = new Selection[firstTwo.size()];
        firstTwo.toArray(copy);
        firstTwo.set(0, two);
        firstTwo.remove(1);

        ArrayList internal = data.getValue();

        assertVectorIdentity("External Reference: ", internal, copy);

        data.setValue(lastTwo);
        ArrayList<Selection> start = data.getValue();

        Selection[] external = new Selection[start.size()];
        start.toArray(external);

        start.remove(1);
        start.set(0, one);

        assertVectorIdentity("Internal Reference: ", data.getValue(), external);
    }

    private void assertVectorIdentity(String messageHeader, ArrayList v, Selection[] a) {
        assertEquals(messageHeader + "SelectMultiData's internal representation was violated. ArrayList size changed.", v.size(), a.length);

        for (int i = 0; i < v.size(); ++i) {
            Selection internalValue = (Selection)v.get(i);
            Selection copyValue = a[i];

            assertEquals(messageHeader + "SelectMultiData's internal representation was violated. Element " + i + "changed.", internalValue, copyValue);
        }
    }

    @Test
    public void testBadDataTypes() {
        boolean failure = false;
        SelectMultiData data = new SelectMultiData(firstTwo);
        try {
            data.setValue(invalid);
            data = new SelectMultiData(invalid);
        } catch (Exception e) {
            failure = true;
        }
        assertTrue("SelectMultiData did not throw a proper exception while being set to invalid data.", failure);

        Selection[] values = new Selection[firstTwo.size()];
        firstTwo.toArray(values);
        assertVectorIdentity("Ensure not overwritten: ", data.getValue(), values);
    }
}
