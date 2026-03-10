package org.commcare.resources.model

import kotlin.jvm.JvmField

/**
 * Represents an install issue caused by resource having invalid content (like mismatched xml tag)
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
class InvalidResourceException(
    @JvmField val resourceName: String,
    msg: String
) : RuntimeException(msg)
