package org.javarosa.core.model.instance

/**
 * Allows manual exceptions triggering; useful for testing failure modes.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
class FormInstanceWithFailures(root: TreeElement) : FormInstance(root) {

    override fun setID(id: Int) {
        if (failOnIdSet) {
            throw RuntimeException("")
        } else {
            super.setID(id)
        }
    }

    companion object {
        private var failOnIdSet = false

        /**
         * Toggle failing setID behavior; useful for testing atomic database actions
         */
        @JvmStatic
        fun setFailOnIdSet(shouldFail: Boolean) {
            failOnIdSet = shouldFail
        }
    }
}
