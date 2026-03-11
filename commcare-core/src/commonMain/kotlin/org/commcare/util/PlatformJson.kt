package org.commcare.util

/**
 * Minimal platform-specific JSON parsing.
 * On JVM: delegates to org.json.JSONObject.
 * On iOS: uses Foundation's JSONSerialization.
 */
expect fun jsonGetString(jsonString: String, propertyName: String): String?

/**
 * Parse a JSON array string into a list of strings.
 */
expect fun jsonArrayToStringList(jsonArrayString: String): List<String>
