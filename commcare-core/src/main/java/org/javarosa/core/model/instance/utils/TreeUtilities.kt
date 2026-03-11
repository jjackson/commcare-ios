package org.javarosa.core.model.instance.utils

import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.CacheTable
import org.javarosa.core.util.DataUtil
import org.javarosa.model.xform.XPathReference
import org.javarosa.xml.ElementParser
import org.javarosa.xml.TreeElementParser
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.expr.XPathEqExpr
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.expr.XPathPathExpr
import org.javarosa.xpath.expr.XPathStringLiteral
import org.javarosa.xml.PlatformXmlParser
import org.javarosa.xml.PlatformXmlParserException
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.io.PlatformInputStream
import kotlin.jvm.JvmStatic

/**
 * Helper methods for procedures which are common to different Tree model
 * implementations and don't fit well into the inheritance model
 *
 * @author ctsims
 */
object TreeUtilities {

    // Static XPathPathExpr cache. Not 100% clear whether this is the best caching strategy,
    // but it's the easiest.
    @JvmStatic
    val table: CacheTable<String, XPathPathExpr> = CacheTable()

    /**
     * A general purpose method for taking an abstract tree element and
     * attempting to batch fetch its children's predicates through static
     * evaluation.
     *
     * @param parent                The element whose children are being requested
     * @param childAttributeHintMap A mapping of paths which can be evaluated in memory.
     * @param name                  The name of the children being queried
     * @param mult                  The multiplicity being queried for (could be undefined)
     * @param predicates            The evaluation step predicates which are
     *                              being processed. NOTE: This vector will be modified by this method as a
     *                              side effect if a predicate was successfully statically evaluated
     * @param evalContext           The current eval context.
     * @return A vector of TreeReferences which contains the nodes matched by predicate expressions.
     * Expressions which result in returned matches will be removed from the predicate collection which
     * is provided
     */
    @JvmStatic
    fun tryBatchChildFetch(
        parent: AbstractTreeElement,
        childAttributeHintMap: HashMap<XPathPathExpr, HashMap<String, Array<TreeElement>>>?,
        name: String?,
        mult: Int,
        predicates: ArrayList<XPathExpression>?,
        evalContext: EvaluationContext?
    ): Collection<TreeReference>? {
        // This method builds a predictive model for quick queries that
        // prevents the need to fully flesh out full walks of the tree.

        // TODO: We build a bunch of models here, it's not clear whether we
        // should be retaining them for multiple queries in the future rather
        // than letting it rebuild the same caches a couple of times

        // We also need to figure out exactly how to determine whether this
        // "worked" more or less and potentially preventing this attempt from
        // proceeding in the future, since it's not exactly free...

        // Only do for predicates
        if (mult != TreeReference.INDEX_UNBOUND || predicates == null || name == null) {
            return null
        }

        val toRemove = ArrayList<Int>()
        var allSelectedChildren: Collection<TreeReference>? = null

        // Lazy init these until we've determined that our predicate is hintable

        // These two are basically a map, but we don't have a great datatype for this
        var attributes: ArrayList<String>? = null
        var indices: ArrayList<XPathPathExpr>? = null

        var kids: ArrayList<TreeElement>? = null

        var i = 0
        while (i < predicates.size) {
            val predicateMatches = LinkedHashSet<TreeReference>()
            val xpe = predicates[i]
            // what we want here is a static evaluation of the expression to see if it consists of evaluating
            // something we index with something static.
            if (xpe is XPathEqExpr) {
                val left = xpe.a
                val right = xpe.b
                val isEqOp = xpe.op == XPathEqExpr.EQ

                // For now, only cheat when this is a string literal (this basically just means that we're
                // handling attribute based referencing with very reasonable timing, but it's complex otherwise)
                if (left is XPathPathExpr && (right is XPathStringLiteral || right is XPathPathExpr)) {
                    var literalMatch: String? = null
                    if (right is XPathStringLiteral) {
                        literalMatch = right.s
                    } else if (right is XPathPathExpr) {
                        // We'll also try to match direct path queries as long as they are not
                        // complex.

                        // First: Evaluate whether there are predicates (which may have nesting that ruins our
                        // ability to do this)
                        var hasPredicates = false
                        for (step in right.steps) {
                            if (step.predicates.isNotEmpty()) {
                                // We can't evaluate this, just bail
                                hasPredicates = true
                                break
                            }
                        }

                        if (!hasPredicates) {
                            try {
                                // Otherwise, go pull out the right hand value
                                val o = FunctionUtils.unpack(right.eval(evalContext!!))
                                literalMatch = FunctionUtils.toString(o)
                            } catch (e: XPathException) {
                                // We may have some weird lack of context that makes this not work, so don't choke on
                                // the bonus evaluation and just evaluate the traditional way
                                e.printStackTrace()
                                break
                            }
                        }
                    }

                    // First, see if we can run this query with a hint map, rather than jumping out to storage
                    // since that may involve iterative I/O queries.
                    if (childAttributeHintMap != null) {
                        if (childAttributeHintMap.containsKey(left)) {
                            // Retrieve the list of children which match our literal
                            val children = childAttributeHintMap[left]?.get(literalMatch)
                            if (children != null) {
                                for (element in children) {
                                    predicateMatches.add(element.getRef())
                                }
                            }
                            // Merge and note that this predicate is evaluated and doesn't need to be evaluated
                            // in the future.
                            allSelectedChildren = merge(allSelectedChildren, predicateMatches, i, toRemove)
                            i++
                            continue
                        }
                    }

                    // We're lazily initializing this, since it might actually take a while, and we
                    // don't want the overhead if our predicate is too complex anyway

                    // TODO: Probably makes sense to actually just build the hint mapping here,
                    // but we currently don't robustly track changes to the models, so would
                    // be too dangerous at the moment
                    if (attributes == null) {
                        attributes = ArrayList()
                        indices = ArrayList()
                        @Suppress("UNCHECKED_CAST")
                        kids = parent.getChildrenWithName(name) as ArrayList<TreeElement>

                        if (kids.size == 0) {
                            return null
                        }

                        // Anything that we're going to use across elements should be on all of them
                        val kid = kids[0]
                        for (j in 0 until kid.getAttributeCount()) {
                            val attribute = kid.getAttributeName(j) ?: continue
                            val path = getXPathAttrExpression(attribute)
                            attributes.add(attribute)
                            indices.add(path)
                        }
                    }

                    for (j in 0 until indices!!.size) {
                        val expr = indices[j]
                        if (expr == left) {
                            val attributeName = attributes[j]

                            for (kidI in 0 until kids!!.size) {
                                var attrValue = kids[kidI].getAttributeValue(null, attributeName)

                                if (attrValue == null) {
                                    attrValue = ""
                                }

                                // We don't necessarily have typing
                                // information for these attributes (and if we
                                // did it's not available here) so we will try
                                // to do some _very basic_ type inference on
                                // this value before performing the match
                                val value = FunctionUtils.InferType(attrValue)

                                if (isEqOp == XPathEqExpr.testEquality(value, literalMatch)) {
                                    predicateMatches.add(kids[kidI].getRef())
                                }
                            }

                            // Merge and note that this predicate is evaluated
                            // and doesn't need to be evaluated in the future.
                            allSelectedChildren = merge(allSelectedChildren, predicateMatches, i, toRemove)
                            i++
                            continue
                        }
                    }
                }
            }
            // There's only one case where we want to keep moving along, and we
            // would have triggered it if it were going to happen, so
            // otherwise, just get outta here.
            break
        }

        // if we weren't able to evaluate any predicates, signal that.
        if (allSelectedChildren == null) {
            return null
        }

        // otherwise, remove all of the predicates we've already evaluated
        for (i in toRemove.size - 1 downTo 0) {
            predicates.removeAt(toRemove[i])
        }

        return allSelectedChildren
    }

