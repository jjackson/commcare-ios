package org.commcare.cases.instance

import org.commcare.cases.model.Case
import org.commcare.cases.model.CaseIndex
import org.commcare.cases.query.QueryContext
import org.commcare.cases.query.QuerySensitive
import org.javarosa.core.model.data.DateData
import org.javarosa.core.model.data.StringData
import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.utils.PreloadUtils

/**
 * @author ctsims
 */
class CaseChildElement : StorageBackedChildElement<Case>, QuerySensitive {

    private var empty: TreeElement? = null

    constructor(
        parent: StorageInstanceTreeElement<Case, *>,
        recordId: Int,
        caseId: String?,
        mult: Int
    ) : super(parent, mult, recordId, caseId, NAME_ID)

    /**
     * Template constructor (For elements that need to create reference nodesets
     * but never look up values)
     */
    private constructor(parent: CaseInstanceTreeElement) :
            super(parent, TreeReference.INDEX_TEMPLATE, TreeReference.INDEX_TEMPLATE, null, NAME_ID) {

        empty = TreeElement("case")
        empty!!.setMult(this._mult)

        empty!!.setAttribute(null, nameId, "")
        empty!!.setAttribute(null, "case_type", "")
        empty!!.setAttribute(null, "status", "")
        empty!!.setAttribute(null, "external_id", "")
        empty!!.setAttribute(null, "category", "")
        empty!!.setAttribute(null, "state", "")

        var scratch = TreeElement("case_name")
        scratch.setAnswer(null)
        empty!!.addChild(scratch)

        scratch = TreeElement("date_opened")
        scratch.setAnswer(null)
        empty!!.addChild(scratch)

        scratch = TreeElement("last_modified")
        scratch.setAnswer(null)
        empty!!.addChild(scratch)
    }

    override fun getName(): String? {
        return "case"
    }

    override fun getChildrenWithName(name: String): ArrayList<AbstractTreeElement> {
        //In order
        val cached = cache()
        val children = cached.getChildrenWithName(name)

        if (children.size == 0) {
            val emptyNode = TreeElement(name)
            cached.addChild(emptyNode)
            emptyNode.setParent(cached)
            children.add(emptyNode)
        }
        return children
    }

    //TODO: THIS IS NOT THREAD SAFE
    override fun cache(context: QueryContext?): TreeElement {
        if (recordId == TreeReference.INDEX_TEMPLATE) {
            return empty!!
        }
        synchronized(parent.treeCache) {
            val element = parent.treeCache.retrieve(recordId)
            if (element != null) {
                return element
            }
            //For now this seems impossible
            if (recordId == -1) {
                val ids = parent.storage.getIDsForValue(nameId, entityId as Any)
                recordId = ids[0]
            }

            val c = parent.getElement(recordId, context)
            entityId = c.getCaseId()

            return buildAndCacheInternalTree(c)
        }
    }

    private fun buildAndCacheInternalTree(c: Case): TreeElement {
        val cacheBuilder = TreeElement("case")
        cacheBuilder.setMult(this._mult)

        cacheBuilder.setAttribute(null, nameId, c.getCaseId())
        cacheBuilder.setAttribute(null, "case_type", c.getTypeId())
        cacheBuilder.setAttribute(null, "status", if (c.isClosed()) "closed" else "open")

        //Don't set anything to null
        cacheBuilder.setAttribute(null, "owner_id", if (c.getUserId() == null) "" else c.getUserId())

        if (c.getExternalId() != null) {
            cacheBuilder.setAttribute(null, "external_id", c.getExternalId())
        }

        if (c.getCategory() != null) {
            cacheBuilder.setAttribute(null, "category", c.getCategory())
        }

        if (c.getState() != null) {
            cacheBuilder.setAttribute(null, "state", c.getState())
        }

        val done = booleanArrayOf(false)

        var scratch = TreeElement("case_name")
        val name = c.getName()
        //This shouldn't be possible
        scratch.setAnswer(StringData(name ?: ""))
        cacheBuilder.addChild(scratch)

        scratch = TreeElement("date_opened")
        scratch.setAnswer(DateData(c.getDateOpened()!!))
        cacheBuilder.addChild(scratch)

        scratch = TreeElement(LAST_MODIFIED_KEY)
        scratch.setAnswer(DateData(c.getLastModified()!!))
        cacheBuilder.addChild(scratch)

        setCaseProperties(c, cacheBuilder)

        val index = buildIndexTreeElement(c, done)
        cacheBuilder.addChild(index)

        val attachments = buildAttachmentTreeElement(c, done)
        cacheBuilder.addChild(attachments)

        cacheBuilder.setParent(this.parent)
        done[0] = true

        parent.treeCache.register(recordId, cacheBuilder)
        return cacheBuilder
    }

