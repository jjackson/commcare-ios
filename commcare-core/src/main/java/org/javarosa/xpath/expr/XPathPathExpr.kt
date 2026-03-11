package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.pivot.UnpivotableExpressionException
import org.javarosa.core.model.data.BooleanData
import org.javarosa.core.model.data.DateData
import org.javarosa.core.model.data.DateTimeData
import org.javarosa.core.model.data.DecimalData
import org.javarosa.core.model.data.GeoPointData
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.data.LongData
import org.javarosa.core.model.data.SelectMultiData
import org.javarosa.core.model.data.SelectOneData
import org.javarosa.core.model.data.StringData
import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.data.helper.Selection
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xform.util.XFormAnswerDataSerializer
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.XPathLazyNodeset
import org.javarosa.xpath.XPathMissingInstanceException
import org.javarosa.xpath.XPathNodeset
import org.javarosa.xpath.XPathTypeMismatchException
import org.javarosa.xpath.XPathUnsupportedException
import org.javarosa.xpath.analysis.AnalysisInvalidException
import org.javarosa.xpath.analysis.XPathAnalyzer

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

class XPathPathExpr : XPathExpression {
    private var templatePathChecked = false

    @JvmField
    var initContext: Int = 0
    @JvmField
    var steps: Array<XPathStep> = emptyArray()
    private var cachedReference: TreeReference? = null

    //for INIT_CONTEXT_EXPR only
    @JvmField
    var filtExpr: XPathFilterExpr? = null

    constructor() // for deserialization

    constructor(initContext: Int, steps: Array<XPathStep>) {
        this.initContext = initContext
        this.steps = steps
    }

    constructor(filtExpr: XPathFilterExpr, steps: Array<XPathStep>) : this(INIT_CONTEXT_EXPR, steps) {
        this.filtExpr = filtExpr
    }

    /**
     * Translate an xpath path reference into a TreeReference
     * TreeReferences only support a subset of xpath paths:
     * - only simple child name tests 'child::name', '.', and '..' allowed.
     * - '../' steps must come before anything else
     *
     * @return a reference built from this path expression
     */
    @Throws(XPathUnsupportedException::class)
    fun getReference(): TreeReference {
        if (cachedReference != null) {
            return cachedReference!!
        }
        var ref = TreeReference()
        var parentsAllowed: Boolean
        // process the beginning of the reference
        when (initContext) {
            INIT_CONTEXT_ROOT -> {
                ref.setRefLevel(TreeReference.REF_ABSOLUTE)
                parentsAllowed = false
            }
            INIT_CONTEXT_RELATIVE -> {
                ref.setRefLevel(0)
                ref.setContextType(TreeReference.CONTEXT_INHERITED)
                parentsAllowed = true
            }
            INIT_CONTEXT_EXPR -> {
                if (filtExpr!!.x != null && filtExpr!!.x is XPathFuncExpr) {
                    val func = filtExpr!!.x as XPathFuncExpr
                    if (func.name == "instance") {
                        // i assume when refering the non main instance you have to be absolute
                        parentsAllowed = false
                        if (func.args.size != 1) {
                            throw XPathUnsupportedException("instance() function used with " +
                                    func.args.size + " arguements. Expecting 1 arguement")
                        }
                        if (func.args[0] !is XPathStringLiteral) {
                            throw XPathUnsupportedException("instance() function expecting 1 string literal arguement arguement")
                        }
                        val strLit = func.args[0] as XPathStringLiteral
                        // we've got a non-standard instance in play, watch out
                        ref = TreeReference(strLit.s, TreeReference.REF_ABSOLUTE)
                    } else if (func.name == "current") {
                        parentsAllowed = true
                        ref = TreeReference.baseCurrentRef()
                    } else {
                        // We only support expression root contexts for
                        // instance refs, everything else is an illegal filter
                        throw XPathUnsupportedException("filter expression")
                    }
                } else {
                    // We only support expression root contexts for instance
                    // refs, everything else is an illegal filter
                    throw XPathUnsupportedException("filter expression")
                }
            }
            else -> throw XPathUnsupportedException("filter expression")
        }

        val otherStepMessage = "step other than 'child::name', '.', '..'"
        for (step in steps) {
            if (step.axis == XPathStep.AXIS_SELF) {
                if (step.test != XPathStep.TEST_TYPE_NODE) {
                    throw XPathUnsupportedException(otherStepMessage)
                }
            } else if (step.axis == XPathStep.AXIS_PARENT) {
                if (!parentsAllowed || step.test != XPathStep.TEST_TYPE_NODE) {
                    throw XPathUnsupportedException(otherStepMessage)
                } else {
                    ref.incrementRefLevel()
                }
            } else if (step.axis == XPathStep.AXIS_ATTRIBUTE) {
                if (step.test == XPathStep.TEST_NAME) {
                    ref.add(step.name.toString(), TreeReference.INDEX_ATTRIBUTE)
                    parentsAllowed = false
                    //TODO: Can you step back from an attribute, or should this always be
                    //the last step?
                } else {
                    throw XPathUnsupportedException("attribute step other than 'attribute::name")
                }
            } else if (step.axis == XPathStep.AXIS_CHILD) {
                if (step.test == XPathStep.TEST_NAME) {
                    ref.add(step.name.toString(), TreeReference.INDEX_UNBOUND)
                    parentsAllowed = false
                } else if (step.test == XPathStep.TEST_NAME_WILDCARD) {
                    ref.add(TreeReference.NAME_WILDCARD, TreeReference.INDEX_UNBOUND)
                    parentsAllowed = false
                } else {
                    throw XPathUnsupportedException(otherStepMessage)
                }
            } else {
                throw XPathUnsupportedException(otherStepMessage)
            }

            if (step.predicates.isNotEmpty()) {
                val v = ArrayList<XPathExpression>()
                for (predicate in step.predicates) {
                    v.add(predicate)
                }
                // add the predicate vector to the last step in the ref
                ref.addPredicate(ref.size() - 1, v)
            }
        }
        cachedReference = ref
        return ref
    }

