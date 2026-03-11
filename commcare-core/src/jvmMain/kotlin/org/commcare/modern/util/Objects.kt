/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commcare.modern.util

import java.lang.reflect.Modifier
import kotlin.jvm.JvmStatic

object Objects {

    /**
     * Returns true if two possibly-null objects are equal.
     */
    @JvmStatic
    fun equal(a: Any?, b: Any?): Boolean {
        return a === b || (a != null && a == b)
    }

    @JvmStatic
    fun hashCode(o: Any?): Int {
        return o?.hashCode() ?: 0
    }

    /**
     * Returns a string reporting the value of each declared field, via reflection.
     * Static and transient fields are automatically skipped. Produces output like
     * "SimpleClassName[integer=1234,string="hello",character='c',intArray=[1,2,3]]".
     */
    @JvmStatic
    fun toString(o: Any): String {
        val c = o.javaClass
        val sb = StringBuilder()
        sb.append(c.simpleName).append('[')
        var i = 0
        for (f in c.declaredFields) {
            if (f.modifiers and (Modifier.STATIC or Modifier.TRANSIENT) != 0) {
                continue
            }
            f.isAccessible = true
            try {
                val value = f.get(o)
                if (i++ > 0) {
                    sb.append(',')
                }
                sb.append(f.name)
                sb.append('=')
                when {
                    value.javaClass == Char::class.javaObjectType -> sb.append('\'').append(value).append('\'')
                    value.javaClass == String::class.java -> sb.append('"').append(value).append('"')
                    else -> sb.append(value)
                }
            } catch (unexpected: IllegalAccessException) {
                throw AssertionError(unexpected)
            }
        }
        sb.append("]")
        return sb.toString()
    }
}
