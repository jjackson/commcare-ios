package org.javarosa.core.util.externalizable

import kotlin.jvm.JvmField

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
class SerializationLimitationException(
    @JvmField val percentOversized: Int,
    cause: Throwable,
    message: String
) : RuntimeException(message, cause)
