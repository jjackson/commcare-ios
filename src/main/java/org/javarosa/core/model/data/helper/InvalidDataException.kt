package org.javarosa.core.model.data.helper

import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.data.UncastData

/**
 * @author ctsims
 */
class InvalidDataException(message: String, private val standin: UncastData) : Exception(message) {

    /**
     * Used by J2ME
     */
    fun getUncastStandin(): IAnswerData {
        return standin
    }
}
