@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util

actual open class PlatformParseException actual constructor(
    message: String,
    @Suppress("unused") val errorOffset: Int
) : Exception(message)
