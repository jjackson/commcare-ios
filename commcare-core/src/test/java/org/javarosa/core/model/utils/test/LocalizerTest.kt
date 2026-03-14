package org.javarosa.core.model.utils.test

import org.javarosa.core.services.locale.Localizer
import org.javarosa.core.services.locale.TableLocaleSource
import org.javarosa.core.util.UnregisteredLocaleException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.test.ExternalizableTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class LocalizerTest {

    private fun testSerialize(l: Localizer, msg: String) {
        val pf = PrototypeFactory()
        pf.addClass(TableLocaleSource::class.java)
        ExternalizableTest.testExternalizable(l, pf, "Localizer [$msg]")
    }

    @Test
    fun testEmpty() {
        val l = Localizer()

        val locales = l.availableLocales
        if (locales == null || locales.isNotEmpty()) {
            fail("New localizer not empty")
        }
        if (l.locale != null) {
            fail("New localizer has locale set")
        }
        if (l.defaultLocale != null) {
            fail("New localizer has default locale set")
        }
    }

    @Test
    fun testAddLocale() {
        val l = Localizer()
        val testLocale = "test"

        if (l.hasLocale(testLocale)) {
            fail("Localizer reports it contains non-existent locale")
        }
        val result = l.addAvailableLocale(testLocale)
        if (!result) {
            fail("Localizer failed to add new locale")
        }
        if (!l.hasLocale(testLocale)) {
            fail("Localizer reports it does not contain newly added locale")
        }
        val localeData = l.getLocaleData(testLocale)
        if (localeData == null || localeData.size != 0) {
            fail("Newly created locale not empty (or undefined)")
        }
    }

    @Test
    fun testAddLocaleWithData() {
        val l = Localizer()
        val testLocale = "test"
        val localeData = TableLocaleSource()
        localeData.setLocaleMapping("textID", "text")

        if (l.hasLocale(testLocale)) {
            fail("Localizer reports it contains non-existent locale")
        }

        l.addAvailableLocale(testLocale)
        l.registerLocaleResource(testLocale, localeData)

        if (!l.hasLocale(testLocale)) {
            fail("Localizer reports it does not contain newly added locale")
        }
        if (localeData.getLocalizedText() != l.getLocaleData(testLocale)) {
            fail("Newly stored locale does not match source")
        }
    }

    @Test
    fun testAddExistingLocale() {
        val l = Localizer()
        val testLocale = "test"

        l.addAvailableLocale(testLocale)
        val table = TableLocaleSource()
        table.setLocaleMapping("textID", "text")
        l.registerLocaleResource(testLocale, table)

        val localeData = l.getLocaleData(testLocale)

        val result = l.addAvailableLocale(testLocale)
        if (result) {
            fail("Localizer overwrote existing locale")
        }

        val newLocaleData = l.getLocaleData(testLocale)
        if (localeData != newLocaleData) {
            fail("Localizer overwrote existing locale")
        }
    }

    @Test
    fun testSetCurrentLocaleExists() {
        val l = Localizer()
        val testLocale = "test"
        l.addAvailableLocale(testLocale)

        l.setLocale(testLocale)
        if (testLocale != l.locale) {
            fail("Did not properly set current locale")
        }
    }

    @Test
    fun testSetCurrentLocaleNotExists() {
        val l = Localizer()
        val testLocale = "test"

        try {
            l.setLocale(testLocale)
            fail("Set current locale to a non-existent locale")
        } catch (nsee: UnregisteredLocaleException) {
            // expected
        }
    }

    @Test
    fun testUnsetCurrentLocale() {
        val l = Localizer()
        val testLocale = "test"
        l.addAvailableLocale(testLocale)
        l.setLocale(testLocale)

        try {
            l.setLocale(null)
            fail("Able to unset current locale")
        } catch (nsee: UnregisteredLocaleException) {
            // expected
        }
    }

    @Test
    fun testSetDefaultLocaleExists() {
        val l = Localizer()
        val testLocale = "test"
        l.addAvailableLocale(testLocale)

        l.setDefaultLocale(testLocale)
        if (testLocale != l.defaultLocale) {
            fail("Did not properly set default locale")
        }
    }

    @Test
    fun testSetDefaultLocaleNotExists() {
        val l = Localizer()
        val testLocale = "test"

        try {
            l.setDefaultLocale(testLocale)
            fail("Set current locale to a non-existent locale")
        } catch (nsee: UnregisteredLocaleException) {
            // expected
        }
    }

    @Test
    fun testUnsetDefaultLocale() {
        val l = Localizer()
        val testLocale = "test"
        l.addAvailableLocale(testLocale)
        l.setDefaultLocale(testLocale)

        try {
            l.setDefaultLocale(null)
            if (l.defaultLocale != null) {
                fail("Could not unset default locale")
            }
        } catch (nsee: UnregisteredLocaleException) {
            fail("Exception unsetting default locale")
        }
    }

    @Test
    fun testSetToDefault() {
        val l = Localizer()
        val testLocale = "test"
        l.addAvailableLocale(testLocale)
        l.setDefaultLocale(testLocale)

        l.setToDefault()
        if (testLocale != l.locale) {
            fail("Could not set current locale to default")
        }
    }

    @Test
    fun testSetToDefaultNoDefault() {
        val l = Localizer()
        val testLocale = "test"
        l.addAvailableLocale(testLocale)

        try {
            l.setToDefault()
            fail("Set current locale to default when no default set")
        } catch (ise: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun testAvailableLocales() {
        val l = Localizer()

        l.addAvailableLocale("test1")
        var locales = l.availableLocales
        if (locales.size != 1 || locales[0] != "test1") {
            fail("Available locales not as expected")
        }

        l.addAvailableLocale("test2")
        locales = l.availableLocales
        if (locales.size != 2 || locales[0] != "test1" || locales[1] != "test2") {
            fail("Available locales not as expected")
        }

        l.addAvailableLocale("test3")
        locales = l.availableLocales
        if (locales.size != 3 || locales[0] != "test1" || locales[1] != "test2" || locales[2] != "test3") {
            fail("Available locales not as expected")
        }
    }

    @Test
    fun testGetLocaleMap() {
        val l = Localizer()
        val testLocale = "test"
        l.addAvailableLocale(testLocale)

        if (l.getLocaleMap(testLocale) != l.getLocaleData(testLocale)) {
            fail()
        }
    }

    @Test
    fun testGetLocaleMapNotExist() {
        val l = Localizer()
        val testLocale = "test"

        try {
            l.getLocaleMap(testLocale)
            fail("Did not throw exception when getting locale mapping for non-existent locale")
        } catch (nsee: UnregisteredLocaleException) {
            // expected
        }
    }

    @Test
    fun testTextMapping() {
        val l = Localizer()
        val testLocale = "test"
        l.addAvailableLocale(testLocale)

        if (l.hasMapping(testLocale, "textID")) {
            fail("Localizer contains text mapping that was not defined")
        }
        val table = TableLocaleSource()
        table.setLocaleMapping("textID", "text")
        l.registerLocaleResource(testLocale, table)

        if (!l.hasMapping(testLocale, "textID")) {
            fail("Localizer does not contain newly added text mapping")
        }
        if ("text" != l.getLocaleData(testLocale)!!["textID"]) {
            fail("Newly added text mapping does not match source")
        }
    }

    @Test
    fun testTextMappingOverwrite() {
        val l = Localizer()
        val testLocale = "test"

        l.addAvailableLocale(testLocale)
        val table = TableLocaleSource()

        table.setLocaleMapping("textID", "oldText")
        table.setLocaleMapping("textID", "newText")

        l.registerLocaleResource(testLocale, table)

        if (!l.hasMapping(testLocale, "textID")) {
            fail("Localizer does not contain overwritten text mapping")
        }
        if ("newText" != l.getLocaleData(testLocale)!!["textID"]) {
            fail("Newly overwritten text mapping does not match source")
        }
    }

    @Test
    fun testGetText() {
        for (localeCase in 1..3) {
            for (formCase in 1..2) {
                testGetText(localeCase, formCase)
            }
        }
    }

    private fun testGetText(localeCase: Int, formCase: Int) {
        var ourLocale: String? = null
        var otherLocale: String? = null

        when (localeCase) {
            DEFAULT_LOCALE -> {
                ourLocale = "default"
                otherLocale = null
            }
            NON_DEFAULT_LOCALE -> {
                ourLocale = "other"
                otherLocale = "default"
            }
            NEUTRAL_LOCALE -> {
                ourLocale = "neutral"
                otherLocale = null
            }
        }

        val textID = "textID" + if (formCase == CUSTOM_FORM) ";form" else ""

        for (i in 0 until 4) {
            for (j in 0 until 4) {
                if (otherLocale == null) {
                    testGetText(i, j, -1, ourLocale!!, otherLocale, textID, localeCase, formCase)
                } else {
                    for (k in 0 until 4) {
                        testGetText(i, j, k, ourLocale!!, otherLocale, textID, localeCase, formCase)
                    }
                }
            }
        }
    }

    private fun testGetText(
        i: Int, j: Int, k: Int,
        ourLocale: String, otherLocale: String?,
        textID: String, localeCase: Int, formCase: Int
    ) {
        val l = buildLocalizer(i, j, k, ourLocale, otherLocale)
        val expected = expectedText(textID, l)

        val text = l.getText(textID, ourLocale)
        if (if (expected == null) text != null else expected != text) {
            fail("Did not retrieve expected text from localizer [$localeCase,$formCase,$i,$j,$k]")
        }

        val text2 = l.getText(textID)
        if (expected == null && text2 != null) {
            fail("Localization shouldn't have returned a result")
        } else if (expected != null && expected != text2) {
            fail("Did not retrieve expected text")
        }
    }

    private fun buildLocalizer(i: Int, j: Int, k: Int, ourLocale: String, otherLocale: String?): Localizer {
        val l = Localizer(i / 2 == 0, i % 2 == 0)

        val firstLocale = TableLocaleSource()
        val secondLocale = TableLocaleSource()

        if (j / 2 == 0 || "default" == ourLocale)
            firstLocale.setLocaleMapping("textID", "text:$ourLocale:base")
        if (j % 2 == 0 || "default" == ourLocale)
            firstLocale.setLocaleMapping("textID;form", "text:$ourLocale:form")

        if (otherLocale != null) {
            if (k / 2 == 0 || "default" == otherLocale)
                secondLocale.setLocaleMapping("textID", "text:$otherLocale:base")
            if (k % 2 == 0 || "default" == otherLocale)
                secondLocale.setLocaleMapping("textID;form", "text:$otherLocale:form")
        }

        l.addAvailableLocale(ourLocale)
        l.registerLocaleResource(ourLocale, firstLocale)

        if (otherLocale != null) {
            l.addAvailableLocale(otherLocale)
            l.registerLocaleResource(otherLocale, secondLocale)
        }
        if (l.hasLocale("default")) {
            l.setDefaultLocale("default")
        }

        l.setLocale(ourLocale)

        return l
    }

    private fun expectedText(textID: String, l: Localizer): String? {
        val searchOrder = BooleanArray(4)
        val fallbackLocale = l.getFallbackLocale()
        val fallbackForm = l.getFallbackForm()
        val hasForm = ";" in textID
        val hasDefault = l.defaultLocale != null && l.defaultLocale != l.locale
        val baseTextID = if (hasForm) textID.substring(0, textID.indexOf(";")) else textID

        searchOrder[0] = hasForm
        searchOrder[1] = !hasForm || fallbackForm
        searchOrder[2] = hasForm && (hasDefault && fallbackLocale)
        searchOrder[3] = (!hasForm || fallbackForm) && (hasDefault && fallbackLocale)

        var text: String? = null
        for (i in 0 until 4) {
            if (text != null) break
            if (!searchOrder[i]) continue

            text = when (i + 1) {
                1 -> l.getRawText(l.locale, textID)
                2 -> l.getRawText(l.locale, baseTextID)
                3 -> l.getRawText(l.defaultLocale, textID)
                4 -> l.getRawText(l.defaultLocale, baseTextID)
                else -> null
            }
        }

        return text
    }

    @Test
    fun testGetTextNoCurrentLocale() {
        val l = Localizer()
        val table = TableLocaleSource()
        l.addAvailableLocale("test")
        l.setDefaultLocale("test")

        table.setLocaleMapping("textID", "text")
        l.registerLocaleResource("test", table)

        try {
            l.getText("textID")
            fail("Retrieved current locale text when current locale not set")
        } catch (nsee: UnregisteredLocaleException) {
            // expected
        }
    }

    @Test
    fun testNullArgs() {
        val l = Localizer()
        l.addAvailableLocale("test")

        val table = TableLocaleSource()

        try {
            @Suppress("NULL_FOR_NONNULL_TYPE")
            l.addAvailableLocale(null as String)
            fail("addAvailableLocale: Did not get expected null pointer exception")
        } catch (npe: NullPointerException) {
            // expected
        }

        if (l.hasLocale(null)) {
            fail("Localizer reports it contains null locale")
        }

        try {
            l.registerLocaleResource(null, TableLocaleSource())
            fail("setLocaleData: Did not get expected null pointer exception")
        } catch (npe: NullPointerException) {
            // expected
        }

        try {
            l.registerLocaleResource("test", null)
            fail("setLocaleData: Did not get expected null pointer exception")
        } catch (npe: NullPointerException) {
            // expected
        }

        if (l.getLocaleData(null) != null) {
            fail("getLocaleData: Localizer returns mappings for null locale")
        }

        try {
            l.getLocaleMap(null)
            fail("getLocaleMap: Did not get expected exception")
        } catch (nsee: UnregisteredLocaleException) {
            // expected
        }

        try {
            table.setLocaleMapping(null, "text")
            fail("setLocaleMapping: Did not get expected null pointer exception")
        } catch (npe: NullPointerException) {
            // expected
        }

        try {
            table.setLocaleMapping(null, null)
            fail("setLocaleMapping: Did not get expected null pointer exception")
        } catch (npe: NullPointerException) {
            // expected
        }

        try {
            l.hasMapping(null, "textID")
            fail("hasMapping: Did not get expected exception")
        } catch (nsee: UnregisteredLocaleException) {
            // expected
        }

        if (l.hasMapping("test", null)) {
            fail("Localization reports it contains null mapping")
        }

        try {
            l.getText("textID", null as String?)
            fail("getText: Did not get expected exception")
        } catch (nsee: UnregisteredLocaleException) {
            // expected
        }

        try {
            val nullTextId: String? = null
            l.getText(nullTextId as String, "test")
            fail("getText: Did not get expected null pointer exception")
        } catch (npe: NullPointerException) {
            // expected
        }
    }

    @Test
    fun testSerialization() {
        val l = Localizer(true, true)
        val firstLocale = TableLocaleSource()
        val secondLocale = TableLocaleSource()
        val finalLocale = TableLocaleSource()

        testSerialize(l, "empty 1")
        testSerialize(Localizer(false, false), "empty 2")
        testSerialize(Localizer(true, false), "empty 3")
        testSerialize(Localizer(false, true), "empty 4")

        l.addAvailableLocale("locale1")
        testSerialize(l, "one empty locale")

        l.addAvailableLocale("locale2")
        testSerialize(l, "two empty locales")

        l.setDefaultLocale("locale2")
        testSerialize(l, "two empty locales + default")

        l.setToDefault()
        testSerialize(l, "two empty locales + default/current")

        l.setLocale("locale1")
        testSerialize(l, "two empty locales + default/current 2")

        l.setDefaultLocale(null)
        testSerialize(l, "two empty locales + current")

        l.registerLocaleResource("locale1", firstLocale)
        l.registerLocaleResource("locale2", secondLocale)
        firstLocale.setLocaleMapping("id1", "text1")
        testSerialize(l, "locales with data 1")
        firstLocale.setLocaleMapping("id2", "text2")
        testSerialize(l, "locales with data 2")

        secondLocale.setLocaleMapping("id1", "text1")
        secondLocale.setLocaleMapping("id2", "text2")
        secondLocale.setLocaleMapping("id3", "text3")
        testSerialize(l, "locales with data 3")

        secondLocale.setLocaleMapping("id2", null)
        testSerialize(l, "locales with data 4")

        finalLocale.setLocaleMapping("id1", "text1")
        finalLocale.setLocaleMapping("id4", "text4")
        l.registerLocaleResource("locale3", finalLocale)
        testSerialize(l, "locales with data 5")

        testSerialize(l, "locales with data 6")
    }

    @Test
    fun testLinearSub() {
        val f = "first"
        val s = "second"
        val c = "\${0}"
        val d = "\${1}\${0}"
        val res = arrayOf("One", "Two")

        assertEquals(Localizer.processArguments("\${0}", arrayOf(f)), f)
        assertEquals(Localizer.processArguments("\${0},\${1}", arrayOf(f, s)), "$f,$s")
        assertEquals(Localizer.processArguments("testing \${0}", arrayOf(f)), "testing $f")
        assertEquals(Localizer.processArguments("1\${arbitrary}2", arrayOf(f)), "1${f}2")

        val holder = arrayOfNulls<String>(1)

        runAsync({ holder[0] = Localizer.processArguments("\${0}", arrayOf(c)) }, "Argument processing: $c")
        assertEquals(holder[0], c)

        runAsync({ holder[0] = Localizer.processArguments("\${0}", arrayOf(d)) }, "Argument processing: $d")
        assertEquals(holder[0], d)

        runAsync({ holder[0] = Localizer.processArguments(holder[0]!!, res) }, "Argument processing: ${res[1]}${res[0]}")
        assertEquals(holder[0], res[1] + res[0])

        runAsync({ holder[0] = Localizer.processArguments("$ {0} \${1}", res) }, "Argument processing: $ {0} ${res[1]}")
        assertEquals(holder[0], "$ {0} " + res[1])
    }

    private fun runAsync(test: Runnable, label: String) {
        val t = Thread(test)
        t.start()
        val attempts = 4

        for (i in 0 until attempts) {
            try {
                t.join(500)
                break
            } catch (e: InterruptedException) {
                // retry
            }
        }
        if (t.isAlive) {
            @Suppress("DEPRECATION")
            t.stop()
            throw RuntimeException("Failed to return from recursive argument processing: $label")
        }
    }

    @Test
    fun testHashSub() {
        val f = "first"
        val s = "second"
        val h = HashMap<String, String>()
        h["fir"] = f
        h["also first"] = f
        h["sec"] = s

        assertEquals(Localizer.processArguments("\${fir}", h), f)
        assertEquals(Localizer.processArguments("\${fir},\${sec}", h), "$f,$s")
        assertEquals(Localizer.processArguments("\${sec},\${fir}", h), "$s,$f")
        assertEquals(Localizer.processArguments("\${empty}", h), "\${empty}")
        assertEquals(Localizer.processArguments("\${fir},\${fir},\${also first}", h), "$f,$f,$f")
    }

    @Test
    fun testFallbacks() {
        val localizer = Localizer(true, true)

        localizer.addAvailableLocale("one")
        localizer.addAvailableLocale("two")

        val firstLocale = TableLocaleSource()
        firstLocale.setLocaleMapping("data", "val")
        firstLocale.setLocaleMapping("data2", "vald2")
        localizer.registerLocaleResource("one", firstLocale)

        val secondLocale = TableLocaleSource()
        firstLocale.setLocaleMapping("data", "val2")
        localizer.registerLocaleResource("two", secondLocale)
        localizer.setDefaultLocale("one")

        localizer.setLocale("two")

        val text = localizer.getText("data2")
        assertEquals("fallback", text, "vald2")
        val shouldBeNull = localizer.getText("noexist")
        assertNull("Localizer didn't return null value", shouldBeNull)

        localizer.setToDefault()

        val shouldBeNull2 = localizer.getText("noexist")
        assertNull("Localizer didn't return null value", shouldBeNull2)
    }

    companion object {
        private const val DEFAULT_LOCALE = 1
        private const val NON_DEFAULT_LOCALE = 2
        private const val NEUTRAL_LOCALE = 3
        private const val CUSTOM_FORM = 2
    }
}