    override fun evalRaw(model: DataInstance<*>?, evalContext: EvaluationContext): Any {
        val genericRef = getReference()
        val ref: TreeReference

        if (genericRef.getContextType() == TreeReference.CONTEXT_ORIGINAL) {
            // reference begins with "current()" so contextualize in the original context
            ref = genericRef.contextualize(evalContext.getOriginalContext()!!)!!
        } else {
            ref = genericRef.contextualize(evalContext.contextRef!!)!!
        }

        //We don't necessarily know the model we want to be working with until we've contextualized the
        //node

        //check if this nodeset refers to a non-main instance
        var m = model
        if (ref.getInstanceName() != null && ref.isAbsolute) {
            val nonMain = evalContext.getInstance(ref.getInstanceName())
            if (nonMain != null) {
                m = nonMain
                if (m!!.getRoot() == null) {
                    //This instance is _declared_, but doesn't actually have any data in it.
                    throw XPathMissingInstanceException(ref.getInstanceName(), "Instance referenced by ${ref.toString(true)} has not been loaded")
                }
            } else {
                throw XPathMissingInstanceException(ref.getInstanceName(),
                    "The instance \"${ref.getInstanceName()}\" in expression \"${ref.toString(true)}\" used by \"${evalContext.contextRef}\" does not exist in the form. Please correct your form or application.")
            }
        } else {
            //TODO: We should really stop passing 'm' around and start just getting the right instance from ec
            //at a more central level
            m = evalContext.getMainInstance()

            if (m == null) {
                val refStr = ref.toString(true)
                throw XPathException("Cannot evaluate the reference [$refStr] in the current evaluation context. No default instance has been declared!")
            }
        }
        //Otherwise we'll leave 'm' as set to the main instance

        // Error out if a (template) path along the reference starting at the
        // main DataInstance doesn't exist.
        if (!templatePathChecked && ref.isAbsolute && !m!!.hasTemplatePath(ref)) {
            return XPathNodeset.constructInvalidPathNodeset(ref.toString(), genericRef.toString())
        }

        // only check the template path once, since it is expensive
        templatePathChecked = true

        return XPathLazyNodeset(ref, m, evalContext)
    }

