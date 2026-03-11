package org.commcare.util

actual fun jsonGetString(jsonString: String, propertyName: String): String? {
    // Simple JSON property extraction without full parser
    // TODO: Use Foundation JSONSerialization for production
    return try {
        // Minimal implementation for basic JSON objects
        val trimmed = jsonString.trim()
        if (!trimmed.startsWith("{")) return null
        // Find the property key
        val keyPattern = "\"$propertyName\""
        val keyIndex = trimmed.indexOf(keyPattern)
        if (keyIndex < 0) return null
        val afterKey = trimmed.substring(keyIndex + keyPattern.length).trimStart()
        if (!afterKey.startsWith(":")) return null
        val afterColon = afterKey.substring(1).trimStart()
        if (afterColon.startsWith("\"")) {
            val endQuote = afterColon.indexOf('"', 1)
            if (endQuote < 0) return null
            afterColon.substring(1, endQuote)
        } else {
            // Non-string value
            val endIndex = afterColon.indexOfFirst { it == ',' || it == '}' || it == ' ' }
            if (endIndex < 0) afterColon else afterColon.substring(0, endIndex)
        }
    } catch (e: Exception) {
        null
    }
}

actual fun jsonArrayToStringList(jsonArrayString: String): List<String> {
    // Simple JSON array parsing
    // TODO: Use Foundation JSONSerialization for production
    return try {
        val trimmed = jsonArrayString.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        val content = trimmed.substring(1, trimmed.length - 1).trim()
        if (content.isEmpty()) return emptyList()
        content.split(",").map { it.trim().removeSurrounding("\"") }
    } catch (e: Exception) {
        emptyList()
    }
}
