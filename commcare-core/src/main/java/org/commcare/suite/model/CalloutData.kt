package org.commcare.suite.model

import java.util.Hashtable
import java.util.Vector

/**
 * Evaluated form of Callout class where all XPaths have been processed.
 *
 * Created by wpride1 on 4/17/15.
 */
class CalloutData(
    val actionName: String?,
    val image: String?,
    val displayName: String?,
    val extras: Hashtable<String, String>,
    val responses: Vector<String>,
    val type: String?
)
