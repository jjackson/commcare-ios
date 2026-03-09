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

import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * A response to a question requesting a String Value
 *
 * @author Drew Roos
 */
class StringData : IAnswerData {
    private var s: String? = null

    /**
     * Empty Constructor, necessary for dynamic construction during deserialization.
     * Shouldn't be used otherwise.
     */
    constructor()

    constructor(s: String) {
        setValue(s)
    }

    override fun clone(): IAnswerData {
        return StringData(s!!)
    }

    // string should not be null or empty; the entire StringData reference should be null in this case
    override fun setValue(o: Any?) {
        if (o == null) {
            throw NullPointerException("Attempt to set an IAnswerData class to null.")
        }
        s = o as String
    }

    override fun getValue(): Any? {
        return s
    }

    override fun getDisplayText(): String? {
        return s
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        s = ExtUtil.readString(`in`)
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeString(out, s!!)
    }

    override fun uncast(): UncastData {
        return UncastData(s)
    }

    override fun cast(data: UncastData): StringData {
        return StringData(data.value!!)
    }
}
