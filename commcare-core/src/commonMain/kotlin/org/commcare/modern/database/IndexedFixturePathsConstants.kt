package org.commcare.modern.database

import kotlin.jvm.JvmField

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
object IndexedFixturePathsConstants {

    @JvmField
    val INDEXED_FIXTURE_PATHS_TABLE = "IndexedFixtureIndex"
    @JvmField
    val INDEXED_FIXTURE_PATHS_COL_NAME = "name"
    @JvmField
    val INDEXED_FIXTURE_PATHS_COL_BASE = "base"
    @JvmField
    val INDEXED_FIXTURE_PATHS_COL_CHILD = "child"
    @JvmField
    val INDEXED_FIXTURE_PATHS_COL_ATTRIBUTES = "attributes"

    @JvmField
    val INDEXED_FIXTURE_PATHS_TABLE_STMT =
        "CREATE TABLE IF NOT EXISTS " +
                INDEXED_FIXTURE_PATHS_TABLE +
                " (" + INDEXED_FIXTURE_PATHS_COL_NAME + " UNIQUE" +
                ", " + INDEXED_FIXTURE_PATHS_COL_BASE +
                ", " + INDEXED_FIXTURE_PATHS_COL_CHILD +
                ", " + INDEXED_FIXTURE_PATHS_COL_ATTRIBUTES + ");"

    @JvmField
    val INDEXED_FIXTURE_INDEXING_STMT =
        DatabaseIndexingUtils.indexOnTableCommand("fixture_name_index",
            INDEXED_FIXTURE_PATHS_TABLE, INDEXED_FIXTURE_PATHS_COL_NAME)

    @JvmField
    val INDEXED_FIXTURE_PATHS_TABLE_STMT_V15 =
        "CREATE TABLE IF NOT EXISTS " +
                INDEXED_FIXTURE_PATHS_TABLE +
                " (" + INDEXED_FIXTURE_PATHS_COL_NAME + " UNIQUE" +
                ", " + INDEXED_FIXTURE_PATHS_COL_BASE +
                ", " + INDEXED_FIXTURE_PATHS_COL_CHILD + ");"

    @JvmField
    val INDEXED_FIXTURE_PATHS_TABLE_SELECT_STMT =
        "SELECT " +
                INDEXED_FIXTURE_PATHS_COL_BASE + ", " +
                INDEXED_FIXTURE_PATHS_COL_CHILD + ", " +
                INDEXED_FIXTURE_PATHS_COL_ATTRIBUTES +
                " FROM " + INDEXED_FIXTURE_PATHS_TABLE +
                " WHERE " + INDEXED_FIXTURE_PATHS_COL_NAME + " = ?;"
}
