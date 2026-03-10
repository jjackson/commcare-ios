package org.javarosa.core.services.locale

import org.javarosa.core.util.externalizable.Externalizable

/**
 * @author Clayton Sims
 */
interface LocaleDataSource : Externalizable {
    fun getLocalizedText(): HashMap<String, String>
}
