package org.commcare.app.engine

/**
 * Generates printable HTML from templates with case/form data substitution.
 * Supports simple variable replacement: {{variable_name}} → value.
 */
class PrintTemplateEngine {

    /**
     * Render an HTML template with the given data.
     * @param template HTML template string with {{key}} placeholders
     * @param data Map of key→value replacements
     * @return Rendered HTML
     */
    fun render(template: String, data: Map<String, String>): String {
        var result = template
        for ((key, value) in data) {
            result = result.replace("{{$key}}", escapeHtml(value))
        }
        // Remove any unreplaced placeholders
        result = result.replace(Regex("\\{\\{\\w+}}"), "")
        return result
    }

    /**
     * Generate a default print layout from case data when no template is configured.
     */
    fun generateDefaultHtml(title: String, fields: List<Pair<String, String>>): String {
        return buildString {
            append("<!DOCTYPE html><html><head>")
            append("<style>")
            append("body { font-family: -apple-system, Helvetica, sans-serif; margin: 20px; }")
            append("h2 { color: #333; border-bottom: 1px solid #ccc; padding-bottom: 8px; }")
            append("table { width: 100%; border-collapse: collapse; }")
            append("td { padding: 6px 8px; border-bottom: 1px solid #eee; }")
            append("td:first-child { font-weight: bold; width: 40%; color: #555; }")
            append("</style></head><body>")
            append("<h2>$title</h2>")
            append("<table>")
            for ((label, value) in fields) {
                append("<tr><td>${escapeHtml(label)}</td><td>${escapeHtml(value)}</td></tr>")
            }
            append("</table>")
            append("</body></html>")
        }
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
