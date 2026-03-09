package org.commcare.modern.models

/**
 * @author ctsims
 */
interface EncryptedModel {
    fun isEncrypted(data: String): Boolean

    fun isBlobEncrypted(): Boolean
}
