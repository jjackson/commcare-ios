package org.javarosa.core.model.condition

import org.javarosa.core.model.Constants
import org.javarosa.core.model.FormDef
import org.javarosa.core.model.data.BooleanData
import org.javarosa.core.model.data.DateData
import org.javarosa.core.model.data.DateTimeData
import org.javarosa.core.model.data.DecimalData
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.data.LongData
import org.javarosa.core.model.data.SelectMultiData
import org.javarosa.core.model.data.SelectOneData
import org.javarosa.core.model.data.StringData
import org.javarosa.core.model.data.TimeData
import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.xpath.XPathException
import org.javarosa.core.model.utils.PlatformDate
import kotlin.math.abs
import kotlin.jvm.JvmStatic

class Recalculate : Triggerable {

    @Suppress("unused")
    constructor() {
        // for externalization
    }

    constructor(expr: IConditionExpr?, contextRef: TreeReference?) : super(expr, contextRef)

    override fun eval(instance: FormInstance?, ec: EvaluationContext?): Any? {
        try {
            return expr!!.evalRaw(instance, ec)
        } catch (e: XPathException) {
            e.setMessagePrefix("Calculation Error: Error in calculation for " + contextRef!!.toString(true))
            throw e
        }
    }

    override fun apply(ref: TreeReference?, result: Any?, instance: FormInstance?, f: FormDef?) {
        val currentRef = ref!!
        val dataType = f!!.getMainInstance()!!.resolveReference(currentRef)!!.getDataType()
        f.setAnswer(wrapData(result, dataType), currentRef)
    }

    override fun canCascade(): Boolean {
        return true
    }

    /**
     * Recalculates are equal based on their triggers and identity as a calculate triggerable
     */
    override fun equals(other: Any?): Boolean {
        if (other is Recalculate) {
            return this === other || super.equals(other)
        }
        return false
    }

    override fun hashCode(): Int {
        return "calculate".hashCode() xor super.hashCode()
    }

    override fun getDebugLabel(): String {
        return "calculate"
    }

    companion object {
        // droos 1/29/10: we need to come up with a consistent rule for whether the resulting data is determined
        // by the type of the instance node, or the type of the expression result. right now it's a mix and a mess
        // note a caveat with going solely by instance node type is that untyped nodes default to string!

        // for now, these are the rules:
        // if node type == bool, convert to boolean (for numbers, zero = f, non-zero = t; empty string = f, all other datatypes -> error)
        // if numeric data, convert to int if node type is int OR data is an integer; else convert to double
        // if string data or date data, keep as is
        // if NaN or empty string, null

        /**
         * convert the data object returned by the xpath expression into an IAnswerData suitable for
         * storage in the FormInstance
         */
        @JvmStatic
        fun wrapData(`val`: Any?, dataType: Int): IAnswerData? {
            if ((`val` is String && `val`.length == 0) ||
                (`val` is Double && `val`.isNaN())) {
                return null
            }

            if (Constants.DATATYPE_BOOLEAN == dataType || `val` is Boolean) {
                // ctsims: We should really be using the boolean datatype for real, it's
                // necessary for backend calculations and XSD compliance

                val b: Boolean

                if (`val` is Boolean) {
                    b = `val`
                } else if (`val` is Double) {
                    b = abs(`val`) > 1.0e-12 && !`val`.isNaN()
                } else if (`val` is String) {
                    b = `val`.length > 0
                } else {
                    throw RuntimeException("unrecognized data representation while trying to convert to BOOLEAN")
                }

                return BooleanData(b)
            } else if (`val` is Double) {
                val d = `val`
                val l = d.toLong()
                val isIntegral = abs(d - l) < 1.0e-9
                if (Constants.DATATYPE_INTEGER == dataType ||
                    (isIntegral && Integer.MAX_VALUE >= l && Integer.MIN_VALUE <= l)) {
                    return IntegerData(d.toInt())
                } else if (Constants.DATATYPE_LONG == dataType || isIntegral) {
                    return LongData(d.toLong())
                } else {
                    return DecimalData(d)
                }
            } else if (dataType == Constants.DATATYPE_CHOICE) {
                return SelectOneData().cast(UncastData(`val`.toString()))
            } else if (dataType == Constants.DATATYPE_CHOICE_LIST) {
                return SelectMultiData().cast(UncastData(`val`.toString()))
            } else if (`val` is String) {
                return StringData(`val`)
            } else if (`val` is PlatformDate) {
                return if (dataType == Constants.DATATYPE_DATE_TIME) {
                    DateTimeData(`val`)
                } else if (dataType == Constants.DATATYPE_TIME) {
                    TimeData(`val`)
                } else {
                    DateData(`val`)
                }
            } else {
                throw RuntimeException("unrecognized data type in 'calculate' expression: " + (`val`!!::class.simpleName ?: "unknown"))
            }
        }
    }
}
