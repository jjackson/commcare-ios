package org.commcare.xml

import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.ExternalDataInstance
import org.kxml2.io.KXmlParser
import java.util.Hashtable

class ParseInstance {
    companion object {
        @JvmStatic
        fun parseInstance(instances: Hashtable<String, DataInstance<*>>, parser: KXmlParser) {
            val instanceId = parser.getAttributeValue(null, "id")
            val location = parser.getAttributeValue(null, "src")
            instances[instanceId] = ExternalDataInstance(location, instanceId)
        }
    }
}
