package org.commcare.suite.model


/**
 * Evaluated form of Callout class where all XPaths have been processed.
 *
 * Created by wpride1 on 4/17/15.
 */
class CalloutData(
    val actionName: String?,
    val image: String?,
    val displayName: String?,
    val extras: HashMap<String, String>,
    val responses: ArrayList<String>,
    val type: String?
)
