package org.commcare.util

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonUtilsTest {

    @Test
    fun toArray() {
        val jsonArray = JSONArray()
        val count = 3
        for (i in 0 until count) {
            jsonArray.put("item_$i")
        }
        val stringArray = JsonUtils.toArray(jsonArray)
        assertEquals(3, stringArray.size)
        assertEquals("item_0", stringArray[0])
        assertEquals("item_1", stringArray[1])
        assertEquals("item_2", stringArray[2])
    }
}
