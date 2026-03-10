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

package org.javarosa.core.model.condition

import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.services.Logger
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

class Constraint : Externalizable {
    @JvmField
    var constraint: IConditionExpr? = null
    private var constraintMsg: String? = null
    private var xPathConstraintMsg: XPathExpression? = null

    constructor()

    constructor(constraint: IConditionExpr?, constraintMsg: String?) {
        this.constraint = constraint
        this.constraintMsg = constraintMsg
        attemptConstraintCompile()
    }

    fun getConstraintMessage(ec: EvaluationContext, instance: FormInstance?, textForm: String?): String? {
        val xPathMsg = xPathConstraintMsg
        if (xPathMsg == null) {
            // If the request is for getting a constraint message in a specific format (like audio) from
            // itext, and there's no xpath, we couldn't possibly fulfill it
            return if (textForm == null) constraintMsg else null
        } else {
            if (textForm != null) {
                ec.setOutputTextForm(textForm)
            }
            return try {
                val value = xPathMsg.eval(instance, ec)
                if (value != "") {
                    value as String
                } else {
                    null
                }
            } catch (e: Exception) {
                Logger.exception("Error evaluating a valid-looking constraint xpath ", e)
                constraintMsg
            }
        }
    }

    private fun attemptConstraintCompile() {
        xPathConstraintMsg = null
        try {
            if (constraintMsg != null) {
                xPathConstraintMsg = XPathParseTool.parseXPath("string($constraintMsg)")
            }
        } catch (e: Exception) {
            // Expected in probably most cases.
        }
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(input: PlatformDataInputStream, pf: PrototypeFactory) {
        constraint = ExtUtil.read(input, ExtWrapTagged(), pf) as IConditionExpr
        constraintMsg = ExtUtil.nullIfEmpty(ExtUtil.readString(input))
        attemptConstraintCompile()
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.write(out, ExtWrapTagged(constraint!!))
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(constraintMsg))
    }
}
