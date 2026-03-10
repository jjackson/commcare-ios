package org.javarosa.core.model

/**
 * Filter rule for a combo box that accepts answer choice strings based on simple prefix logic
 *
 * @author Aliza Stone
 */
class StandardFilterRule : ComboboxFilterRule {

    override fun shouldRestrictTyping(): Boolean {
        return true
    }

    override fun choiceShouldBeShown(choice: ComboItem?, textEntered: CharSequence?): Boolean {
        if (textEntered == null || "" contentEquals textEntered) {
            return true
        }
        return choice!!.displayText.lowercase().startsWith(textEntered.toString().lowercase())
    }
}
