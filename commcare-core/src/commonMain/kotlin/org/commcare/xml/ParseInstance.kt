package org.commcare.xml

import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.xml.PlatformXmlParser
import kotlin.jvm.JvmStatic

class ParseInstance {
    companion object {
        @JvmStatic
        fun parseInstance(instances: HashMap<String, DataInstance<*>>, parser: PlatformXmlParser) {
            val instanceId = parser.getAttributeValue(null, "id")
            val location = parser.getAttributeValue(null, "src")
            instances[instanceId!!] = ExternalDataInstance(location!!, instanceId)
        }
    }
}