    private fun merge(
        allSelectedChildren: Collection<TreeReference>?,
        predicateMatches: Collection<TreeReference>,
        i: Int,
        toRemove: ArrayList<Int>
    ): Collection<TreeReference> {
        toRemove.add(DataUtil.integer(i))
        if (allSelectedChildren == null) {
            return predicateMatches
        }
        return DataUtil.intersection(allSelectedChildren, predicateMatches)
    }

    @JvmStatic
    fun getXPathAttrExpression(attribute: String): XPathPathExpr {
        // Cache tables can only take in integers due to some terrible 1.3 design issues
        // so we have to manually cache our attribute string's hash and follow from there.
        var cached = table.retrieve(attribute)

        if (cached == null) {
            cached = XPathReference.getPathExpr("@$attribute")
            table.register(attribute, cached)
        }
        return cached
    }

    @JvmStatic
    fun renameInstance(root: TreeElement, instanceId: String?): TreeElement {
        val copy = root.deepCopy(false)
        copy.accept(object : ITreeVisitor {
            override fun visit(tree: FormInstance) {
                throw RuntimeException("Not implemented")
            }

            override fun visit(element: AbstractTreeElement) {
                (element as TreeElement).setInstanceName(instanceId)
            }
        })
        return copy
    }

    /**
     * Converts xml in a given file to TreeElement
     *
     * @param xmlFilepath file path for the xml file
     * @return TreeElement for the given xml
     */
    @JvmStatic
    @Throws(InvalidStructureException::class, PlatformIOException::class)
    fun xmlToTreeElement(xmlFilepath: String?): TreeElement {
        var inputStream: PlatformInputStream? = null
        try {
            inputStream = InstanceUtils::class.java.getResourceAsStream(xmlFilepath)
            try {
                return xmlStreamToTreeElement(inputStream, "instance")
            } catch (e: UnfullfilledRequirementsException) {
                throw PlatformIOException(e.message)
            } catch (e: PlatformXmlParserException) {
                throw PlatformIOException(e.message)
            }
        } finally {
            StreamsUtil.closeStream(inputStream)
        }
    }

    /**
     * Converts a xml stream to TreeElement
     *
     * @param stream     Xml Stream
     * @param instanceId Instance Id for the TreeElement
     * @return TreeElement for the given xml stream
     */
    @JvmStatic
    @Throws(
        PlatformIOException::class,
        UnfullfilledRequirementsException::class,
        PlatformXmlParserException::class,
        InvalidStructureException::class
    )
    fun xmlStreamToTreeElement(stream: PlatformInputStream?, instanceId: String?): TreeElement {
        val baseParser = ElementParser.instantiateParser(stream!!)
        return TreeElementParser(baseParser, 0, instanceId).parse()
    }
}
