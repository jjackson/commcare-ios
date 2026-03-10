package org.commcare.modern.database

import kotlin.jvm.JvmStatic

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
object DatabaseIndexingUtils {

    /**
     * Build SQL command to create an index on a table
     *
     * @param indexName        Name of index on the table
     * @param tableName        Table target of index being created
     * @param columnListString One or more columns used to create the index.
     *                         Multiple columns should be comma-seperated.
     * @return Indexed table creation SQL command.
     */
    @JvmStatic
    fun indexOnTableCommand(indexName: String,
                            tableName: String,
                            columnListString: String): String {
        return "CREATE INDEX IF NOT EXISTS $indexName ON $tableName( $columnListString )"
    }

    @JvmStatic
    fun getIndexStatements(tableName: String, indices: Set<String>): Array<String> {
        val indexStatements = Array(indices.size) { "" }
        var i = 0
        for (index in indices) {
            indexStatements[i++] = makeIndexingStatement(tableName, index)
        }
        return indexStatements
    }

    private fun makeIndexingStatement(tableName: String, index: String): String {
        var indexName = "fixture_${tableName}_${index}_index"
        if (index.contains(",")) {
            indexName = index.replace(",", "_") + "_index"
        }
        return indexOnTableCommand(indexName, tableName, index)
    }
}
