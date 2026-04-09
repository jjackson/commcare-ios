@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.instance.utils

import org.javarosa.core.io.PlatformInputStream
import org.javarosa.core.io.createByteArrayInputStream

/**
 * On iOS there is no classpath — resources that commcare-core normally
 * loads from `src/main/resources/` must be either bundled as iOS app
 * resources or (for small, rarely-changing static schemas) embedded as
 * string constants.
 *
 * This map embeds the small static JavaRosa instance-structure schemas
 * that the engine needs at form-load time. Without them, any form whose
 * nodeset references `instance('casedb')/...` fails to load with
 * "UnsupportedOperationException: Classpath resource loading not
 * available on iOS". Register Household works (no casedb reference) but
 * Visit, Close, Edit, etc. don't. Keeping the schema inline is
 * appropriate because it is stable, version-locked to the engine, and
 * bundling it as an app resource would require xcodeproj changes
 * without any benefit.
 */
private val EMBEDDED_RESOURCES: Map<String, String> = mapOf(
    "/casedb_instance_structure.xml" to """<wrapper>
    <case case_id="" case_type="" owner_id="" status="" external_id="" state="" category="">
        <!-- case_id: The unique GUID of this case -->
        <!-- case_type: The id of this case's type -->
        <!-- owner_id: The GUID of the case or group which owns this case -->
        <!-- status: 'open' if the case has not been closed. 'closed' if the case has -->
        <case_name/>
        <!-- The name of the case-->
        <date_opened/>
        <!-- The date this case was opened -->
        <last_modified/>
        <!-- The date of the case's last transaction -->
        <CASEDB_WILD_CARD/>
        <!-- An arbitrary data value set in this case -->
        <index>
            <CASEDB_WILD_CARD case_type="" relationship=""/>
            <!-- An index to another case of the given type -->
            <!-- @case_type: Exactly one - the type of the indexed case -->
            <!-- @relationship: Exactly one - the relationship of this case to the indexed case. See the casexml spec for details -->
        </index>
        <attachment>
            <CASEDB_WILD_CARD/>
            <!-- A named element which provides a reference to an attachment in the local environment. This attachment may or may not be currently available (if it is being processed asynchronously, for instance, but should have a valid JR reference URI either way whose existence can be checked.-->
        </attachment>
    </case>
</wrapper>
"""
)

actual fun loadClasspathResource(path: String?): PlatformInputStream? {
    if (path == null) return null
    val content = EMBEDDED_RESOURCES[path]
        ?: throw UnsupportedOperationException(
            "Classpath resource not embedded for iOS: $path. " +
                "Add it to EMBEDDED_RESOURCES in PlatformResourceLoader.kt " +
                "if it's a stable engine schema."
        )
    return createByteArrayInputStream(content.encodeToByteArray())
}
