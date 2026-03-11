@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util.externalizable

/**
 * Minimal expect declaration for PrototypeFactory so that Externalizable can reference it
 * from commonMain. The full JVM implementation with Class<*>-based registration lives in jvmMain.
 */
expect open class PrototypeFactory()
