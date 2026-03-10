package org.commcare.session

import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.util.OrderedHashtable
import java.util.Enumeration
import java.util.Hashtable
import java.util.Vector

object SessionInstanceBuilder {
    const val KEY_LAST_QUERY_STRING: String = "LAST_QUERY_STRING"
    const val KEY_ENTITY_LIST_EXTRA_DATA: String = "entity-list-data"

    @JvmStatic
    fun getSessionInstance(
        frame: SessionFrame, deviceId: String?,
        appversion: String?, drift: Long,
        username: String?, userId: String?,
        userFields: Hashtable<String, String>, windowWidth: String?,
        applanguage: String?
    ): TreeElement {
        val sessionRoot = TreeElement("session", 0)

        addSessionNavData(sessionRoot, frame)
        addMetadata(sessionRoot, deviceId, appversion, username, userId, drift, windowWidth, applanguage)
        addUserProperties(sessionRoot, userFields)
        return sessionRoot
    }

    private fun addSessionNavData(sessionRoot: TreeElement, frame: SessionFrame) {
        val sessionData = TreeElement("data", 0)
        addDatums(sessionData, frame)
        addUserQueryData(sessionData, frame)
        sessionRoot.addChild(sessionData)
    }

    /**
     * Add datums chosen by user to the session
     */
    private fun addDatums(sessionData: TreeElement, frame: SessionFrame) {
        for (step in frame.getSteps()) {
            if (SessionFrame.isEntitySelectionDatum(step.getType()) ||
                SessionFrame.STATE_DATUM_COMPUTED == step.getType()
            ) {
                val matchingElements: Vector<AbstractTreeElement> =
                    sessionData.getChildrenWithName(step.getId()!!)
                if (matchingElements.size > 0) {
                    (matchingElements.elementAt(0) as TreeElement).setValue(UncastData(step.getValue()))
                } else {
                    addData(sessionData, step.getId()!!, step.getValue())
                }
            }
        }
    }

    /**
     * Add data to session tracking queries user made before entering form
     */
    private fun addUserQueryData(sessionData: TreeElement, frame: SessionFrame) {
        for (step in frame.getSteps()) {
            val textSearch = getStringQuery(step)
            if (textSearch != null) {
                addData(sessionData, "stringquery", textSearch)
            }

            val calloutResultCount = getCalloutSearchResultCount(step)
            if (calloutResultCount != null) {
                addData(sessionData, "fingerprintquery", calloutResultCount)
            }
        }
    }

    private fun getStringQuery(step: org.commcare.suite.model.StackFrameStep): String? {
        val extra = step.getExtra(KEY_LAST_QUERY_STRING)
        if (extra != null && extra is String && "" != extra) {
            return extra
        }
        return null
    }

    private fun getCalloutSearchResultCount(step: org.commcare.suite.model.StackFrameStep): String? {
        val entitySelectCalloutSearch = step.getExtra(KEY_ENTITY_LIST_EXTRA_DATA)
        if (entitySelectCalloutSearch != null && entitySelectCalloutSearch is OrderedHashtable<*, *>) {
            return "" + entitySelectCalloutSearch.keys.size
        }
        return null
    }

    private fun addMetadata(
        sessionRoot: TreeElement, deviceId: String?,
        appversion: String?, username: String?,
        userId: String?, drift: Long, windowWidth: String?,
        applanguage: String?
    ) {
        val sessionMeta = TreeElement("context", 0)

        addData(sessionMeta, "deviceid", deviceId)
        addData(sessionMeta, "appversion", appversion)
        addData(sessionMeta, "username", username)
        addData(sessionMeta, "userid", userId)
        addData(sessionMeta, "drift", drift.toString())
        addData(sessionMeta, "window_width", windowWidth)
        addData(sessionMeta, "applanguage", applanguage)
        sessionRoot.addChild(sessionMeta)
    }

    private fun addUserProperties(
        sessionRoot: TreeElement,
        userFields: Hashtable<String, String>
    ) {
        val user = TreeElement("user", 0)
        val userData = TreeElement("data", 0)
        user.addChild(userData)
        val en: Enumeration<String> = userFields.keys()
        while (en.hasMoreElements()) {
            val key = en.nextElement()
            addData(userData, key, userFields[key])
        }

        sessionRoot.addChild(user)
    }

    private fun addData(root: TreeElement, name: String, data: String?) {
        val datum = TreeElement(name, root.getChildMultiplicity(name))
        if (data != null) {
            datum.setValue(UncastData(data))
            root.addChild(datum)
        }
    }
}
