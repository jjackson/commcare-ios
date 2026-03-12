package org.commcare.resources.model

/**
 * @author ctsims
 */
interface TableStateListener {
    /**
     * A basic resource was added to the table
     */
    fun simpleResourceAdded()

    /**
     * A compound resource (i.e. profile or suite) was added to the table.
     * There might now be more resources that need to be processed than before.
     *
     * @param table For calculating updated completed and total resource counts
     */
    fun compoundResourceAdded(table: ResourceTable)

    fun incrementProgress(complete: Int, total: Int)
}
