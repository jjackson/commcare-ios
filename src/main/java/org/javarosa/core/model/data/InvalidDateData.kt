package org.javarosa.core.model.data

/**
 * Created by Saumya on 7/25/2016.
 */
class InvalidDateData(
    error: String,
    returnValue: IAnswerData,
    private val dayText: String,
    private val monthText: String,
    private val yearText: String
) : InvalidData(error, returnValue) {

    fun getDayText(): String {
        return dayText
    }

    fun getMonthText(): String {
        return monthText
    }

    fun getYearText(): String {
        return yearText
    }
}
