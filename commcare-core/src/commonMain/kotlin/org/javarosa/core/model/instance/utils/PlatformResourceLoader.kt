@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.instance.utils

import org.javarosa.core.io.PlatformInputStream

/**
 * Load a classpath resource as a stream.
 * On JVM: uses Class.getResourceAsStream().
 * On iOS: throws UnsupportedOperationException (no classpath resources on iOS).
 */
expect fun loadClasspathResource(path: String?): PlatformInputStream?
