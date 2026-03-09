package org.javarosa.core.services.locale

import org.javarosa.core.util.externalizable.Externalizable
import java.util.Hashtable

/**
 * @author Clayton Sims
 */
interface LocaleDataSource : Externalizable {
    fun getLocalizedText(): Hashtable<String, String>
}
