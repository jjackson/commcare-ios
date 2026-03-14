package org.commcare.suite.model

import com.google.common.collect.ImmutableList
import org.commcare.xml.QueryDataParser
import org.javarosa.core.model.utils.test.PersistableSandbox
import org.javarosa.test_utils.ReflectionUtils
import org.javarosa.xml.util.InvalidStructureException
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Serialization tests for QueryData classes
 */
class QueryDataModelTest {

    private lateinit var mSandbox: PersistableSandbox

    @Before
    fun setUp() {
        mSandbox = PersistableSandbox()
    }

    @Test
    @Throws(InvalidStructureException::class, IllegalAccessException::class)
    fun testSerializeValueQueryData() {
        val value = QueryDataParser.buildQueryData("key", "ref", null, null)
        checkValueData(value)
    }

    @Test
    @Throws(InvalidStructureException::class, IllegalAccessException::class)
    fun testSerializeValueQueryData_exclude() {
        val value = QueryDataParser.buildQueryData("key", "ref", "true()", null)
        checkValueData(value)
    }

    @Test
    @Throws(InvalidStructureException::class, IllegalAccessException::class)
    fun testSerializeListQueryData() {
        val value = QueryDataParser.buildQueryData(
                "key", "ref", "true()", "instance('selected-cases')/session-data/value")
        checkListQueryData(value)
    }

    @Test
    @Throws(InvalidStructureException::class, IllegalAccessException::class)
    fun testSerializeListQueryData_nullExclude() {
        val value = QueryDataParser.buildQueryData(
                "key", "ref", null, "instance('selected-cases')/session-data/value")
        checkListQueryData(value)
    }

    private fun checkValueData(value: QueryData) {
        checkSerialization(ValueQueryData::class.java, value, ImmutableList.of("ref", "excludeExpr"))
    }

    private fun checkListQueryData(value: QueryData) {
        checkSerialization(ListQueryData::class.java, value, ImmutableList.of("ref", "excludeExpr", "nodeset"))
    }

    @Throws(IllegalAccessException::class)
    private fun <T : QueryData> checkSerialization(clazz: Class<T>, value: QueryData, fields: List<String>) {
        val bytes = mSandbox.serialize(value)
        val deserialized = mSandbox.deserialize(bytes, clazz)
        assertEquals(value.getKey(), deserialized.getKey())
        for (fieldName in fields) {
            assertEquals(
                    ReflectionUtils.getField(value, fieldName),
                    ReflectionUtils.getField(deserialized, fieldName)
            )
        }
    }
}
