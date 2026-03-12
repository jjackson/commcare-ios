package org.javarosa.core.model.data

import org.javarosa.core.model.Constants
import kotlin.jvm.JvmStatic

/**
 * This is not a factory, actually, since there's no drop-in component model, but
 * it could be in the future. Just wanted a centralized place to dispatch common
 * templating methods.
 *
 * In the future this could be a legitimate factory which is stored in... say...
 * the evaluation context
 *
 * @author ctsims
 */
object AnswerDataFactory {

    /**
     * The one-template to rule them all. Takes in a control type and a
     * data type and returns the appropriate answer data template with
     * which to cast incoming values.
     *
     * All enormous spaghetti ifs should be replaced with a call to this
     */
    @JvmStatic
    fun template(controlType: Int, datatype: Int): IAnswerData {
        // First take care of the easy two, selections, since their
        // datatype is implicit
        if (controlType == Constants.CONTROL_SELECT_ONE) {
            return SelectOneData()
        }

        if (controlType == Constants.CONTROL_SELECT_MULTI) {
            return SelectMultiData()
        }

        // That's actually it for now, we might have more in the future
        // so now return the template based on just data
        return templateByDataType(datatype)
    }

    @JvmStatic
    fun templateByDataType(datatype: Int): IAnswerData {
        return when (datatype) {
            Constants.DATATYPE_CHOICE -> SelectOneData()
            Constants.DATATYPE_CHOICE_LIST -> SelectMultiData()
            Constants.DATATYPE_BOOLEAN -> BooleanData()
            Constants.DATATYPE_DATE -> DateData()
            Constants.DATATYPE_DATE_TIME -> DateTimeData()
            Constants.DATATYPE_DECIMAL -> DecimalData()
            Constants.DATATYPE_GEOPOINT -> GeoPointData()
            Constants.DATATYPE_INTEGER -> IntegerData()
            Constants.DATATYPE_LONG -> LongData()
            Constants.DATATYPE_TEXT -> StringData()
            Constants.DATATYPE_TIME -> TimeData()

            // All of these are things that might require other manipulations in the future, but
            // for now can all just live as untyped
            Constants.DATATYPE_BARCODE,
            Constants.DATATYPE_BINARY,
            Constants.DATATYPE_UNSUPPORTED,
            Constants.DATATYPE_NULL -> UncastData()

            // If this is new and we don't know what's going on, just leave it untyped
            else -> UncastData()
        }
    }
}
