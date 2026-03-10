package org.commcare.util

import kotlin.jvm.JvmStatic

import org.json.JSONArray

object JsonUtils {

    /**
     * Converts a JSON Array to a String Array
     * @param jsonArray A JSON Array containing string objects that we wish to convert to a String Array
     * @return A String Array representation for the given jsonArray
     */
    @JvmStatic
    fun toArray(jsonArray: JSONArray?): Array<String> {
        if (jsonArray != null) {
            return Array(jsonArray.length()) { i -> jsonArray.optString(i) }
        }
        return emptyArray()
    }
}
