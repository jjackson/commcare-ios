package org.javarosa.xpath.expr.test;

import org.javarosa.xpath.expr.XPathJsonPropertyFunc;
import org.json.JSONException;
import org.junit.Assert;
import org.junit.Test;

/**Tests for the XPathJsonProperty
 *
 * @author rcostello
 */

public class XPathJsonPropertyFuncTest {

    @Test
    public void getJsonProperty() {
        String testObj1 = "{\"name\":\"Sam\"}";
        String testVal1 = XPathJsonPropertyFunc.Companion.getJsonProperty(testObj1, "name");
        String testVal2 = XPathJsonPropertyFunc.Companion.getJsonProperty(testObj1, "city");
        Assert.assertEquals(testVal1, "Sam");
        Assert.assertEquals(testVal2, "");

        String testObj2 = "{city: New York}";
        String testVal3 = XPathJsonPropertyFunc.Companion.getJsonProperty(testObj2, "city");
        String testVal4 = XPathJsonPropertyFunc.Companion.getJsonProperty(testObj2, "state");
        Assert.assertEquals(testVal3, "New York");
        Assert.assertEquals(testVal4, "");

        String testInvalidObj = "{\"name\"}: \"Sam\"}";
        String testVal5 = XPathJsonPropertyFunc.Companion.getJsonProperty(testInvalidObj, "name");  
        Assert.assertEquals(testVal5, "");
        
        String testEmptyStrObj = "";
        String testVal6 = XPathJsonPropertyFunc.Companion.getJsonProperty(testEmptyStrObj, "name");  
        Assert.assertEquals(testVal6, "");
    }
}
