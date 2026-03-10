/*
 * Copyright (C) 2009 JavaRosa
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.javarosa.xform.util

import org.javarosa.core.data.IDataPointer
import org.javarosa.core.model.IAnswerDataSerializer
import org.javarosa.core.model.data.BooleanData
import org.javarosa.core.model.data.DateData
import org.javarosa.core.model.data.DateTimeData
import org.javarosa.core.model.data.DecimalData
import org.javarosa.core.model.data.GeoPointData
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.data.LongData
import org.javarosa.core.model.data.PointerAnswerData
import org.javarosa.core.model.data.SelectMultiData
import org.javarosa.core.model.data.SelectOneData
import org.javarosa.core.model.data.StringData
import org.javarosa.core.model.data.TimeData
import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.data.helper.Selection
import org.javarosa.core.model.utils.DateUtils

import java.util.Date

/**
 * The XFormAnswerDataSerializer takes in AnswerData objects, and provides
 * an XForms compliant (String or Element) representation of that AnswerData.
 *
 * By default, this serializer can properly operate on StringData, DateData
 * SelectMultiData, and SelectOneData AnswerData objects. This list can be
 * extended by registering appropriate XForm serializing AnswerDataSerializers
 * with this class.
 *
 * @author Clayton Sims
 */
class XFormAnswerDataSerializer : IAnswerDataSerializer {

    val additionalSerializers = ArrayList<IAnswerDataSerializer>()

    override fun canSerialize(data: IAnswerData?): Boolean {
        return data is StringData || data is DateData || data is TimeData ||
                data is SelectMultiData || data is SelectOneData ||
                data is IntegerData || data is DecimalData || data is PointerAnswerData ||
                data is GeoPointData || data is LongData || data is DateTimeData || data is UncastData
    }

    /**
     * @param data The AnswerDataObject to be serialized
     * @return A String which contains the given answer
     */
    fun serializeAnswerData(data: UncastData): Any? {
        return data.getString()
    }

    /**
     * @param data The AnswerDataObject to be serialized
     * @return A String which contains the given answer
     */
    fun serializeAnswerData(data: StringData): Any? {
        return data.getValue()
    }

    /**
     * @param data The AnswerDataObject to be serialized
     * @return A String which contains a date in xsd:date
     * formatting
     */
    fun serializeAnswerData(data: DateData): Any {
        return DateUtils.formatDate(data.getValue() as Date, DateUtils.FORMAT_ISO8601)
    }

    /**
     * @param data The AnswerDataObject to be serialized
     * @return A String which contains a date in xsd:date
     * formatting
     */
    fun serializeAnswerData(data: DateTimeData): Any {
        return DateUtils.formatDateTime(data.getValue() as Date, DateUtils.FORMAT_ISO8601)
    }

    /**
     * @param data The AnswerDataObject to be serialized
     * @return A String which contains a date in xsd:time
     * formatting
     */
    fun serializeAnswerData(data: TimeData): Any {
        return DateUtils.formatTime(data.getValue() as Date, DateUtils.FORMAT_ISO8601_WALL_TIME)
    }

    /**
     * @param data The AnswerDataObject to be serialized
     * @return A String which contains a reference to the
     * data
     */
    fun serializeAnswerData(data: PointerAnswerData): Any {
        //Note: In order to override this default behavior, a
        //new serializer should be used, and then registered
        //with this serializer
        val pointer = data.getValue() as IDataPointer
        return pointer.displayText
    }

    /**
     * @param data The AnswerDataObject to be serialized
     * @return A string containing the xforms compliant format
     * for a <select> tag, a string containing a list of answers
     * separated by space characters.
     */
    fun serializeAnswerData(data: SelectMultiData): Any {
        @Suppress("UNCHECKED_CAST")
        val selections = data.getValue() as ArrayList<Selection>
        val en = selections.iterator()
        val selectString = StringBuffer()

        while (en.hasNext()) {
            val selection = en.next()
            if (selectString.isNotEmpty())
                selectString.append(DELIMITER)
            selectString.append(selection.getValue())
        }
        //As Crazy, and stupid, as it sounds, this is the XForms specification
        //for storing multiple selections.
        return selectString.toString()
    }

    /**
     * @param data The AnswerDataObject to be serialized
     * @return A String which contains the value of a selection
     */
    fun serializeAnswerData(data: SelectOneData): Any {
        return (data.getValue() as Selection).getValue()
    }

    fun serializeAnswerData(data: IntegerData): Any {
        return data.getValue().toString()
    }

    fun serializeAnswerData(data: LongData): Any {
        return data.getValue().toString()
    }

    fun serializeAnswerData(data: DecimalData): Any {
        return data.getValue().toString()
    }

    fun serializeAnswerData(data: GeoPointData): Any {
        return data.getDisplayText()
    }

    fun serializeAnswerData(data: BooleanData): Any {
        return if (data.getValue() as Boolean) {
            "1"
        } else {
            "0"
        }
    }

    override fun serializeAnswerData(data: IAnswerData?, dataType: Int): Any? {
        // First, we want to go through the additional serializers, as they should
        // take priority to the default serializations
        val en = additionalSerializers.iterator()
        while (en.hasNext()) {
            val serializer = en.next()
            if (serializer.canSerialize(data)) {
                return serializer.serializeAnswerData(data, dataType)
            }
        }
        //Defaults
        return serializeAnswerData(data)
    }

    override fun serializeAnswerData(data: IAnswerData?): Any? {
        if (data is StringData) {
            return serializeAnswerData(data)
        } else if (data is SelectMultiData) {
            return serializeAnswerData(data)
        } else if (data is SelectOneData) {
            return serializeAnswerData(data)
        } else if (data is IntegerData) {
            return serializeAnswerData(data)
        } else if (data is LongData) {
            return serializeAnswerData(data)
        } else if (data is DecimalData) {
            return serializeAnswerData(data)
        } else if (data is DateData) {
            return serializeAnswerData(data)
        } else if (data is TimeData) {
            return serializeAnswerData(data)
        } else if (data is PointerAnswerData) {
            return serializeAnswerData(data)
        } else if (data is GeoPointData) {
            return serializeAnswerData(data)
        } else if (data is DateTimeData) {
            return serializeAnswerData(data)
        } else if (data is BooleanData) {
            return serializeAnswerData(data)
        } else if (data is UncastData) {
            return serializeAnswerData(data)
        }

        return null
    }

    override fun containsExternalData(data: IAnswerData?): Boolean? {
        //First check for registered serializers to identify whether
        //they override this one.
        val en = additionalSerializers.iterator()
        while (en.hasNext()) {
            val serializer = en.next()
            val contains = serializer.containsExternalData(data)
            if (contains != null) {
                return contains
            }
        }
        if (data is PointerAnswerData) {
            return java.lang.Boolean.TRUE
        }
        return java.lang.Boolean.FALSE
    }

    override fun retrieveExternalDataPointer(data: IAnswerData?): Array<IDataPointer>? {
        val en = additionalSerializers.iterator()
        while (en.hasNext()) {
            val serializer = en.next()
            val contains = serializer.containsExternalData(data)
            if (contains != null) {
                return serializer.retrieveExternalDataPointer(data)
            }
        }
        if (data is PointerAnswerData) {
            val pointer = arrayOf(data.getValue() as IDataPointer)
            return pointer
        }
        //This shouldn't have been called.
        return null
    }

    companion object {
        const val DELIMITER: String = " "
    }
}