    private fun setCaseProperties(c: Case, cacheBuilder: TreeElement) {
        val en = c.getProperties().keys.iterator()
        while (en.hasNext()) {
            val key = en.next() as String

            //this is an unfortunate complication of our internal model
            if (LAST_MODIFIED_KEY == key) {
                continue
            }

            val scratch = TreeElement(parent.intern(key))
            val temp = c.getProperty(key)
            if (temp is String) {
                scratch.setValue(UncastData(temp))
            } else {
                scratch.setValue(PreloadUtils.wrapIndeterminedObject(temp))
            }
            cacheBuilder.addChild(scratch, true)
        }
    }

    private fun buildIndexTreeElement(c: Case, done: BooleanArray): TreeElement {
        val parentRef = this.parent
        val index = object : TreeElement("index") {
            override fun getChild(name: String, multiplicity: Int): TreeElement? {
                val child = super.getChild(parentRef.intern(name), multiplicity)

                //TODO: Skeeeetchy, this is not a good way to do this,
                //should extract pattern instead.

                //If we haven't finished caching yet, we can safely not return
                //something useful here, so we can construct as normal.
                if (!done[0]) {
                    return child
                }

                //blank template index for repeats and such to not crash
                if (multiplicity >= 0 && child == null) {
                    val emptyNode = TreeElement(parentRef.intern(name))
                    emptyNode.setAttribute(null, "case_type", "")
                    emptyNode.setAttribute(null, "relationship", "")
                    this.addChild(emptyNode)
                    emptyNode.setParent(this)
                    return emptyNode
                }
                return child
            }

            override fun getChildrenWithName(name: String): ArrayList<AbstractTreeElement> {
                val children = super.getChildrenWithName(parentRef.intern(name))

                //If we haven't finished caching yet, we can safely not return
                //something useful here, so we can construct as normal.
                if (!done[0]) {
                    return children
                }

                if (children.size == 0) {
                    val emptyNode = TreeElement(name)
                    emptyNode.setAttribute(null, "case_type", "")
                    emptyNode.setAttribute(null, "relationship", "")

                    this.addChild(emptyNode)
                    emptyNode.setParent(this)
                    children.add(emptyNode)
                }
                return children
            }
        }

        val indices = c.getIndices()
        for (i in indices) {
            val scratch = TreeElement(i.getName())
            scratch.setAttribute(null, "case_type", parentRef.intern(i.getTargetType()!!))
            scratch.setAttribute(null, "relationship", parentRef.intern(i.getRelationship()!!))
            scratch.setValue(UncastData(i.getTarget()))
            index.addChild(scratch)
        }
        return index
    }

    private fun buildAttachmentTreeElement(c: Case, done: BooleanArray): TreeElement {
        val parentRef = this.parent
        val attachments = object : TreeElement("attachment") {
            override fun getChild(name: String, multiplicity: Int): TreeElement? {
                val child = super.getChild(parentRef.intern(name), multiplicity)

                //TODO: Skeeeetchy, this is not a good way to do this,
                //should extract pattern instead.

                //If we haven't finished caching yet, we can safely not return
                //something useful here, so we can construct as normal.
                if (!done[0]) {
                    return child
                }
                if (multiplicity >= 0 && child == null) {
                    val emptyNode = TreeElement(parentRef.intern(name))
                    this.addChild(emptyNode)
                    emptyNode.setParent(this)
                    return emptyNode
                }
                return child
            }
        }

        for (attachment in c.getAttachments()) {
            val scratch = TreeElement(attachment)
            scratch.setValue(UncastData(c.getAttachmentSource(attachment)))
            attachments.addChild(scratch)
        }
        return attachments
    }

    override fun prepareForUseInCurrentContext(queryContext: QueryContext) {
        cache(queryContext)
    }

    companion object {
        private const val NAME_ID = "case_id"
        private const val LAST_MODIFIED_KEY = "last_modified"

        @JvmStatic
        fun buildCaseChildTemplate(parent: CaseInstanceTreeElement): CaseChildElement {
            return CaseChildElement(parent)
        }
    }
}
