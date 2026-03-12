package org.javarosa.xml.util

import org.javarosa.core.services.locale.Localization
import org.javarosa.core.util.NoLocalizedTextException

/**
 * Invalid structure error that can _potentially_ be recovered from via
 * advanced user intervention. Useful for notifying the user that the issue lies
 * on the server.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
class ActionableInvalidStructureException(
    private val localizationKey: String?,
    private val localizationParameters: Array<String>,
    message: String
) : InvalidStructureException(message) {

    fun getLocalizedMessage(): String {
        if (localizationKey != null) {
            return try {
                Localization.get(localizationKey, localizationParameters)
            } catch (e: NoLocalizedTextException) {
                message ?: ""
            }
        }
        return message ?: ""
    }
}
