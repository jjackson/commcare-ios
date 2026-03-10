package org.javarosa.xml

/**
 * Pure Kotlin XML pull parser for iOS (Kotlin/Native).
 * Implements a state-machine parser that handles elements, attributes, text,
 * namespaces, comments, CDATA, and processing instructions.
 *
 * Matches kxml2/XmlPullParser behavior for the subset used by CommCare.
 */
class IosXmlParser(data: ByteArray, encoding: String) : PlatformXmlParser {

    private val input: String = data.decodeToString()
    private var pos = 0
    private var _eventType = PlatformXmlParser.START_DOCUMENT
    private var _depth = 0

    // Current event state
    private var currentName: String? = null
    private var currentNamespace: String? = null
    private var currentText: String? = null
    private var currentPrefix: String? = null
    private var _attributes = mutableListOf<Attribute>()
    private var isEmptyElement = false
    private var pendingEndTag = false

    // Namespace tracking: stack of maps (prefix -> uri) per depth
    private val namespaceStack = mutableListOf<MutableMap<String, String>>()

    data class Attribute(
        val namespace: String,
        val prefix: String?,
        val name: String,
        val value: String
    )

    override fun next(): Int {
        if (pendingEndTag) {
            pendingEndTag = false
            _depth--
            _eventType = PlatformXmlParser.END_TAG
            return _eventType
        }

        skipWhitespaceAndComments()

        if (pos >= input.length) {
            _eventType = PlatformXmlParser.END_DOCUMENT
            return _eventType
        }

        if (input[pos] == '<') {
            if (pos + 1 < input.length) {
                when {
                    input[pos + 1] == '/' -> parseEndTag()
                    input[pos + 1] == '?' -> {
                        parseProcessingInstruction()
                        return next()
                    }
                    input[pos + 1] == '!' -> {
                        if (input.startsWith("<![CDATA[", pos)) {
                            parseCData()
                        } else {
                            // Comment or DOCTYPE — skip
                            skipDeclaration()
                            return next()
                        }
                    }
                    else -> parseStartTag()
                }
            }
        } else {
            parseText()
        }

        return _eventType
    }

    override val eventType: Int get() = _eventType
    override val name: String? get() = currentName
    override val namespace: String? get() = currentNamespace
    override val text: String? get() = currentText
    override val depth: Int get() = _depth

    override val isWhitespace: Boolean get() {
        val t = currentText ?: return true
        return t.all { it == ' ' || it == '\t' || it == '\n' || it == '\r' }
    }

    override fun getAttributeValue(namespace: String?, name: String): String? {
        return _attributes.find { attr ->
            attr.name == name && (namespace == null || namespace.isEmpty() || attr.namespace == namespace)
        }?.value
    }

    override val attributeCount: Int get() = _attributes.size

    override fun getAttributeName(index: Int): String = _attributes[index].name
    override fun getAttributeNamespace(index: Int): String = _attributes[index].namespace
    override fun getAttributePrefix(index: Int): String? = _attributes[index].prefix
    override fun getAttributeValue(index: Int): String = _attributes[index].value

    override fun getNamespace(prefix: String?): String {
        val p = prefix ?: ""
        for (i in namespaceStack.indices.reversed()) {
            namespaceStack[i][p]?.let { return it }
        }
        return ""
    }

    override fun nextText(): String {
        if (_eventType != PlatformXmlParser.START_TAG) {
            throw RuntimeException("nextText() called not on START_TAG")
        }
        val event = next()
        if (event == PlatformXmlParser.TEXT) {
            val result = currentText ?: ""
            val endEvent = next()
            if (endEvent != PlatformXmlParser.END_TAG) {
                throw RuntimeException("nextText(): expected END_TAG after text, got $endEvent")
            }
            return result
        } else if (event == PlatformXmlParser.END_TAG) {
            return ""
        } else {
            throw RuntimeException("nextText(): unexpected event type $event")
        }
    }

    override fun nextTag(): Int {
        var event = next()
        while (event == PlatformXmlParser.TEXT && isWhitespace) {
            event = next()
        }
        if (event != PlatformXmlParser.START_TAG && event != PlatformXmlParser.END_TAG) {
            throw RuntimeException("nextTag(): unexpected event type $event")
        }
        return event
    }

    override val positionDescription: String get() = "@position $pos"

    override val prefix: String? get() = currentPrefix

    // --- Parsing Methods ---

    private fun parseStartTag() {
        pos++ // skip '<'
        val qname = readName()
        _attributes.clear()
        val nsDeclarations = mutableMapOf<String, String>()

        // Parse attributes
        while (pos < input.length) {
            skipSpaces()
            if (pos >= input.length) break
            if (input[pos] == '/' || input[pos] == '>') break
            val attrQName = readName()
            skipSpaces()
            expect('=')
            skipSpaces()
            val attrValue = readAttributeValue()

            if (attrQName == "xmlns") {
                nsDeclarations[""] = attrValue
            } else if (attrQName.startsWith("xmlns:")) {
                nsDeclarations[attrQName.substring(6)] = attrValue
            } else {
                val (attrPrefix, attrLocal) = splitQName(attrQName)
                _attributes.add(Attribute("", attrPrefix, attrLocal, attrValue))
            }
        }

        // Handle self-closing tag
        isEmptyElement = false
        if (pos < input.length && input[pos] == '/') {
            isEmptyElement = true
            pos++ // skip '/'
        }
        expect('>')

        _depth++

        // Push namespace scope
        while (namespaceStack.size < _depth) {
            namespaceStack.add(mutableMapOf())
        }
        namespaceStack[_depth - 1] = nsDeclarations.toMutableMap()

        // Resolve element namespace
        val (pfx, localName) = splitQName(qname)
        currentPrefix = pfx
        currentName = localName
        currentNamespace = resolveNamespace(pfx)
        currentText = null

        // Resolve attribute namespaces
        _attributes = _attributes.map { attr ->
            if (attr.prefix != null && attr.prefix.isNotEmpty()) {
                attr.copy(namespace = resolveNamespace(attr.prefix))
            } else {
                attr
            }
        }.toMutableList()

        _eventType = PlatformXmlParser.START_TAG

        if (isEmptyElement) {
            pendingEndTag = true
        }
    }