    override fun toString(): String {
        val sb = StringBuffer()

        sb.append("{path-expr:")
        when (initContext) {
            INIT_CONTEXT_ROOT -> sb.append("abs")
            INIT_CONTEXT_RELATIVE -> sb.append("rel")
            INIT_CONTEXT_EXPR -> sb.append(filtExpr.toString())
        }
        sb.append(",{")
        for (i in steps.indices) {
            sb.append(steps[i].toString())
            if (i < steps.size - 1)
                sb.append(",")
        }
        sb.append("}}")

        return sb.toString()
    }

    override fun equals(o: Any?): Boolean {
        if (o is XPathPathExpr) {
            //Shortcuts for easily comparable values
            if (initContext != o.initContext || steps.size != o.steps.size) {
                return false
            }

            @Suppress("UNCHECKED_CAST")
            return ExtUtil.arrayEquals(steps as Array<Any?>, o.steps as Array<Any?>, false) && (initContext != INIT_CONTEXT_EXPR || filtExpr == o.filtExpr)
        } else {
            return false
        }
    }

    override fun hashCode(): Int {
        var stepsHash = 0
        for (step in steps) {
            stepsHash = stepsHash xor step.hashCode()
        }

        return if (initContext == INIT_CONTEXT_EXPR) {
            initContext xor stepsHash xor filtExpr.hashCode()
        } else {
            initContext xor stepsHash
        }
    }

    /**
     * Warning: this method has somewhat unclear semantics.
     *
     * "matches" follows roughly the same process as equals(), in that it goes
     * through the path step by step and compares whether each step can refer to the same node.
     * The only difference is that match() will allow for a named step to match a step who's name
     * is a wildcard.
     *
     * So
     * /data/path/to
     * will "match"
     * /data/`*`/to
     *
     * even though they are not equal.
     *
     * Matching is reflexive, consistent, and symmetric, but _not_ transitive.
     *
     * @return true if the expression is a path that matches this one
     */
    fun matches(o: XPathExpression): Boolean {
        if (o is XPathPathExpr) {
            //Shortcuts for easily comparable values
            if (initContext != o.initContext || steps.size != o.steps.size) {
                return false
            }

            for (i in steps.indices) {
                if (!steps[i].matches(o.steps[i])) {
                    return false
                }
            }

            // If all steps match, we still need to make sure we're in the same "context" if this
            // is a normal expression.
            return (initContext != INIT_CONTEXT_EXPR || filtExpr == o.filtExpr)
        } else {
            return false
        }
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        initContext = ExtUtil.readInt(`in`)
        if (initContext == INIT_CONTEXT_EXPR) {
            filtExpr = ExtUtil.read(`in`, XPathFilterExpr::class.java, pf) as XPathFilterExpr
        }

        val v = ExtUtil.read(`in`, ExtWrapList(XPathStep::class.java), pf) as ArrayList<*>
        steps = Array(v.size) { i -> (v[i] as XPathStep).intern() }
        cacheState = ExtUtil.read(`in`, CacheableExprState::class.java, pf) as CacheableExprState
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.writeNumeric(out, initContext.toLong())
        if (initContext == INIT_CONTEXT_EXPR) {
            ExtUtil.write(out, filtExpr!!)
        }

        val v = ArrayList<XPathStep>()
        for (step in steps) {
            v.add(step)
        }
        ExtUtil.write(out, ExtWrapList(v))
        ExtUtil.write(out, cacheState)
    }

    @Throws(UnpivotableExpressionException::class)
    override fun pivot(model: DataInstance<*>?, evalContext: EvaluationContext,
                       pivots: ArrayList<Any>, sentinal: Any?): Any? {
        val ref = this.getReference()
        //Either concretely the sentinal, or "."
        if (ref == sentinal || (ref.getRefLevel() == 0)) {
            return sentinal
        } else {
            //It's very, very hard to figure out how to pivot predicates. For now, just skip it
            for (i in 0 until ref.size()) {
                val pred = ref.getPredicate(i)
                if (pred != null && pred.size > 0) {
                    throw UnpivotableExpressionException("Can't pivot filtered treereferences. Ref: ${ref.toString(true)} has predicates.")
                }
            }
            return this.eval(model, evalContext)
        }
    }

