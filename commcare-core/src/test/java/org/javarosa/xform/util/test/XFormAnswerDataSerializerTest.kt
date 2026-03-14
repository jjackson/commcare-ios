package org.javarosa.xform.util.test

import org.javarosa.core.model.data.DateData
import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.data.StringData
import org.javarosa.core.model.data.TimeData
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.xform.util.XFormAnswerDataSerializer
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.util.Date

/**
 * Note that this is just a start and doesn't cover direct comparisons
 * for most values.
 *
 * @author Clayton Sims
 */
class XFormAnswerDataSerializerTest {

    companion object {
        const val stringDataValue = "String Data Value"
        val integerDataValue: Int = 5
        val dateDataValue = Date()
        val timeDataValue = Date()

        lateinit var stringData: StringData
        lateinit var integerData: IntegerData
        lateinit var dateData: DateData
        lateinit var timeData: TimeData

        val stringElement = TreeElement()
        val intElement = TreeElement()
        val dateElement = TreeElement()
        val timeElement = TreeElement()

        lateinit var serializer: XFormAnswerDataSerializer

        @JvmStatic
        @BeforeClass
        fun setUp() {
            stringData = StringData(stringDataValue)
            stringElement.setValue(stringData)

            integerData = IntegerData(integerDataValue)
            intElement.setValue(integerData)

            dateData = DateData(dateDataValue)
            dateElement.setValue(dateData)

            timeData = TimeData(timeDataValue)
            timeElement.setValue(timeData)

            serializer = XFormAnswerDataSerializer()
        }
    }

    @Test
    fun testString() {
        assertTrue("Serializer Incorrectly Reports Inability to Serializer String", serializer.canSerialize(stringElement.getValue()))
        val answerData = serializer.serializeAnswerData(stringData)
        assertNotNull("Serializer returns Null for valid String Data", answerData)
        assertEquals("Serializer returns incorrect string serialization", answerData, stringDataValue)
    }

    @Test
    fun testInteger() {
        assertTrue("Serializer Incorrectly Reports Inability to Serializer Integer", serializer.canSerialize(intElement.getValue()))
        val answerData = serializer.serializeAnswerData(integerData)
        assertNotNull("Serializer returns Null for valid Integer Data", answerData)
    }

    @Test
    fun testDate() {
        assertTrue("Serializer Incorrectly Reports Inability to Serializer Date", serializer.canSerialize(dateElement.getValue()))
        val answerData = serializer.serializeAnswerData(dateData)
        assertNotNull("Serializer returns Null for valid Date Data", answerData)
    }

    @Test
    fun testTime() {
        assertTrue("Serializer Incorrectly Reports Inability to Serializer Time", serializer.canSerialize(timeElement.getValue()))
        val answerData = serializer.serializeAnswerData(timeData)
        assertNotNull("Serializer returns Null for valid Time Data", answerData)
    }
}
