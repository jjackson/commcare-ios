package org.javarosa.core.model.util.restorable

import org.javarosa.core.model.Constants
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.IConditionExpr
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.services.storage.Persistable
import org.javarosa.model.xform.XPathReference
import org.javarosa.xpath.XPathConditional
import org.javarosa.xpath.expr.XPathPathExpr
import org.javarosa.core.model.utils.PlatformDate
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

object RestoreUtils {
    @JvmField
    val RECORD_ID_TAG: String = "rec-id"

    @JvmStatic
    fun ref(refStr: String): TreeReference {
        return DataInstance.unpackReference(XPathReference(refStr))
    }

    @JvmStatic
    fun refToPathExpr(ref: TreeReference): IConditionExpr {
        return XPathConditional(XPathPathExpr.fromRef(ref))
    }

    private fun topRef(dm: FormInstance): TreeReference {
        return ref("/" + dm.getRoot().getName())
    }

    private fun childRef(childPath: String, parentRef: TreeReference): TreeReference {
        return ref(childPath).parent(parentRef)!!
    }

    //used for incoming data
    private fun getDataType(c: KClass<*>): Int {
        return when (c) {
            String::class -> Constants.DATATYPE_TEXT
            Int::class -> Constants.DATATYPE_INTEGER
            Long::class -> Constants.DATATYPE_LONG
            Float::class, Double::class -> Constants.DATATYPE_DECIMAL
            PlatformDate::class -> Constants.DATATYPE_DATE
            Boolean::class -> Constants.DATATYPE_TEXT
            else -> throw RuntimeException("Can't handle data type ${c.qualifiedName}")
        }
    }

    @JvmStatic
    fun getValue(xpath: String, tree: FormInstance): Any? {
        val context = topRef(tree)
        val contextualizedRef = ref(xpath).contextualize(context)
            ?: throw RuntimeException("Could not contextualize reference [$xpath]")
        val node = tree.resolveReference(contextualizedRef)
            ?: throw RuntimeException("Could not find node [$xpath] when parsing saved instance!")

        return if (node.isRelevant) {
            val value = node.getValue()
            value?.getValue()
        } else {
            null
        }
    }

    @JvmStatic
    fun applyDataType(dm: FormInstance, path: String, parent: TreeReference, type: KClass<*>) {
        val dataType = getDataType(type)
        val ref = childRef(path, parent)

        val v: ArrayList<TreeReference> = EvaluationContext(dm).expandReference(ref) ?: return
        for (i in 0 until v.size) {
            val e = dm.resolveReference(v[i]) ?: continue
            e.setDataType(dataType)
        }
    }

    @JvmStatic
    fun templateData(r: Restorable, dm: FormInstance, parent: TreeReference?) {
        var resolvedParent = parent
        if (resolvedParent == null) {
            resolvedParent = topRef(dm)
            applyDataType(dm, "timestamp", resolvedParent, PlatformDate::class)
        }

        if (r is Persistable) {
            applyDataType(dm, RECORD_ID_TAG, resolvedParent, Int::class)
        }

        r.templateData(dm, resolvedParent)
    }
}
