package org.commcare.core

import org.commcare.xml.DetailParser
import org.commcare.xml.GraphParser
import org.javarosa.xml.ElementParser

/**
 * Register JVM-specific component factories.
 * Must be called during JVM initialization.
 */
object JvmPlatformInit {
    private var registered = false

    @JvmStatic
    fun ensureRegistered() {
        if (registered) return
        registered = true

        // Register graph parser factory for DetailParser
        DetailParser.graphParserFactory = { parser ->
            @Suppress("UNCHECKED_CAST")
            GraphParser(parser) as ElementParser<out org.commcare.suite.model.DetailTemplate>
        }
    }
}
