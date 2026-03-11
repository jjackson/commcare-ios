package org.commcare.util

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

actual fun jsonGetString(jsonString: String, propertyName: String): String? {
    return try {
        JSONObject(jsonString).getString(propertyName)
    } catch (e: JSONException) {
        null
    }
}

actual fun jsonArrayToStringList(jsonArrayString: String): List<String> {
    return try {
        val arr = JSONArray(jsonArrayString)
        List(arr.length()) { i -> arr.optString(i) }
    } catch (e: JSONException) {
        emptyList()
    }
}
