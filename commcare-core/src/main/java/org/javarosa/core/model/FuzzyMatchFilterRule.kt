package org.javarosa.core.model

import org.commcare.cases.util.StringUtils

/**
 * Filter rule for a combo box that accepts answer choice strings based on either
 * direct or fuzzy matches to the entered text
 */
class FuzzyMatchFilterRule : ComboboxFilterRule {

    override fun shouldRestrictTyping(): Boolean {
        // Since fuzzy match only works once the number of typed letters reaches a certain
        // threshold and is close to the number of letters in the comparison string, it doesn't
        // make any sense to restrict typing here
        return false
    }

    override fun choiceShouldBeShown(choice: ComboItem?, textEntered: CharSequence?): Boolean {
        if (textEntered == null || "" contentEquals textEntered) {
            return true
        }

        val textEnteredLowerCase = textEntered.toString().lowercase()
        val choiceLowerCase = choice!!.displayText.lowercase()

        // Try the easy cases first
        if (isSubstringOrFuzzyMatch(choiceLowerCase, textEnteredLowerCase, 2)) {
            return true
        }

        return allEnteredWordsHaveMatchOrFuzzyMatch(choiceLowerCase, textEnteredLowerCase)
    }

    companion object {
        private fun allEnteredWordsHaveMatchOrFuzzyMatch(
            choiceLowerCase: String,
            textEnteredLowerCase: String
        ): Boolean {
            val enteredWords = textEnteredLowerCase.split(" ")
            val wordsFromChoice = choiceLowerCase.split(" ")
            for (enteredWord in enteredWords) {
                var foundMatchForWord = false
                for (wordFromChoice in wordsFromChoice) {
                    if (isSubstringOrFuzzyMatch(wordFromChoice, enteredWord, 1)) {
                        foundMatchForWord = true
                        break
                    }
                }
                if (!foundMatchForWord) {
                    return false
                }
            }
            return true
        }

        private fun isSubstringOrFuzzyMatch(
            choiceLowerCase: String,
            textEnteredLowerCase: String,
            distanceThreshold: Int
        ): Boolean {
            return choiceLowerCase.contains(textEnteredLowerCase) ||
                    StringUtils.fuzzyMatch(textEnteredLowerCase, choiceLowerCase, distanceThreshold).first
        }
    }
}