    override fun toPrettyString(): String {
        try {
            return getReference().toString(true)
        } catch (e: Exception) {
            return toDebugString()
        }
    }

    fun toDebugString(): String {
        try {
            val sb = StringBuffer()

            when (initContext) {
                INIT_CONTEXT_ROOT -> sb.append("/")
                INIT_CONTEXT_RELATIVE -> sb.append("./")
                INIT_CONTEXT_EXPR -> {
                    try {
                        sb.append(filtExpr!!.x!!.toPrettyString())
                        sb.append("/")
                    } catch (e: Exception) {
                        sb.append(filtExpr.toString())
                    }
                }
            }
            for (i in steps.indices) {
                val step = steps[i]
                sb.append(step.toPrettyString())
                sb.append("/")
            }

            var output = sb.toString()
            if (output.endsWith("/")) {
                output = output.substring(0, output.length - 1)
            }

            return output
        } catch (e: Exception) {
            return toString()
        }
    }

    @Throws(AnalysisInvalidException::class)
    override fun applyAndPropagateAnalyzer(analyzer: XPathAnalyzer) {
        if (analyzer.shortCircuit()) {
            return
        }
        analyzer.doAnalysis(this)
        getReference().applyAndPropagateAnalyzer(analyzer)
    }

    companion object {
        const val INIT_CONTEXT_ROOT = 0
        const val INIT_CONTEXT_RELATIVE = 1
        const val INIT_CONTEXT_EXPR = 2

        @JvmStatic
        fun getRefValue(model: DataInstance<*>, ec: EvaluationContext, ref: TreeReference): Any {
            if (ec.isConstraint && ref == ec.contextRef) {
                //ITEMSET TODO: need to update this; for itemset/copy constraints, need to simulate a whole xml sub-tree here
                return unpackValue(ec.candidateValue)
            } else {
                val node = model.resolveReference(ref, ec)
                    ?: //shouldn't happen -- only existent nodes should be in nodeset
                    throw XPathTypeMismatchException("Node ${ref} does not exist!")

                return unpackValue(if (node.isRelevant) node.getValue() else null)
            }
        }

        @JvmStatic
        fun unpackValue(`val`: IAnswerData?): Any {
            if (`val` == null) {
                return ""
            } else if (`val` is UncastData) {
                return `val`.getValue()!!
            } else if (`val` is IntegerData) {
                return (`val`.getValue() as Int).toDouble()
            } else if (`val` is LongData) {
                return (`val`.getValue() as Long).toDouble()
            } else if (`val` is DecimalData) {
                return `val`.getValue()!!
            } else if (`val` is StringData) {
                return `val`.getValue()!!
            } else if (`val` is SelectOneData) {
                return (`val`.getValue() as Selection).getValue()
            } else if (`val` is SelectMultiData) {
                return XFormAnswerDataSerializer().serializeAnswerData(`val`)
            } else if (`val` is DateData) {
                return `val`.getValue()!!
            } else if (`val` is DateTimeData) {
                return `val`.getValue()!!
            } else if (`val` is BooleanData) {
                return `val`.getValue()!!
            } else if (`val` is GeoPointData) {
                return `val`.uncast().getString()!!
            } else {
                System.out.println("warning: unrecognized data type in xpath expr: ${`val`.javaClass.name}")

                //TODO: Does this mess up any of our other plans?
                return `val`.uncast().getString()!!
            }
        }

        @JvmStatic
        fun fromRef(ref: TreeReference): XPathPathExpr {
            val path = XPathPathExpr()
            path.initContext = if (ref.isAbsolute) INIT_CONTEXT_ROOT else INIT_CONTEXT_RELATIVE
            path.steps = Array(ref.size()) { i ->
                if (ref.getName(i) == TreeReference.NAME_WILDCARD) {
                    XPathStep(XPathStep.AXIS_CHILD, XPathStep.TEST_NAME_WILDCARD).intern()
                } else {
                    XPathStep(XPathStep.AXIS_CHILD, XPathQName(ref.getName(i))).intern()
                }
            }
            return path
        }
    }
}
