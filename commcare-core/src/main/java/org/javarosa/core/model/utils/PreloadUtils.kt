package org.javarosa.core.model.utils

import org.javarosa.core.model.data.DateData
import org.javarosa.core.model.data.DecimalData
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.data.LongData
import org.javarosa.core.model.data.SelectMultiData
import org.javarosa.core.model.data.StringData
import org.javarosa.core.model.data.helper.Selection
import org.javarosa.core.model.utils.PlatformDate

/**
 * @author Clayton Sims
 * @date Mar 30, 2009
 */
object PreloadUtils {

    /**
     * Note: This method is a hack to fix the problem that we don't know what
     * data type we're using when we have a preloader. That should get fixed,
     * and this method should be removed.
     */
    @JvmStatic
    fun wrapIndeterminedObject(o: Any?): IAnswerData? {
        if (o == null) {
            return null
        }

        //TODO: Replace this all with an uncast data
        return when (o) {
            is String -> StringData(o)
            is PlatformDate -> DateData(o)
            is Int -> IntegerData(o)
            is Long -> LongData(o)
            is Double -> DecimalData(o)
            is ArrayList<*> -> {
                @Suppress("UNCHECKED_CAST")
                SelectMultiData(o as ArrayList<Selection>)
            }
            is IAnswerData -> o
            else -> StringData(o.toString())
        }
    }
}