    private fun parseEndTag() {
        pos += 2 // skip '</'
        val qname = readName()
        skipSpaces()
        expect('>')

        val (pfx, localName) = splitQName(qname)
        currentName = localName
        currentNamespace = resolveNamespace(pfx)
        currentText = null
        _attributes.clear()

        // Pop namespace scope
        if (_depth > 0 && _depth <= namespaceStack.size) {
            namespaceStack[_depth - 1].clear()
        }
        _depth--

        _eventType = PlatformXmlParser.END_TAG
    }

    private fun parseText() {
        val start = pos
        while (pos < input.length && input[pos] != '<') {
            pos++
        }
        currentText = decodeEntities(input.substring(start, pos))
        currentName = null
        currentNamespace = null
        _attributes.clear()
        _eventType = PlatformXmlParser.TEXT
    }

    private fun parseCData() {
        pos += 9 // skip "<![CDATA["
        val end = input.indexOf("]]>", pos)
        if (end == -1) throw RuntimeException("Unclosed CDATA section")
        currentText = input.substring(pos, end)
        pos = end + 3
        currentName = null
        currentNamespace = null
        _attributes.clear()
        _eventType = PlatformXmlParser.TEXT
    }

    private fun parseProcessingInstruction() {
        val end = input.indexOf("?>", pos)
        if (end == -1) throw RuntimeException("Unclosed processing instruction")
        pos = end + 2
    }

    private fun skipDeclaration() {
        if (input.startsWith("<!--", pos)) {
            val end = input.indexOf("-->", pos + 4)
            if (end == -1) throw RuntimeException("Unclosed comment")
            pos = end + 3
        } else {
            var nestLevel = 1
            pos += 2 // skip "<!"
            while (pos < input.length && nestLevel > 0) {
                if (input[pos] == '<') nestLevel++
                else if (input[pos] == '>') nestLevel--
                pos++
            }
        }
    }

    private fun skipWhitespaceAndComments() {
        // Only skip at document level, not inside elements
    }

    // --- Helper Methods ---

    private fun readName(): String {
        val start = pos
        while (pos < input.length && isNameChar(input[pos])) {
            pos++
        }
        if (pos == start) throw RuntimeException("Expected name at position $pos")
        return input.substring(start, pos)
    }

    private fun isNameChar(c: Char): Boolean {
        return c.isLetterOrDigit() || c == ':' || c == '_' || c == '-' || c == '.'
    }

    private fun readAttributeValue(): String {
        val quote = input[pos]
        if (quote != '"' && quote != '\'') throw RuntimeException("Expected quote at position $pos")
        pos++
        val start = pos
        while (pos < input.length && input[pos] != quote) {
            pos++
        }
        val value = input.substring(start, pos)
        pos++
        return decodeEntities(value)
    }

    private fun expect(c: Char) {
        if (pos >= input.length || input[pos] != c) {
            throw RuntimeException("Expected '$c' at position $pos but got '${if (pos < input.length) input[pos] else "EOF"}'")
        }
        pos++
    }

    private fun skipSpaces() {
        while (pos < input.length && input[pos].isWhitespace()) {
            pos++
        }
    }

    private fun splitQName(qname: String): Pair<String?, String> {
        val colon = qname.indexOf(':')
        return if (colon >= 0) {
            Pair(qname.substring(0, colon), qname.substring(colon + 1))
        } else {
            Pair(null, qname)
        }
    }

    private fun resolveNamespace(prefix: String?): String {
        val p = prefix ?: ""
        for (i in namespaceStack.indices.reversed()) {
            namespaceStack[i][p]?.let { return it }
        }
        return ""
    }

    private fun decodeEntities(s: String): String {
        if (!s.contains('&')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            if (s[i] == '&') {
                val end = s.indexOf(';', i + 1)
                if (end == -1) {
                    sb.append(s[i])
                    i++
                    continue
                }
                val entity = s.substring(i + 1, end)
                when (entity) {
                    "amp" -> sb.append('&')
                    "lt" -> sb.append('<')
                    "gt" -> sb.append('>')
                    "quot" -> sb.append('"')
                    "apos" -> sb.append('\'')
                    else -> {
                        if (entity.startsWith("#x")) {
                            sb.append(entity.substring(2).toInt(16).toChar())
                        } else if (entity.startsWith("#")) {
                            sb.append(entity.substring(1).toInt().toChar())
                        } else {
                            sb.append("&$entity;")
                        }
                    }
                }
                i = end + 1
            } else {
                sb.append(s[i])
                i++
            }
        }
        return sb.toString()
    }
}

actual fun createXmlParser(data: ByteArray, encoding: String): PlatformXmlParser =
    IosXmlParser(data, encoding)
