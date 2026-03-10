package org.javarosa.core.model.actions

import org.javarosa.core.util.ListMultimap
import org.javarosa.core.model.FormDef
import org.javarosa.core.model.IFormElement
import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.services.Logger
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xform.parse.IElementHandler
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream

/**
 * A Send Action is responsible for loading a submission template from the form, and performing
 * a callout action in the current platform to retrieve a value synchronously from a web
 * service.
 *
 * @author ctsims
 */
class SendAction : Action {

    private var submissionId: String? = null

    constructor() : super() {
        // for externalization
    }

    constructor(submissionId: String?) : super(ELEMENT_NAME) {
        this.submissionId = submissionId
    }

    override fun processAction(model: FormDef, contextRef: TreeReference?): TreeReference? {
        val profile = model.getSubmissionProfile(submissionId!!) ?: return null
        val url = profile.resource

        val ref = profile.ref
        var map: ListMultimap<String, String>? = null
        if (ref != null) {
            map = getKeyValueMapping(model, ref)
        }

        var result: String? = null
        try {
            result = model.dispatchSendCallout(url!!, map)
        } catch (e: Exception) {
            Logger.exception("send-action", e)
        }
        return if (result == null) {
            null
        } else {
            val target = profile.targetRef
            model.setValue(UncastData(result), target!!)
            target
        }
    }

    private fun getKeyValueMapping(model: FormDef, ref: TreeReference): ListMultimap<String, String> {
        val map: ListMultimap<String, String> = ListMultimap()
        val element = model.getEvaluationContext()!!.resolveReference(ref)
        for (i in 0 until element!!.getNumChildren()) {
            val child = element.getChildAt(i)

            val name = child!!.getName()
            val value = child.getValue()

            if (value != null) {
                map.put(name!!, value.uncast().getString()!!)
            }
        }
        return map
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        submissionId = ExtUtil.readString(`in`)
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(submissionId))
    }

    companion object {
        const val ELEMENT_NAME: String = "send"

        @JvmStatic
        fun getHandler(): IElementHandler {
            return IElementHandler { p, e, parent ->
                // the generic parseAction() method in XFormParser already checks to make sure
                // that parent is an IFormElement, and throws an exception if it is not
                p.parseSendAction((parent as IFormElement).getActionController()!!, e)
            }
        }
    }
}
