@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.instance.utils

import org.javarosa.core.io.PlatformInputStream

actual fun loadClasspathResource(path: String?): PlatformInputStream? {
    throw UnsupportedOperationException("Classpath resource loading not available on iOS")
}
