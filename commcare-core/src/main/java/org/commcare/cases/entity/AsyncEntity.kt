package org.commcare.cases.entity

import org.javarosa.core.util.platformSynchronized
import org.commcare.cases.entity.EntityStorageCache.ValueType.TYPE_NORMAL_FIELD
import org.commcare.cases.entity.EntityStorageCache.ValueType.TYPE_SORT_FIELD
import org.commcare.cases.util.StringUtils
import org.commcare.suite.model.Detail
import org.commcare.suite.model.DetailField
import org.commcare.suite.model.DetailGroup
import org.commcare.suite.model.Text
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.services.Logger
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException

/**
 * An AsyncEntity is an entity reference which is capable of building its
 * values (evaluating all Text elements/background data elements) lazily
 * rather than upfront when the entity is constructed.
 *
 * It is threadsafe.
 *
 * It will attempt to Cache its values persistently by a derived entity key rather
 * than evaluating them each time when possible. This can be slow to perform across
 * all entities internally due to the overhead of establishing the db connection, it
 * is recommended that the entities be primed externally with a bulk query.
 *
 * @author ctsims
 */
class AsyncEntity(
    detail: Detail,
    private val context: EvaluationContext,
    t: TreeReference,
    private val mVariableDeclarations: HashMap<String, XPathExpression>,
    private val mEntityStorageCache: EntityStorageCache?,
    private val mCacheIndex: String?,
    extraKey: String?
) : Entity<TreeReference>(t, extraKey) {

    private val fields: Array<DetailField> = detail.fields
    private val data: Array<Any?> = arrayOfNulls(fields.size)
    private val sortData: Array<String?> = arrayOfNulls(fields.size)
    private val relevancyData: Array<Boolean?> = arrayOfNulls(fields.size)
    private val sortDataPieces: Array<Array<String>?> = arrayOfNulls(fields.size)
    private val altTextData: Array<String?> = arrayOfNulls(fields.size)
    private val mDetailGroup: DetailGroup? = detail.group
    private val cacheEnabled: Boolean = detail.isCacheEnabled
    private var mVariableContextLoaded = false
    private val mDetailId: String = detail.id ?: ""

    /*
     * the Object's lock for the integrity of this object
     */
    private val mAsyncLock = Any()

    private fun loadVariableContext() {
        platformSynchronized(mAsyncLock) {
            if (!mVariableContextLoaded) {
                // These are actually in an ordered hashtable, so we can't just get the keyset, since it's
                // in a 1.3 hashtable equivalent
                val en: Iterator<String> = mVariableDeclarations.keys.iterator()
                while (en.hasNext()) {
                    val key = en.next()
                    context.setVariable(key, FunctionUtils.unpack(mVariableDeclarations[key]!!.eval(context)))
                }
                mVariableContextLoaded = true
            }
        }
    }

    override fun getField(i: Int): Any? {
        platformSynchronized(mAsyncLock) {
            if (data[i] != null) {
                return data[i]
            }
            if (!fields[i].isCacheEnabled) {
                data[i] = evaluateField(i)
                return data[i]
            }
            var cacheKey: String? = null
            if (data[i] == null) {
                if (mEntityStorageCache != null && mCacheIndex != null) {
                    cacheKey = mEntityStorageCache.getCacheKey(mDetailId, i.toString(),
                        TYPE_NORMAL_FIELD)
                    // Return from the cache if we have a value
                    val value = mEntityStorageCache.retrieveCacheValue(mCacheIndex, cacheKey)
                    if (value != null) {
                        data[i] = value
                        return data[i]
                    }
                }
            }
            data[i] = evaluateField(i)
            if (mEntityStorageCache != null && mCacheIndex != null) {
                mEntityStorageCache.cache(mCacheIndex, cacheKey, data[i].toString())
            }
        }
        return data[i]
    }

    private fun evaluateField(i: Int): Any? {
        loadVariableContext()
        try {
            data[i] = fields[i].template?.evaluate(context)
        } catch (xpe: XPathException) {
            Logger.exception("Error while evaluating field for case list ", xpe)
            data[i] = "<invalid xpath: ${xpe.message}>"
        }
        return data[i]
    }

    override fun getNormalizedField(i: Int): String {
        val normalized = this.getSortField(i) ?: return ""
        return normalized
    }

    override fun getSortField(i: Int): String? {
        platformSynchronized(mAsyncLock) {
            if (sortData[i] != null) {
                return sortData[i]
            }

            // eval and return if field is not marked as optimize
            if (cacheEnabled && !fields[i].isCacheEnabled) {
                evaluateSortData(i)
                return sortData[i]
            }

            var cacheKey: String? = null
            if (sortData[i] == null) {
                val sortText: Text? = fields[i].sort
                if (sortText == null) {
                    return null
                }

                if (mEntityStorageCache != null) {
                    cacheKey = if (cacheEnabled) {
                        mEntityStorageCache.getCacheKey(mDetailId, i.toString(),
                            TYPE_SORT_FIELD)
                    } else {
                        // old cache and index
                        "${i}_${TYPE_SORT_FIELD}"
                    }
                    if (mCacheIndex != null) {
                        // Check the cache!
                        val value = mEntityStorageCache.retrieveCacheValue(mCacheIndex, cacheKey)
                        if (value != null) {
                            this.setSortData(i, value)
                            return sortData[i]
                        }
                    }
                }
            }
            evaluateSortData(i)
            if (mEntityStorageCache != null && mCacheIndex != null) {
                mEntityStorageCache.cache(mCacheIndex, cacheKey, sortData[i])
            }
            return sortData[i]
        }
    }

    private fun evaluateSortData(i: Int) {
        loadVariableContext()
        try {
            val sortText: Text? = fields[i].sort
            if (sortText == null) {
                this.setSortData(i, getFieldString(i))
            } else {
                this.setSortData(i, StringUtils.normalize(sortText.evaluate(context)))
            }
        } catch (xpe: XPathException) {
            Logger.exception("Error while evaluating sort field", xpe)
            sortData[i] = "<invalid xpath: ${xpe.message}>"
        }
    }

    override fun getNumFields(): Int {
        return fields.size
    }

    override fun isValidField(fieldIndex: Int): Boolean {
        // NOTE: This totally jacks the asynchronicity. It's only used in
        // detail fields for now, so not super important, but worth bearing
        // in mind
        platformSynchronized(mAsyncLock) {
            loadVariableContext()
            if (getField(fieldIndex) == "") {
                return false
            }
            return getRelevancyData(fieldIndex)
        }
    }

    private fun getRelevancyData(i: Int): Boolean {
        platformSynchronized(mAsyncLock) {
            val cached = relevancyData[i]
            if (cached != null) {
                return cached
            }
            loadVariableContext()
            try {
                relevancyData[i] = fields[i].isRelevant(context)
            } catch (e: XPathSyntaxException) {
                val msg = "Invalid relevant condition for field : ${fields[i].header}"
                Logger.exception(msg, e)
                throw XPathException(e)
            }
            return relevancyData[i]!!
        }
    }

    override fun getData(): Array<Any?> {
        for (i in 0 until this.getNumFields()) {
            this.getField(i)
        }
        return data
    }

    override fun getSortFieldPieces(i: Int): Array<String> {
        if (getSortField(i) == null) {
            return emptyArray()
        }
        return sortDataPieces[i] ?: emptyArray()
    }

    private fun setSortData(i: Int, value: String) {
        platformSynchronized(mAsyncLock) {
            this.sortData[i] = value
            this.sortDataPieces[i] = breakUpField(value)
        }
    }

    private fun setFieldData(i: Int, value: String) {
        platformSynchronized(mAsyncLock) {
            data[i] = value
        }
    }

    fun setSortData(cacheKey: String, value: String) {
        if (mEntityStorageCache == null) {
            throw IllegalStateException("No entity cache defined")
        }
        val fieldIndex = mEntityStorageCache.getFieldIdFromCacheKey(mDetailId, cacheKey)
        if (fieldIndex != -1) {
            setSortData(fieldIndex, value)
        }
    }

    fun setFieldData(cacheKey: String, value: String) {
        if (mEntityStorageCache == null) {
            throw IllegalStateException("No entity cache defined")
        }
        val fieldIndex = mEntityStorageCache.getFieldIdFromCacheKey(mDetailId, cacheKey)
        if (fieldIndex != -1) {
            setFieldData(fieldIndex, value)
        }
    }

    override fun getGroupKey(): String? {
        val group = mDetailGroup ?: return null
        return group.function?.eval(context) as String?
    }

    fun getAltTextData(i: Int): String? {
        platformSynchronized(mAsyncLock) {
            if (altTextData[i] != null) {
                return altTextData[i]
            }
            loadVariableContext()
            val altText: Text? = fields[i].altText
            if (altText != null) {
                try {
                    altTextData[i] = altText.evaluate(context)
                } catch (xpe: XPathException) {
                    Logger.exception("Error while evaluating field for case list ", xpe)
                    altTextData[i] = "<invalid xpath: ${xpe.message}>"
                }
            }
            return altTextData[i]
        }
    }

    override fun getAltText(): Array<String?> {
        for (i in 0 until this.getNumFields()) {
            this.getAltTextData(i)
        }
        return altTextData
    }

    companion object {
        private fun breakUpField(input: String?): Array<String> {
            if (input == null) {
                return emptyArray()
            } else {
                // We always fuzzy match on the sort field and only if it is available
                // (as a way to restrict possible matching)
                return input.split("\\s+".toRegex()).toTypedArray()
            }
        }
    }
}
