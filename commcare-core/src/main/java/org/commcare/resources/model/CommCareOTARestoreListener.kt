package org.commcare.resources.model

interface CommCareOTARestoreListener {

    /**
     * Called by the parseBlock method every time the restore task successfully
     * parses a new block of the restore form
     */
    fun onUpdate(numberCompleted: Int)

    /**
     * Called when the parser encounters the "<items>" property in the restore file
     * Enables the progress bar
     */
    fun setTotalForms(totalItemCount: Int)
}
