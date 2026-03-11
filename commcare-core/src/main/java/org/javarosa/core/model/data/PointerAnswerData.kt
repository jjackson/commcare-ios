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

import org.javarosa.core.data.IDataPointer
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.PrototypeFactory

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Answer data representing a pointer object. The pointer is a reference to some
 * other object that it knows how to get out of memory.
 *
 * @author Cory Zue
 */
class PointerAnswerData : IAnswerData {

    private var data: IDataPointer? = null

    /**
     * NOTE: Only for serialization/deserialization
     */
    constructor()

    constructor(data: IDataPointer) {
        this.data = data
    }

    override fun clone(): IAnswerData? {
        return null // not cloneable
    }

    override fun getDisplayText(): String {
        return data!!.getDisplayText()
    }

    override fun getValue(): Any? {
        return data
    }

    override fun setValue(o: Any?) {
        if (o == null) {
            throw NullPointerException("Attempt to set an IAnswerData class to null.")
        }
        data = o as IDataPointer
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        data = ExtUtil.read(`in`, ExtWrapTagged(), pf) as IDataPointer
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.write(out, ExtWrapTagged(data!!))
    }

    override fun uncast(): UncastData {
        return UncastData(data!!.getDisplayText())
    }

    override fun cast(data: UncastData): PointerAnswerData? {
        return null
    }
}
