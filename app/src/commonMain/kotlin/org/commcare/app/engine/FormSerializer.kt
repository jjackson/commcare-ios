package org.commcare.app.engine

import org.javarosa.core.model.FormDef
import org.javarosa.model.xform.DataModelSerializer
import org.javarosa.xml.createXmlSerializer

/**
 * Cross-platform form instance serializer.
 * Uses DataModelSerializer + PlatformXmlSerializer to produce XML bytes
 * from a completed FormDef, working on both JVM and iOS.
 */
object FormSerializer {

    /**
     * Serialize a completed form's instance data to XML string.
     */
    fun serializeForm(formDef: FormDef): String {
        val instance = formDef.getInstance() ?: throw IllegalStateException("No form instance")
        val serializer = createXmlSerializer()
        val dms = DataModelSerializer(serializer)
        val root = instance.getRoot()
        dms.serialize(root)
        return serializer.toByteArray().decodeToString()
    }
}
