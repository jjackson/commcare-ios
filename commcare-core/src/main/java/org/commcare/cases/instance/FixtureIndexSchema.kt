package org.commcare.cases.instance

import org.commcare.cases.model.StorageIndexedTreeElementModel
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.xml.util.InvalidStructureException
import java.util.HashSet
import java.util.Vector
import java.util.regex.Pattern

/**
 * Tracks what attributes and elements are stored in indexed columns of an
 * indexed fixture db table.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
class FixtureIndexSchema(schemaTree: TreeElement, @JvmField val fixtureName: String) {

    private val indices: MutableSet<String> = HashSet()

    init {
        setupIndices(schemaTree.getChildrenWithName("index"))
    }

    @Throws(InvalidStructureException::class)
    private fun setupIndices(indexElements: Vector<AbstractTreeElement>) {
        for (index in indexElements) {
            val value = index.getValue()
            if (value != null) {
                val indexString = value.uncast().getString() ?: continue
                validateIndexValue(indexString)
                indices.add(indexString)
            }
        }
    }

    /**
     * Break-up composite indices into individual ones and escape index names
     * to be SQL compatible
     */
    fun getColumnIndices(): Set<String> {
        val columnIndices: MutableSet<String> = HashSet()
        for (index in indices) {
            columnIndices.add(escapeIndex(index))
        }
        return columnIndices
    }

    /**
     * Set of indices, breaking apart composite indices
     * i.e. ("id", "name,dob") -> ("id", "name", "dob")
     */
    fun getSingleIndices(): Set<String> {
        val singleIndices: MutableSet<String> = HashSet()
        for (index in indices) {
            if (index.contains(",")) {
                for (part in index.split(",")) {
                    singleIndices.add(part)
                }
            } else {
                singleIndices.add(index)
            }
        }
        return singleIndices
    }

    companion object {
        @JvmStatic
        @Throws(InvalidStructureException::class)
        private fun validateIndexValue(index: String) {
            if (!Pattern.matches("^[a-zA-Z0-9,@_\\.-]+$", index)) {
                throw InvalidStructureException("Fixture schema contains an invalid index: '$index'")
            }
        }

        @JvmStatic
        fun escapeIndex(index: String): String {
            return if (index.contains(",")) {
                val compoundIndex = StringBuilder()
                var prefix = ""
                for (entry in index.split(",")) {
                    compoundIndex.append(prefix)
                    prefix = ","
                    compoundIndex.append(StorageIndexedTreeElementModel.getSqlColumnNameFromElementOrAttribute(entry))
                }
                compoundIndex.toString()
            } else {
                StorageIndexedTreeElementModel.getSqlColumnNameFromElementOrAttribute(index)
            }
        }
    }
}
