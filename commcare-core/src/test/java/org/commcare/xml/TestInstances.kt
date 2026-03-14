package org.commcare.xml

import org.commcare.data.xml.SimpleNode
import org.commcare.data.xml.TreeBuilder
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.core.model.instance.ExternalDataInstance.Companion.JR_CASE_DB_REFERENCE
import org.javarosa.core.model.instance.ExternalDataInstance.Companion.JR_SELECTED_ENTITIES_REFERENCE
import org.javarosa.core.model.instance.ExternalDataInstance.Companion.JR_SESSION_REFERENCE
import org.javarosa.core.model.instance.TreeElement

/**
 * Utilities for building mock [DataInstance] objects for tests.
 */
class TestInstances {
    companion object {
        private const val SELECTED_CASES = "selected-cases"
        private const val SESSION = "session"
        const val CASEDB = "casedb"

        @JvmStatic
        fun getInstances(): HashMap<String, DataInstance<*>> {
            val instances = HashMap<String, DataInstance<*>>()
            instances[SESSION] = buildSessionInstance()
            instances[SELECTED_CASES] = buildSelectedEntities()
            instances[CASEDB] = buildCaseDb()
            return instances
        }

        @JvmStatic
        fun buildSessionInstance(): ExternalDataInstance {
            val nodes = listOf(
                SimpleNode.textNode("case_id", emptyMap(), "bang")
            )
            val root = TreeBuilder.buildTree(SESSION, SESSION, nodes)
            return ExternalDataInstance(JR_SESSION_REFERENCE, SESSION, root)
        }

        @JvmStatic
        fun buildSelectedEntities(): ExternalDataInstance {
            val nodes = listOf(
                SimpleNode.textNode("value", emptyMap(), "123"),
                SimpleNode.textNode("value", emptyMap(), "456"),
                SimpleNode.textNode("value", emptyMap(), "789")
            )
            val root = TreeBuilder.buildTree(SELECTED_CASES, "session-data", nodes)
            return ExternalDataInstance(JR_SELECTED_ENTITIES_REFERENCE, SELECTED_CASES, root)
        }

        @JvmStatic
        fun buildCaseDb(): ExternalDataInstance {
            val nodes = listOf(
                SimpleNode.textNode("case", mapOf("case_id" to "123"), "123")
            )
            val root = TreeBuilder.buildTree(CASEDB, CASEDB, nodes)
            return ExternalDataInstance(JR_CASE_DB_REFERENCE, CASEDB, root)
        }

        @JvmStatic
        fun buildCaseDb(caseIds: List<String>): ExternalDataInstance {
            val nodes = caseIds.map { caseId ->
                SimpleNode.textNode("case", mapOf("case_id" to caseId), caseId)
            }
            val root = TreeBuilder.buildTree(CASEDB, CASEDB, nodes)
            return ExternalDataInstance(JR_CASE_DB_REFERENCE, CASEDB, root)
        }
    }
}
