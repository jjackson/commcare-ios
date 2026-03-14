package org.javarosa.core.model.instance.test

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.instance.utils.InstanceUtils
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.XPathPathExpr
import org.junit.Assert.*
import org.junit.Test

/**
 * DataInstance methods tests
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
class DataInstanceTest {

    companion object {
        private const val formPath = "/test_xpathpathexpr.xml"
    }

    @Test
    fun testDataInstance() {
        // load the xml doc into a form instance
        val model = try {
            InstanceUtils.loadFormInstance(formPath)
        } catch (e: Exception) {
            fail("Unable to load form at $formPath: ${e.message}")
            return
        }

        val evalCtx = EvaluationContext(model)

        // make sure a valid path can be found even when the xml sub-elements
        // aren't homogeneous in structure
        assertTrue("Homogeneous template path for a reference",
            model.hasTemplatePath(exprToRef("/data/places/country[1]/name", evalCtx)))

        assertTrue("Heterogeneous template path for a reference",
            model.hasTemplatePath(exprToRef("/data/places/country[1]/state[0]", evalCtx)))

        assertFalse("Unfound template path for a reference",
            model.hasTemplatePath(exprToRef("/data/places/fake[1]/name", evalCtx)))

        assertFalse("Unfound template path for a reference",
            model.hasTemplatePath(exprToRef("/data/places/country[1]/fake", evalCtx)))
    }

    /**
     * Evaluate an xpath query expression into a reference.
     *
     * @param expr     xpath expression
     * @param evalCtx contextual information needed to evaluate the expression
     */
    private fun exprToRef(expr: String, evalCtx: EvaluationContext): TreeReference {
        val xpe = try {
            XPathParseTool.parseXPath(expr) as XPathPathExpr
        } catch (e: Exception) {
            fail("Null expression or syntax error $expr")
            return TreeReference.rootRef() // unreachable
        }

        val genericRef = xpe.getReference()
        return if (genericRef.getContextType() == TreeReference.CONTEXT_ORIGINAL) {
            genericRef.contextualize(evalCtx.getOriginalContext()!!)!!
        } else {
            genericRef.contextualize(evalCtx.contextRef!!)!!
        }
    }
}
