package org.javarosa.core.api

/**
 * The Module Interface represents an integration point
 * for an extensible set of JavaRosa code.
 *
 * @author Clayton Sims
 */
interface IModule {
    /**
     * Register Module should identify all configuration that
     * needs to occur for the elements that are contained within
     * a module, and perform that configuration and registration
     * with the current application.
     */
    fun registerModule()
}
