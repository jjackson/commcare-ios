package org.javarosa.core.reference

import kotlin.jvm.JvmStatic

/**
 * The reference manager is a singleton class which
 * is responsible for deriving reference URI's into
 * references at runtime.
 *
 * Raw reference factories
 * (which are capable of actually creating fully
 * qualified reference objects) are added with the
 * addFactory() method. The most common method
 * of doing so is to implement the PrefixedRootFactory
 * as either a full class, or an anonymous inner class,
 * providing the roots available in the current environment
 * and the code for constructing a reference from them.
 *
 * RootTranslators (which rely on other factories) are
 * used to describe that a particular reference style (generally
 * a high level reference like "jr://media/" or "jr://images/"
 * should be translated to another available reference in this
 * environment like "jr://file/". Root Translators do not
 * directly derive references, but rather translate them to what
 * the reference should look like in the current circumstances.
 *
 * @author ctsims
 */
open class ReferenceManager {

    private val translators: ArrayList<RootTranslator> = ArrayList()
    private val factories: ArrayList<ReferenceFactory> = ArrayList()
    private val sessionTranslators: ArrayList<RootTranslator> = ArrayList()

    /**
     * @return The available reference factories
     */
    fun getFactories(): Array<ReferenceFactory> {
        val roots = arrayOfNulls<ReferenceFactory>(translators.size)
        return translators.toArray(roots)
    }

    /**
     * Adds a new Translator to the current environment.
     */
    fun addRootTranslator(translator: RootTranslator) {
        if (!translators.contains(translator)) {
            translators.add(translator)
        }
    }

    /**
     * Adds a factory for deriving reference URI's into references
     *
     * @param factory A raw ReferenceFactory capable of creating
     * a reference.
     */
    fun addReferenceFactory(factory: ReferenceFactory) {
        if (!factories.contains(factory)) {
            factories.add(factory)
        }
    }

    fun removeReferenceFactory(factory: ReferenceFactory): Boolean {
        return factories.remove(factory)
    }

    /**
     * Derives a global reference from a URI in the current environment.
     *
     * @param uri The URI representing a global reference.
     * @return A reference which is identified by the provided URI.
     * @throws InvalidReferenceException If the current reference could
     * not be derived by the current environment
     */
    @Throws(InvalidReferenceException::class)
    fun DeriveReference(uri: String?): Reference {
        return DeriveReference(uri, null as String?)
    }

    /**
     * Derives a reference from a URI in the current environment.
     *
     * @param uri The URI representing a reference.
     * @param context A reference which provides context for any
     * relative reference accessors.
     * @return A reference which is identified by the provided URI.
     * @throws InvalidReferenceException If the current reference could
     * not be derived by the current environment
     */
    @Throws(InvalidReferenceException::class)
    fun DeriveReference(uri: String?, context: Reference): Reference {
        return DeriveReference(uri, context.getURI())
    }

    /**
     * Derives a reference from a URI in the current environment.
     *
     * @param uri The URI representing a reference.
     * @param context A reference URI which provides context for any
     * relative reference accessors.
     * @return A reference which is identified by the provided URI.
     * @throws InvalidReferenceException If the current reference could
     * not be derived by the current environment, or if the context URI
     * is not valid in the current environment.
     */
    @Throws(InvalidReferenceException::class)
    fun DeriveReference(uri: String?, context: String?): Reference {
        @Suppress("NAME_SHADOWING")
        var uri = uri
        if (uri == null) {
            throw InvalidReferenceException("Null references aren't valid", null)
        }

        // Relative URI's need to determine their context first.
        return if (isRelative(uri)) {
            // Clean up the relative reference to lack any leading separators.
            if (uri.startsWith("./")) {
                uri = uri.substring(2)
            }

            if (context == null) {
                throw RuntimeException("Attempted to retrieve local reference with no context")
            } else {
                derivingRoot(context).derive(uri, context)
            }
        } else {
            derivingRoot(uri).derive(uri)
        }
    }

    /**
     * Adds a root translator that is maintained over the course of a session. It will be globally
     * available until the session is cleared using the "clearSession" method.
     *
     * @param translator A Root Translator that will be added to the current session
     */
    fun addSessionRootTranslator(translator: RootTranslator) {
        sessionTranslators.add(translator)
    }

    /**
     * Wipes out all of the translators being maintained in the current session (IE: Any translators
     * added via "addSessionRootTranslator". Used to manage a temporary set of translations for a limited
     * amount of time.
     */
    fun clearSession() {
        sessionTranslators.clear()
    }

    @Throws(InvalidReferenceException::class)
    private fun derivingRoot(uri: String): ReferenceFactory {
        // First, try any/all roots which are put in the temporary session stack
        for (root in sessionTranslators) {
            if (root.derives(uri)) {
                return root
            }
        }

        // Now, try any/all roots referenced at runtime.
        for (root in translators) {
            if (root.derives(uri)) {
                return root
            }
        }

        // Now try all of the raw connectors available
        for (root in factories) {
            if (root.derives(uri)) {
                return root
            }
        }

        throw InvalidReferenceException(getPrettyPrintException(uri), uri)
    }

    private fun getPrettyPrintException(uri: String): String {
        if ("" == uri) {
            return "Attempt to derive a blank reference"
        }
        try {
            var uriRoot = uri
            var jrRefMessagePortion = "reference type"
            if (uri.contains("jr://")) {
                uriRoot = uri.substring("jr://".length)
                jrRefMessagePortion = "javarosa jr:// reference root"
            }
            // For http:// style uri's
            var endOfRoot = uriRoot.indexOf("://") + "://".length
            if (endOfRoot == "://".length - 1) {
                endOfRoot = uriRoot.indexOf("/")
            }
            if (endOfRoot != -1) {
                uriRoot = uriRoot.substring(0, endOfRoot)
            }
            var message = "The reference \"$uri\" was invalid and couldn't be understood. The $jrRefMessagePortion \"$uriRoot\" is not available on this system and may have been mis-typed. Some available roots: "
            for (root in sessionTranslators) {
                message += "\n" + root.prefix
            }

            // Now, try any/all roots referenced at runtime.
            for (root in translators) {
                message += "\n" + root.prefix
            }

            // Now try all of the raw connectors available
            for (root in factories) {
                // TODO: Skeeeeeeeeeeeeetch
                try {
                    if (root is PrefixedRootFactory) {
                        for (rootName in root.roots) {
                            message += "\n" + rootName
                        }
                    } else {
                        message += "\n" + root.derive("").getURI()
                    }
                } catch (e: Exception) {
                    // ignored
                }
            }
            return message
        } catch (e: Exception) {
            return "Couldn't process the reference $uri . It may have been entered incorrectly. " +
                    "Note that this doesn't mean that this doesn't mean the file or location referenced " +
                    "couldn't be found, the reference itself was not understood."
        }
    }

    companion object {
        /**
         * @return Singleton accessor to the global ReferenceManager.
         */
        @JvmStatic
        fun instance(): ReferenceManager {
            return ReferenceHandler.instance()
        }

        /**
         * @return Whether the provided URI describe a relative reference.
         */
        @JvmStatic
        fun isRelative(URI: String): Boolean {
            return URI.startsWith("./")
        }
    }
}
