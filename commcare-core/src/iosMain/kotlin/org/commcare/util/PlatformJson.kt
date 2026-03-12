@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.commcare.util

import platform.Foundation.*

actual fun jsonGetString(jsonString: String, propertyName: String): String? {
    return try {
        val data = (jsonString as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            ?: return null
        val obj = NSJSONSerialization.JSONObjectWithData(data, 0u, null) as? Map<*, *>
            ?: return null
        obj[propertyName]?.toString()
    } catch (e: Exception) {
        null
    }
}

actual fun jsonArrayToStringList(jsonArrayString: String): List<String> {
    return try {
        val data = (jsonArrayString as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            ?: return emptyList()
        val arr = NSJSONSerialization.JSONObjectWithData(data, 0u, null) as? List<*>
            ?: return emptyList()
        arr.mapNotNull { it?.toString() }
    } catch (e: Exception) {
        emptyList()
    }
}
