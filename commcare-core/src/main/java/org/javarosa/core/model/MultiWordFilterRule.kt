package org.javarosa.core.model

/**
 * Filter rule for a combo box that uses a more flexible filtering rule intended for when
 * answer choices are expected to contain multiple words.
 *
 * @author Aliza Stone
 */
class MultiWordFilterRule : ComboboxFilterRule {

    override fun shouldRestrictTyping(): Boolean {
        return true
    }

    /**
     * @param choice - the answer choice to be considered
     * @param textEntered - the text entered by the user
     * @return true if choiceLowerCase contains any word within textEntered (the "words" of
     * textEntered are obtained by splitting textEntered on " ")
     */
    override fun choiceShouldBeShown(choice: ComboItem?, textEntered: CharSequence?): Boolean {
        if (textEntered == null || "" contentEquals textEntered) {
            return true
        }
        val choiceLowerCase = choice!!.displayText.lowercase()
        val enteredTextIndividualWords = textEntered.toString().split(" ")
        for (word in enteredTextIndividualWords) {
            if (!choiceLowerCase.contains(word.lowercase())) {
                return false
            }
        }
        return true
    }
}
