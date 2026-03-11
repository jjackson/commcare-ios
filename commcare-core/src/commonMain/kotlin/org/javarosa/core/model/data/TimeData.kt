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

package org.javarosa.core.model.data

import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.model.utils.PlatformDate

class TimeData : IAnswerData {
    var d: PlatformDate? = null

    /**
     * Empty Constructor, necessary for dynamic construction during deserialization.
     * Shouldn't be used otherwise.
     */
    constructor()

    constructor(d: PlatformDate) {
        setValue(d)
    }

    override fun clone(): IAnswerData {
        return TimeData(PlatformDate(d!!.getTime()))
    }

    override fun setValue(o: Any?) {
        if (o == null) {
            throw NullPointerException("Attempt to set an IAnswerData class to null.")
        }
        d = PlatformDate((o as PlatformDate).getTime())
    }

    override fun getValue(): Any {
        return PlatformDate(d!!.getTime())
    }

    override fun getDisplayText(): String {
        return DateUtils.formatTime(d, DateUtils.FORMAT_HUMAN_READABLE_SHORT)
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        setValue(ExtUtil.readDate(`in`))
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeDate(out, d!!)
    }

    override fun uncast(): UncastData {
        return UncastData(DateUtils.formatTime(d, DateUtils.FORMAT_ISO8601_WALL_TIME))
    }

    override fun cast(data: UncastData): TimeData {
        val ret = DateUtils.parseTime(data.value!!, true)
        if (ret != null) {
            return TimeData(ret)
        }

        throw IllegalArgumentException("Invalid cast of data [" + data.value + "] to type Time")
    }
}
