package org.javarosa.core.model

/**
 * Class to represent a combo box item for ComboboxAdapters
 */
class ComboItem(
    @JvmField val displayText: String,
    @JvmField val value: String,
    @JvmField val selectChoiceIndex: Int
) {

    override fun toString(): String {
        return displayText
    }

    override fun equals(other: Any?): Boolean {
        if (other is ComboItem) {
            return other.displayText == displayText &&
                    other.value == value &&
                    other.selectChoiceIndex == selectChoiceIndex
        }
        return false
    }

    override fun hashCode(): Int {
        var result = displayText.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + selectChoiceIndex
        return result
    }
}
