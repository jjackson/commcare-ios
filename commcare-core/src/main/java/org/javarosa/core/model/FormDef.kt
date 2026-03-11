package org.javarosa.core.model

import org.javarosa.core.util.ListMultimap
import org.commcare.modern.util.Pair
import org.javarosa.core.log.WrappedException
import org.javarosa.core.model.actions.Action
import org.javarosa.core.model.actions.ActionController
import org.javarosa.core.model.actions.FormSendCalloutHandler
import org.javarosa.core.model.condition.Condition
import org.javarosa.core.model.condition.Constraint
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.condition.IConditionExpr
import org.javarosa.core.model.condition.IFunctionHandler
import org.javarosa.core.model.condition.Recalculate
import org.javarosa.core.model.condition.Triggerable
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.data.InvalidData
import org.javarosa.core.model.data.SelectMultiData
import org.javarosa.core.model.data.SelectOneData
import org.javarosa.core.model.data.helper.Selection
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.InstanceInitializationFactory
import org.javarosa.core.model.instance.InvalidReferenceException
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.trace.EvaluationTrace
import org.javarosa.core.model.util.restorable.RestoreUtils
import org.javarosa.core.model.utils.ItemSetUtils
import org.javarosa.core.services.locale.Localizer
import org.javarosa.core.services.storage.IMetaData
import org.javarosa.core.util.LocalCacheTable
import org.javarosa.core.util.DataUtil
import org.javarosa.core.util.ShortestCycleAlgorithm
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.core.util.externalizable.emptyIfNull
import org.javarosa.core.util.externalizable.nullIfEmpty
import org.javarosa.model.xform.XPathReference
import org.javarosa.xpath.XPathTypeMismatchException
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.core.model.trace.PlatformTrace
import org.javarosa.core.model.trace.setActiveSpanTag
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Definition of a form. This has some meta data about the form definition and a
 * collection of groups together with question branching or skipping rules.
 *
 * @author Daniel Kayiwa, Drew Roos
 */
class FormDef : IFormElement, IMetaData, ActionController.ActionResultProcessor {

    private var children: ArrayList<IFormElement> = ArrayList()
    private var id: Int = 0
    private var title: String? = null
    private var name: String? = null
    private var extensions: ArrayList<XFormExtension> = ArrayList()
    private var localizer: Localizer? = null

    // This list is topologically ordered, meaning for any tA
    // and tB in the list, where tA comes before tB, evaluating tA cannot
    // depend on any result from evaluating tB
    private var triggerables: ArrayList<Triggerable>? = null

    // <IConditionExpr> contents of <output> tags that serve as parameterized
    // arguments to captions
    private var outputFragments: ArrayList<Any?> = ArrayList()

    /**
     * Map references to the calculate/relevancy conditions that depend on that
     * reference's value. Used to trigger re-evaluation of those conditionals
     * when the reference is updated.
     */
    private var triggerIndex: HashMap<TreeReference, ArrayList<Triggerable>>? = null

    /**
     * Associates repeatable nodes with the Condition that determines their
     * relevancy.
     */
    private var conditionRepeatTargetIndex: HashMap<TreeReference, Condition>? = null

    @JvmField
    var exprEvalContext: EvaluationContext? = null

    private var submissionProfiles: HashMap<String, SubmissionProfile> = HashMap()

    /**
     * Secondary and external instance pointers
     */
    private var formInstances: HashMap<String, DataInstance<*>> = HashMap()

    private var mainInstance: FormInstance? = null

    private var mDebugModeEnabled: Boolean = false

    private val triggeredDuringInsert: ArrayList<Triggerable> = ArrayList()

    private var actionController: ActionController = ActionController()
    // If this instance is just being edited, don't fire end of form events
    private var isCompletedInstance: Boolean = false

    private var mProfilingEnabled: Boolean = false
    private var useExpressionCaching: Boolean = false

    @JvmField
    var sendCalloutHandler: FormSendCalloutHandler? = null

    /**
     * Cache children that trigger target will cascade to. For speeding up
     * calculations that determine what needs to be triggered when a value
     * changes.
     */
    private val cachedCascadingChildren: LocalCacheTable<TreeReference, ArrayList<TreeReference>> =
        LocalCacheTable()

    constructor() : this(false)

    constructor(useExpressionCaching: Boolean) {
        setID(-1)
        setChildren(null)
        triggerables = ArrayList()
        triggerIndex = HashMap()
        // This is kind of a wreck...
        setEvaluationContext(EvaluationContext(null))
        outputFragments = ArrayList()
        submissionProfiles = HashMap()
        formInstances = HashMap()
        extensions = ArrayList()
        actionController = ActionController()
        this.useExpressionCaching = useExpressionCaching
    }

    /**
     * Getters and setters for the vectors
     */
    fun addNonMainInstance(instance: DataInstance<*>) {
        formInstances[instance.getInstanceId()!!] = instance
        this.setEvaluationContext(EvaluationContext(null))
    }

    /**
     * Get an instance based on a name
     */
    fun getNonMainInstance(name: String?): DataInstance<*>? {
        if (!formInstances.containsKey(name)) {
            return null
        }
        return formInstances[name]
    }

    fun getNonMainInstances(): MutableIterator<DataInstance<*>> {
        return formInstances.values.iterator()
    }

    /**
     * Set the main instance
     */
    fun setInstance(fi: FormInstance) {
        mainInstance = fi
        fi.setFormId(getID())
        this.setEvaluationContext(EvaluationContext(null))
        attachControlsToInstanceData()
    }

    /**
     * Get the main instance
     */
    fun getMainInstance(): FormInstance? {
        return mainInstance
    }

    fun getInstance(): FormInstance? {
        return getMainInstance()
    }

    // ---------- child elements
    override fun addChild(fe: IFormElement?) {
        this.children.add(fe!!)
    }

    override fun getChild(i: Int): IFormElement {
        if (i < this.children.size)
            return this.children[i]

        throw ArrayIndexOutOfBoundsException(
            "FormDef: invalid child index: $i only ${children.size} children"
        )
    }

    fun getChild(index: FormIndex?): IFormElement {
        var currentIndex = index
        var element: IFormElement = this
        while (currentIndex != null && currentIndex.isInForm()) {
            element = element.getChild(currentIndex.getLocalIndex())!!
            currentIndex = currentIndex.nextLevel
        }
        return element
    }

    /**
     * Dereference the form index and return a ArrayList of all interstitial nodes
     * (top-level parent first; index target last)
     *
     * Ignore 'new-repeat' node for now; just return/stop at ref to
     * yet-to-be-created repeat node (similar to repeats that already exist)
     */
    fun explodeIndex(index: FormIndex): ArrayList<*> {
        val indexes = ArrayList<Int>()
        val multiplicities = ArrayList<Int>()
        val elements = ArrayList<IFormElement>()

        collapseIndex(index, indexes, multiplicities, elements)
        return elements
    }

    // take a reference, find the instance node it refers to (factoring in
    // multiplicities)
    fun getChildInstanceRef(index: FormIndex): TreeReference? {
        val indexes = ArrayList<Int>()
        val multiplicities = ArrayList<Int>()
        val elements = ArrayList<IFormElement>()

        collapseIndex(index, indexes, multiplicities, elements)
        return getChildInstanceRef(elements, multiplicities)
    }

    /**
     * Return a tree reference which follows the path down the concrete elements provided
     * along with the multiplicities provided.
     */
    fun getChildInstanceRef(
        elements: ArrayList<IFormElement>,
        multiplicities: ArrayList<Int>
    ): TreeReference? {
        if (elements.size == 0) {
            return null
        }

        // get reference for target element
        val ref = DataInstance.unpackReference(elements.last().getBind()!!).clone()
        for (i in 0 until ref.size()) {
            // There has to be a better way to encapsulate this
            if (ref.getMultiplicity(i) != TreeReference.INDEX_ATTRIBUTE) {
                ref.setMultiplicity(i, 0)
            }
        }

        // fill in multiplicities for repeats along the way
        for (i in 0 until elements.size) {
            val temp = elements[i]
            if (temp is GroupDef && temp.isRepeat()) {
                val repRef = DataInstance.unpackReference(temp.getBind()!!)
                if (repRef.isParentOf(ref, false)) {
                    val repMult = multiplicities[i]
                    ref.setMultiplicity(repRef.size() - 1, repMult)
                } else {
                    // question/repeat hierarchy is not consistent with
                    // instance instance and bindings
                    return null
                }
            }
        }

        return ref
    }

    fun setLocalizer(l: Localizer?) {
        this.localizer = l
    }

    // don't think this should ever be called(!)
    override fun getBind(): XPathReference {
        throw RuntimeException("method not implemented")
    }

    fun setValue(data: IAnswerData?, ref: TreeReference) {
        setValue(data, ref, mainInstance!!.resolveReference(ref)!!)
    }

    fun setValue(data: IAnswerData?, ref: TreeReference, node: TreeElement) {
        setAnswer(data, node)
        triggerTriggerables(ref)
        // TODO: pre-populate fix-count repeats here?
    }

    fun setAnswer(data: IAnswerData?, ref: TreeReference) {
        setAnswer(data, mainInstance!!.resolveReference(ref)!!)
    }

    fun setAnswer(data: IAnswerData?, node: TreeElement) {
        node.setAnswer(data)
    }

    /**
     * Deletes the inner-most repeat that this node belongs to and returns the
     * corresponding FormIndex. Behavior is currently undefined if you call this
     * method on a node that is not contained within a repeat.
     */
    fun deleteRepeat(index: FormIndex): FormIndex {
        val indexes = ArrayList<Int>()
        val multiplicities = ArrayList<Int>()
        val elements = ArrayList<IFormElement>()
        collapseIndex(index, indexes, multiplicities, elements)

        // loop backwards through the elements, removing objects from each
        // vector, until we find a repeat
        // TODO: should probably check to make sure size > 0
        for (i in elements.size - 1 downTo 0) {
            val e = elements[i]
            if (e is GroupDef && e.isRepeat()) {
                break
            } else {
                indexes.removeAt(i)
                multiplicities.removeAt(i)
                elements.removeAt(i)
            }
        }

        // build new formIndex which includes everything
        // up to the node we're going to remove
        val newIndex = buildIndex(indexes, multiplicities, elements)

        val deleteRef = getChildInstanceRef(newIndex)!!
        val deleteElement = mainInstance!!.resolveReference(deleteRef)!!
        val parentRef = deleteRef.getParentRef()!!
        val parentElement = mainInstance!!.resolveReference(parentRef)!!

        parentElement.removeChild(deleteElement)

        reduceTreeSiblingMultiplicities(parentElement, deleteElement)

        this.getMainInstance()!!.cleanCache()

        triggerTriggerables(deleteRef)
        return newIndex
    }

    /**
     * When a repeat is deleted, we need to reduce the multiplicities of its siblings that were higher than it
     * by one.
     */
    private fun reduceTreeSiblingMultiplicities(parentElement: TreeElement, deleteElement: TreeElement) {
        val childMult = deleteElement.getMult()
        // update multiplicities of other child nodes
        for (i in 0 until parentElement.getNumChildren()) {
            val child = parentElement.getChildAt(i)
            // We also need to check that this element matches the deleted element (besides multiplicity)
            // in the case where the deleted repeat's parent isn't a subgroup
            if (child!!.doFieldsMatch(deleteElement) && child.getMult() > childMult) {
                child.setMult(child.getMult() - 1)
            }
        }
    }

    @Throws(InvalidReferenceException::class)
    fun createNewRepeat(index: FormIndex) {
        val repeatContextRef = getChildInstanceRef(index)!!
        val template = mainInstance!!.getTemplate(repeatContextRef)!!

        mainInstance!!.copyNode(template, repeatContextRef)

        // Fire jr-insert events before "calculate"s
        triggeredDuringInsert.clear()
        actionController.triggerActionsFromEvent(Action.EVENT_JR_INSERT, this, repeatContextRef, this)

        // trigger conditions that depend on the creation of this new node
        triggerTriggerables(repeatContextRef)

        // trigger conditions for the node (and sub-nodes)
        initTriggerablesRootedBy(repeatContextRef, triggeredDuringInsert)
    }

    override fun processResultOfAction(refSetByAction: TreeReference, event: String) {
        if (Action.EVENT_JR_INSERT == event) {
            val triggerables =
                triggerIndex!![refSetByAction.genericize()]
            if (triggerables != null) {
                for (elem in triggerables) {
                    triggeredDuringInsert.add(elem)
                }
            }
        }
    }

    fun isRepeatRelevant(repeatRef: TreeReference): Boolean {
        var relev = true

        val c = conditionRepeatTargetIndex!![repeatRef.genericize()]
        if (c != null) {
            relev = c.evalBool(mainInstance, EvaluationContext(exprEvalContext, repeatRef))
        }

        // check the relevancy of the immediate parent
        if (relev) {
            val templNode = mainInstance!!.getTemplate(repeatRef)!!
            val parentPath = templNode.getParent()!!.getRef().genericize()
            val parentNode = mainInstance!!.resolveReference(parentPath.contextualize(repeatRef)!!)
            relev = parentNode!!.isRelevant
        }

        return relev
    }

    /**
     * Does the repeat group at the given index enable users to add more items,
     * and if so, has the user reached the item limit?
     */
    fun canCreateRepeat(repeatRef: TreeReference, repeatIndex: FormIndex): Boolean {
        val repeat = this.getChild(repeatIndex) as GroupDef

        // Check to see if this repeat can have children added by the user
        if (repeat.noAddRemove) {
            // Check to see if there's a count to use to determine how many children this repeat
            // should have
            if (repeat.getCountReference() != null) {
                val currentMultiplicity = repeatIndex.getElementMultiplicity()

                val absPathToCount = repeat.getConextualizedCountReference(repeatRef)
                val countNode = this.getMainInstance()!!.resolveReference(absPathToCount)
                    ?: throw XPathTypeMismatchException(
                        "Could not find the location " +
                                absPathToCount.toString() + " where the repeat at " +
                                repeatRef.toString(false) + " is looking for its count"
                    )
                // get the total multiplicity possible
                val boxedCount = countNode.getValue()
                val count: Int
                if (boxedCount == null) {
                    count = 0
                } else {
                    try {
                        count = IntegerData().cast(boxedCount.uncast())!!.getValue() as Int
                    } catch (iae: IllegalArgumentException) {
                        throw XPathTypeMismatchException(
                            "The repeat count value \"" +
                                    boxedCount.uncast().getString() +
                                    "\" at " + absPathToCount.toString() +
                                    " must be a number!"
                        )
                    }
                }

                if (count <= currentMultiplicity) {
                    return false
                }
            } else {
                // Otherwise the user can never add repeat instances
                return false
            }
        }

        // TODO: If we think the node is still relevant, we also need to figure out a way to test that assumption against
        // the repeat's constraints.

        return true
    }

    @Throws(InvalidReferenceException::class)
    fun copyItemsetAnswer(q: QuestionDef, targetNode: TreeElement, data: IAnswerData) {
        val itemset = q.getDynamicChoices()!!
        val targetRef = targetNode.getRef()
        val destRef = itemset.getDestRef()!!.contextualize(targetRef)!!

        var selections: ArrayList<Selection>? = null
        val selectedValues = ArrayList<String>()
        if (data is SelectMultiData) {
            @Suppress("UNCHECKED_CAST")
            selections = data.getValue() as ArrayList<Selection>
        } else if (data is SelectOneData) {
            selections = ArrayList()
            selections.add(data.getValue() as Selection)
        }
        if (itemset.valueRef != null) {
            for (i in 0 until selections!!.size) {
                selectedValues.add(selections[i].choice!!.value!!)
            }
        }

        // delete existing dest nodes that are not in the answer selection
        val existingValues = HashMap<String, TreeElement>()
        val existingNodes = exprEvalContext!!.expandReference(destRef)!!
        for (i in 0 until existingNodes.size) {
            val node = getMainInstance()!!.resolveReference(existingNodes[i])!!

            if (itemset.valueRef != null) {
                val value = itemset.getRelativeValue()!!.evalReadable(
                    this.getMainInstance(),
                    EvaluationContext(exprEvalContext, node.getRef())
                )!!
                if (selectedValues.contains(value)) {
                    existingValues[value] = node // cache node if in selection and already exists
                }
            }

            // delete from target
            targetNode.removeChild(node)
        }

        // copy in nodes for new answer; preserve ordering in answer
        for (i in 0 until selections!!.size) {
            val s = selections[i]
            val ch = s.choice!!

            var cachedNode: TreeElement? = null
            if (itemset.valueRef != null) {
                val value = ch.value
                if (existingValues.containsKey(value)) {
                    cachedNode = existingValues[value]
                }
            }

            if (cachedNode != null) {
                cachedNode.setMult(i)
                targetNode.addChild(cachedNode)
            } else {
                getMainInstance()!!.copyItemsetNode(ch.copyNode!!, destRef, this)
            }
        }

        // trigger conditions that depend on the creation of these new nodes
        triggerTriggerables(destRef)

        // initialize conditions for the node (and sub-nodes)
        initTriggerablesRootedBy(destRef, ArrayList())
    }

    /**
     * Add a Condition to the form's Collection.
     */
    fun addTriggerable(t: Triggerable): Triggerable {
        val existingIx = triggerables!!.indexOf(t)
        if (existingIx != -1) {
            // One node may control access to many nodes; this means many nodes
            // effectively have the same condition. Let's identify when
            // conditions are the same, and store and calculate it only once.

            val existingTriggerable = triggerables!![existingIx]

            existingTriggerable.contextRef = existingTriggerable.contextRef!!.intersect(t.contextRef!!)

            return existingTriggerable

            // NOTE: if the contextRef is unnecessarily deep, the condition
            // will be evaluated more times than needed. Perhaps detect when
            // 'identical' condition has a shorter contextRef, and use that one
            // instead?
        } else {
            // The triggerable isn't being added in any order, so topological
            // sorting has been disrupted
            triggerables!!.add(t)

            for (trigger in t.getTriggers()) {
                val predicatelessTrigger = t.widenContextToAndClearPredicates(trigger)
                if (!triggerIndex!!.containsKey(predicatelessTrigger)) {
                    triggerIndex!![predicatelessTrigger.clone()] = ArrayList()
                }
                val triggered = triggerIndex!![predicatelessTrigger]!!
                if (!triggered.contains(t)) {
                    triggered.add(t)
                }
            }

            return t
        }
    }

    /**
     * Dependency-sorted enumerator for the triggerables present in the form.
     */
    fun getTriggerables(): Iterator<Triggerable> {
        return triggerables!!.iterator()
    }

    /**
     * @return All references in the form that are depended on by
     * calculate/relevancy conditions.
     */
    fun refWithTriggerDependencies(): Iterator<TreeReference> {
        return triggerIndex!!.keys.iterator()
    }

    /**
     * Get the triggerable conditions, like relevancy/calculate, that depend on
     * the given reference.
     */
    fun conditionsTriggeredByRef(ref: TreeReference): ArrayList<Triggerable>? {
        return triggerIndex!![ref]
    }

    /**
     * Finalize the DAG associated with the form's triggered conditions.
     */
    @Throws(IllegalStateException::class)
    fun finalizeTriggerables() {
        val partialOrdering = ArrayList<Pair<Triggerable, Triggerable>>()
        buildPartialOrdering(partialOrdering)

        val vertices = ArrayList(triggerables!!)
        triggerables!!.clear()

        while (vertices.isNotEmpty()) {
            val roots = buildRootNodes(vertices, partialOrdering)

            if (roots.isEmpty()) {
                // if no root nodes while graph still has nodes, graph has cycles
                throwGraphCyclesException(vertices)
            }

            setOrderOfTriggerable(roots, vertices, partialOrdering)
        }

        // At this point triggerables should be topologically sorted (according
        // to Drew)

        buildConditionRepeatTargetIndex()
    }

    private fun buildPartialOrdering(partialOrdering: MutableList<Pair<Triggerable, Triggerable>>) {
        for (t in triggerables!!) {
            val deps = ArrayList<Triggerable>()
            fillTriggeredElements(t, deps, false)

            for (u in deps) {
                partialOrdering.add(Pair(t, u))
            }
        }
    }

    private fun getTreeReferenceAndChildren(reference: TreeReference): ArrayList<TreeReference> {
        var updatedNodes = ArrayList<TreeReference>()
        updatedNodes.add(reference)
        updatedNodes = findCascadeReferences(reference, updatedNodes)
        return updatedNodes
    }

    private fun throwGraphCyclesException(vertices: List<Triggerable>) {
        val edges = ArrayList<Array<TreeReference>>()
        for (outerTriggerables in vertices) {
            for (outerReference in outerTriggerables.targets) {
                // Get child refs because children are affected by parents
                val updatedNodes = getTreeReferenceAndChildren(outerReference)
                for (innerReference in updatedNodes) {
                    @Suppress("UNCHECKED_CAST")
                    val triggered = conditionsTriggeredByRef(innerReference) as? ArrayList<Triggerable>
                    if (triggered != null) {
                        for (trig in triggered) {
                            if (innerReference != outerReference) {
                                // We are dealing with a child and parent, so add an edge between the parent
                                // and child for clarity in the error message.
                                edges.add(arrayOf(outerReference, innerReference))
                            }
                            // Add all the targets of the triggered
                            for (reference in trig.targets) {
                                edges.add(arrayOf(innerReference, reference))
                            }
                        }
                    }
                }
            }
        }
        throw IllegalStateException(ShortestCycleAlgorithm(edges).getCycleErrorMessage())
    }

    private fun setOrderOfTriggerable(
        roots: List<Triggerable>,
        vertices: MutableList<Triggerable>,
        partialOrdering: MutableList<Pair<Triggerable, Triggerable>>
    ) {
        for (root in roots) {
            triggerables!!.add(root)
            vertices.remove(root)
        }
        for (i in partialOrdering.size - 1 downTo 0) {
            val edge = partialOrdering[i]
            if (roots.contains(edge.first)) {
                partialOrdering.removeAt(i)
            }
        }
    }

    private fun buildConditionRepeatTargetIndex() {
        conditionRepeatTargetIndex = HashMap()
        for (t in triggerables!!) {
            if (t is Condition) {
                for (target in t.targets) {
                    if (mainInstance!!.getTemplate(target) != null) {
                        conditionRepeatTargetIndex!![target] = t
                    }
                }
            }
        }
    }

    /**
     * Get all of the elements which will need to be evaluated (in order) when
     * the triggerable is fired.
     */
    @PlatformTrace
    private fun fillTriggeredElements(
        t: Triggerable,
        destination: MutableList<Triggerable>,
        isRepeatEntryInit: Boolean
    ) {
        if (t.canCascade()) {
            for (target in t.targets) {
                var updatedNodes = ArrayList<TreeReference>()
                updatedNodes.add(target)
                // Repeat sub-elements have already been added to 'destination'
                // when we grabbed all triggerables that target children of the
                // repeat entry (via initTriggerablesRootedBy). Hence skip them
                if (!isRepeatEntryInit && t.isCascadingToChildren()) {
                    updatedNodes = findCascadeReferences(target, updatedNodes)
                }

                addTriggerablesTargetingNodes(updatedNodes, destination)
            }
        }
    }

    /**
     * Gather list of generic references to children of a target reference for
     * a triggerable that cascades to its children.
     */
    private fun findCascadeReferences(
        target: TreeReference,
        updatedNodes: ArrayList<TreeReference>
    ): ArrayList<TreeReference> {
        var nodes = updatedNodes
        val cachedNodes = cachedCascadingChildren.retrieve(target)
        if (cachedNodes == null) {
            if (target.getMultLast() == TreeReference.INDEX_ATTRIBUTE) {
                // attributes don't have children that might change under
                // contextualization
                cachedCascadingChildren.register(target, nodes)
            } else {
                val expandedRefs = exprEvalContext!!.expandReference(target, true)!!
                if (expandedRefs.size > 0) {
                    val template = mainInstance!!.getTemplatePath(target)
                    if (template != null) {
                        addChildrenOfElement(template, nodes)
                        cachedCascadingChildren.register(target, nodes)
                    } else {
                        addChildrenOfReference(expandedRefs, nodes)
                    }
                }
            }
        } else {
            nodes = cachedNodes
        }
        return nodes
    }

    /**
     * Resolve the expanded references and gather their generic children and
     * attributes into the genericRefs list.
     */
    private fun addChildrenOfReference(
        expandedRefs: List<TreeReference>,
        genericRefs: MutableList<TreeReference>
    ) {
        for (ref in expandedRefs) {
            addChildrenOfElement(exprEvalContext!!.resolveReference(ref)!!, genericRefs)
        }
    }

    private fun addTriggerablesTargetingNodes(
        updatedNodes: List<TreeReference>,
        destination: MutableList<Triggerable>
    ) {
        // Now go through each of these updated nodes (generally just 1 for a normal calculation,
        // multiple nodes if there's a relevance cascade.
        for (ref in updatedNodes) {
            // Check our index to see if that target is a Trigger for other conditions

            var predicatelessRef = ref
            if (ref.hasPredicates()) {
                predicatelessRef = ref.removePredicates()
            }
            val triggered = triggerIndex!![predicatelessRef]

            if (triggered != null) {
                // If so, walk all of these triggerables that we found
                for (triggerable in triggered) {
                    // And add them to the queue if they aren't there already
                    if (!destination.contains(triggerable)) {
                        destination.add(triggerable)
                    }
                }
            }
        }
    }

    /**
     * Enables debug traces in this form.
     */
    fun enableDebugTraces() {
        if (!mDebugModeEnabled) {
            for (t in triggerables!!) {
                t.setDebug(true)
            }

            // Re-execute all triggerables to collect traces
            initAllTriggerables()
            mDebugModeEnabled = true
        }
    }

    /**
     * Disable debug tracing for this form.
     */
    fun disableDebugTraces() {
        if (mDebugModeEnabled) {
            for (t in triggerables!!) {
                t.setDebug(false)
            }
            mDebugModeEnabled = false
        }
    }

    /**
     * Aggregates a map of evaluation traces collected by the form's
     * triggerables.
     */
    @Throws(IllegalStateException::class)
    fun getDebugTraceMap(): HashMap<TreeReference, HashMap<String, EvaluationTrace>> {
        if (!mDebugModeEnabled) {
            throw IllegalStateException("Debugging is not enabled")
        }

        val debugInfo = HashMap<TreeReference, HashMap<String, EvaluationTrace>>()

        for (t in triggerables!!) {
            val triggerOutputs = t.getEvaluationTraces()

            val e = triggerOutputs.keys.iterator()
            while (e.hasNext()) {
                val elementRef = e.next() as TreeReference
                val label = t.getDebugLabel()
                var traces = debugInfo[elementRef]
                if (traces == null) {
                    traces = HashMap()
                }
                traces[label] = triggerOutputs[elementRef]!!
                debugInfo[elementRef] = traces
            }
        }

        return debugInfo
    }

    @PlatformTrace
    private fun initAllTriggerables() {
        // Use all triggerables because we can assume they are rooted by rootRef
        val rootRef = TreeReference.rootRef()

        val applicable = ArrayList<Triggerable>()
        for (triggerable in triggerables!!) {
            applicable.add(triggerable)
        }

        evaluateTriggerables(applicable, rootRef, false)
    }

    /**
     * Evaluate triggerables targeting references that are children of the
     * provided newly created (repeat instance) ref.
     */
    @PlatformTrace
    private fun initTriggerablesRootedBy(
        rootRef: TreeReference,
        triggeredDuringInsert: ArrayList<Triggerable>
    ) {
        val genericRoot = rootRef.genericize()

        val applicable = ArrayList<Triggerable>()
        for (triggerable in triggerables!!) {
            for (target in triggerable.targets) {
                if (genericRoot.isParentOf(target, false)) {
                    if (!triggeredDuringInsert.contains(triggerable)) {
                        applicable.add(triggerable)
                        break
                    }
                }
            }
        }

        evaluateTriggerables(applicable, rootRef, true)
    }

    /**
     * The entry point for the DAG cascade after a value is changed in the model.
     */
    @PlatformTrace
    fun triggerTriggerables(ref: TreeReference) {
        // turn unambiguous ref into a generic ref to identify what nodes
        // should be triggered by this reference changing
        val genericRef = ref.genericize()

        // get triggerables which are activated by the generic reference
        val triggered = triggerIndex!![genericRef]
        if (triggered != null) {
            val triggeredCopy = ArrayList(triggered)

            evaluateTriggerables(triggeredCopy, ref, false)
        }
    }

    /**
     * Step 2 in evaluating DAG computation updates from a value being changed
     * in the instance.
     */
    @PlatformTrace
    private fun evaluateTriggerables(
        tv: MutableList<Triggerable>,
        anchorRef: TreeReference,
        isRepeatEntryInit: Boolean
    ) {
        // Update the list of triggerables that need to be evaluated.
        var i = 0
        while (i < tv.size) {
            // NOTE PLM: tv may grow in size through iteration.
            val t = tv[i]
            fillTriggeredElements(t, tv, isRepeatEntryInit)
            i++
        }

        // tv should now contain all of the triggerable components which are
        // going to need to be addressed by this update.
        for (triggerable in triggerables!!) {
            if (tv.contains(triggerable)) {
                evaluateTriggerable(triggerable, anchorRef)
            }
        }
    }

    /**
     * Step 3 in DAG cascade. evaluate the individual triggerable expressions
     * against the anchor (the value that changed which triggered recomputation)
     */
    @PlatformTrace
    private fun evaluateTriggerable(triggerable: Triggerable, anchorRef: TreeReference) {
        // Contextualize the reference used by the triggerable against the anchor
        val contextRef = triggerable.narrowContextBy(anchorRef)
        if (isTracingEnabled()) {
            setActiveSpanTag("triggerable", triggerable.toString())
        }

        // Now identify all of the fully qualified nodes which this triggerable
        // updates. (Multiple nodes can be updated by the same trigger)
        val expandedReferences = exprEvalContext!!.expandReference(contextRef)!!

        for (treeReference in expandedReferences) {
            triggerable.apply(mainInstance, exprEvalContext, treeReference, this)
        }
    }

    fun evaluateConstraint(ref: TreeReference, data: IAnswerData?): Boolean {
        if (data is InvalidData) {
            return false
        }

        if (data == null) {
            return true
        }

        val node = mainInstance!!.resolveReference(ref)!!
        val c = node.getConstraint() ?: return true
        val ec = EvaluationContext(exprEvalContext, ref)
        ec.isConstraint = true
        ec.candidateValue = data

        return c.constraint!!.eval(mainInstance, ec)
    }

    fun setEvaluationContext(ec: EvaluationContext) {
        val newEc = EvaluationContext(mainInstance, formInstances, ec)
        initEvalContext(newEc)
        if (useExpressionCaching) {
            newEc.enableExpressionCaching()
        }
        this.exprEvalContext = newEc
    }

    fun getEvaluationContext(): EvaluationContext? {
        return this.exprEvalContext
    }

    private fun initEvalContext(ec: EvaluationContext) {
        if (!ec.getFunctionHandlers().containsKey("jr:itext")) {
            val f = this
            ec.addFunctionHandler(object : IFunctionHandler {
                override fun getName(): String {
                    return "jr:itext"
                }

                override fun eval(args: Array<Any?>?, ec: EvaluationContext?): Any {
                    var textID = args!![0] as String
                    try {
                        // SUUUUPER HACKY
                        val form = ec!!.getOutputTextForm()
                        if (form != null) {
                            textID = "$textID;$form"
                            val result = f.getLocalizer()!!.getRawText(f.getLocalizer()!!.locale, textID)
                            return result ?: ""
                        } else {
                            val text = f.getLocalizer()!!.getText(textID)
                            return text ?: "[itext:$textID]"
                        }
                    } catch (nsee: NoSuchElementException) {
                        return "[nolocale]"
                    }
                }

                override fun getPrototypes(): ArrayList<Any> {
                    val proto = arrayOf<Class<*>>(String::class.java)
                    val v = ArrayList<Any>()
                    v.add(proto)
                    return v
                }

                override fun rawArgs(): Boolean {
                    return false
                }
            })
        }

        /* function to reverse a select value into the display label for that choice in the question it came from */
        if (!ec.getFunctionHandlers().containsKey("jr:choice-name")) {
            val f = this
            ec.addFunctionHandler(object : IFunctionHandler {
                override fun getName(): String {
                    return "jr:choice-name"
                }

                override fun eval(args: Array<Any?>?, ec: EvaluationContext?): Any {
                    try {
                        val value = args!![0] as String
                        val questionXpath = args[1] as String
                        val ref = RestoreUtils.ref(questionXpath)

                        val q = findQuestionByRef(ref, f)
                            ?: return ""
                        if (q.getControlType() != Constants.CONTROL_SELECT_ONE &&
                            q.getControlType() != Constants.CONTROL_SELECT_MULTI
                        ) {
                            return ""
                        }

                        println("here!!")

                        val choices = q.getChoices()!!
                        for (ch in choices) {
                            if (ch.value == value) {
                                val textID = ch.textID
                                return if (textID != null) {
                                    f.getLocalizer()!!.getText(textID) as Any
                                } else {
                                    ch.labelInnerText as Any
                                }
                            }
                        }
                        return ""
                    } catch (e: Exception) {
                        throw WrappedException("error in evaluation of xpath function [choice-name]", e)
                    }
                }

                override fun getPrototypes(): ArrayList<Any> {
                    val proto = arrayOf<Class<*>>(String::class.java, String::class.java)
                    val v = ArrayList<Any>()
                    v.add(proto)
                    return v
                }

                override fun rawArgs(): Boolean {
                    return false
                }
            })
        }
    }

    fun enableExpressionCaching() {
        useExpressionCaching = true
    }

    fun fillTemplateString(template: String, contextRef: TreeReference): String {
        return fillTemplateString(template, contextRef, HashMap<String, Any?>())
    }

    /**
     * Performs substitutions on place-holder template from form text by
     * evaluating args in template using the current context.
     */
    fun fillTemplateString(
        template: String,
        contextRef: TreeReference,
        variables: HashMap<String, *>
    ): String {
        var currentTemplate = template
        // argument to value mapping
        val args = HashMap<String, String>()

        var depth = 0
        // grab all template arguments that need to have substitutions performed
        var outstandingArgs = Localizer.getArgs(currentTemplate)

        // Step through outstandingArgs from the template, looking up the value
        // they map to, evaluating that under the evaluation context and
        // storing in the local args mapping.
        // Then perform substitutions over the template until a fixpoint is found
        while (outstandingArgs.size > 0) {
            for (i in 0 until outstandingArgs.size) {
                val argName = outstandingArgs[i] as String
                // lookup value an arg points to if it isn't in our local mapping
                if (!args.containsKey(argName)) {
                    var ix = -1
                    try {
                        ix = argName.toInt()
                    } catch (nfe: NumberFormatException) {
                        org.javarosa.core.util.platformStdErrPrintln("Warning: expect arguments to be numeric [$argName]")
                    }

                    if (ix < 0 || ix >= outputFragments.size) {
                        continue
                    }

                    val expr = outputFragments[ix] as IConditionExpr
                    val ec = EvaluationContext(exprEvalContext, contextRef)
                    ec.setOriginalContext(contextRef)
                    ec.setVariables(variables)
                    val value = expr.evalReadable(this.getMainInstance(), ec) ?: ""
                    args[argName] = value
                }
            }

            val templateAfterSubstitution = Localizer.processArguments(currentTemplate, args)

            // The last substitution made no progress, probably because the
            // argument isn't in outputFragments, so stop looping and
            // attempting more subs!
            if (currentTemplate == templateAfterSubstitution) {
                return currentTemplate
            }

            currentTemplate = templateAfterSubstitution

            // Since strings being substituted might themselves have arguments that
            // need to be further substituted, we must recompute the unperformed
            // substitutions and continue to loop.
            outstandingArgs = Localizer.getArgs(currentTemplate)

            if (depth++ >= TEMPLATING_RECURSION_LIMIT) {
                throw RuntimeException("Dependency cycle in <output>s; recursion limit exceeded!!")
            }
        }

        return currentTemplate
    }

    @PlatformTrace
    fun populateDynamicChoices(itemset: ItemsetBinding, curQRef: TreeReference) {
        if (isTracingEnabled()) {
            setActiveSpanTag("itemset", itemset.nodesetRef.toString())
            setActiveSpanTag("treeReference", curQRef.toString())
        }
        ItemSetUtils.populateDynamicChoices(itemset, curQRef, exprEvalContext!!, getMainInstance(), mProfilingEnabled)
    }

    private fun isTracingEnabled(): Boolean {
        return "true" == System.getProperty("src.main.java.org.javarosa.enableOpenTracing")
    }

    override fun toString(): String {
        return getTitle() ?: ""
    }

    fun postProcessInstance() {
        if (!isCompletedInstance) {
            actionController.triggerActionsFromEvent(Action.EVENT_XFORMS_REVALIDATE, this)
        }
    }

    /**
     * Reads the form definition object from the supplied stream.
     */
    @PlatformTrace
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(dis: PlatformDataInputStream, pf: PrototypeFactory) {
        setID(SerializationHelpers.readInt(dis))
        setName(nullIfEmpty(SerializationHelpers.readString(dis)))
        setTitle(SerializationHelpers.readNullableString(dis, pf))
        @Suppress("UNCHECKED_CAST")
        setChildren(SerializationHelpers.readListPoly(dis, pf) as ArrayList<IFormElement>?)
        setInstance(SerializationHelpers.readExternalizable(dis, pf) { FormInstance() })

        setLocalizer(SerializationHelpers.readNullableExternalizable(dis, pf) { Localizer() })

        val vcond = SerializationHelpers.readList(dis, pf) { Condition() }
        val econd = vcond.iterator()
        while (econd.hasNext()) {
            addTriggerable(econd.next())
        }
        val vcalc = SerializationHelpers.readList(dis, pf) { Recalculate() }
        val ecalc = vcalc.iterator()
        while (ecalc.hasNext()) {
            addTriggerable(ecalc.next())
        }
        finalizeTriggerables()

        outputFragments = SerializationHelpers.readListPoly(dis, pf)

        submissionProfiles = SerializationHelpers.readStringExtMap(dis, pf) { SubmissionProfile() }

        @Suppress("UNCHECKED_CAST")
        formInstances = SerializationHelpers.readStringTaggedMap(dis, pf) as HashMap<String, DataInstance<*>>

        @Suppress("UNCHECKED_CAST")
        extensions = SerializationHelpers.readListPoly(dis, pf) as ArrayList<XFormExtension>

        setEvaluationContext(EvaluationContext(null))
        actionController = SerializationHelpers.readExternalizable(dis, pf) { ActionController() }
    }

    /**
     * meant to be called after deserialization and initialization of handlers
     */
    fun initialize(
        newInstance: Boolean, isCompletedInstance: Boolean,
        factory: InstanceInitializationFactory?
    ) {
        initialize(newInstance, isCompletedInstance, factory, null, false)
    }

    fun initialize(newInstance: Boolean, factory: InstanceInitializationFactory?) {
        initialize(newInstance, false, factory, null, false)
    }

    fun initialize(
        newInstance: Boolean, factory: InstanceInitializationFactory?,
        locale: String?, isReadOnly: Boolean
    ) {
        initialize(newInstance, false, factory, locale, isReadOnly)
    }

    /**
     * meant to be called after deserialization and initialization of handlers
     */
    @PlatformTrace
    fun initialize(
        newInstance: Boolean, isCompletedInstance: Boolean,
        factory: InstanceInitializationFactory?,
        locale: String?, isReadOnly: Boolean
    ) {
        val en = formInstances.keys.iterator()
        while (en.hasNext()) {
            val instanceId = en.next()
            val instance = formInstances[instanceId]!!
            formInstances[instanceId] = instance.initialize(factory, instanceId)
        }
        setEvaluationContext(this.exprEvalContext!!)

        initLocale(locale)

        if (newInstance) {
            // only dispatch on a form's first opening, not subsequent loadings
            // of saved instances.
            actionController.triggerActionsFromEvent(Action.EVENT_XFORMS_READY, this)
        }
        this.isCompletedInstance = isCompletedInstance
        if (!isReadOnly) {
            initAllTriggerables()
        }
    }

    private fun initLocale(locale: String?) {
        if (localizer != null) {
            if (locale == null || !localizer!!.hasLocale(locale)) {
                if (localizer!!.locale == null) {
                    localizer!!.setToDefault()
                }
            } else {
                localizer!!.setLocale(locale)
            }
        }
    }

    /**
     * Writes the form definition object to the supplied stream.
     */
    @Throws(PlatformIOException::class)
    override fun writeExternal(dos: PlatformDataOutputStream) {
        SerializationHelpers.writeNumeric(dos, getID().toLong())
        SerializationHelpers.writeString(dos, emptyIfNull(getName()))
        SerializationHelpers.writeNullable(dos, getTitle())
        SerializationHelpers.writeListPoly(dos, getChildren())
        SerializationHelpers.write(dos, getMainInstance()!!)
        SerializationHelpers.writeNullable(dos, localizer)

        val conditions = ArrayList<Condition>()
        val recalcs = ArrayList<Recalculate>()
        for (t in triggerables!!) {
            if (t is Condition) {
                conditions.add(t)
            } else if (t is Recalculate) {
                recalcs.add(t)
            }
        }
        SerializationHelpers.writeList(dos, conditions)
        SerializationHelpers.writeList(dos, recalcs)

        SerializationHelpers.writeListPoly(dos, outputFragments)
        SerializationHelpers.writeMap(dos, submissionProfiles)

        // for support of multi-instance forms
        SerializationHelpers.writeTaggedMap(dos, formInstances as HashMap<*, *>)
        SerializationHelpers.writeListPoly(dos, extensions)
        SerializationHelpers.write(dos, actionController)
    }

    fun collapseIndex(
        index: FormIndex,
        indexes: ArrayList<Int>,
        multiplicities: ArrayList<Int>,
        elements: ArrayList<IFormElement>
    ) {
        if (!index.isInForm()) {
            return
        }

        var element: IFormElement = this
        var currentIndex: FormIndex? = index
        while (currentIndex != null) {
            val i = currentIndex.getLocalIndex()
            element = element.getChild(i)!!

            indexes.add(DataUtil.integer(i))
            multiplicities.add(
                DataUtil.integer(if (currentIndex.getInstanceIndex() == -1) 0 else currentIndex.getInstanceIndex())
            )
            elements.add(element)

            currentIndex = currentIndex.nextLevel
        }
    }

    fun buildIndex(
        indexes: ArrayList<Int>,
        multiplicities: ArrayList<Int>,
        elements: ArrayList<IFormElement>
    ): FormIndex {
        var cur: FormIndex? = null
        val curMultiplicities = ArrayList<Int>()
        for (j in 0 until multiplicities.size) {
            curMultiplicities.add(multiplicities[j])
        }

        val curElements = ArrayList<IFormElement>()
        for (j in 0 until elements.size) {
            curElements.add(elements[j])
        }

        for (i in indexes.size - 1 downTo 0) {
            val ix = indexes[i]
            var mult = multiplicities[i]

            if (!(elements[i] is GroupDef && (elements[i] as GroupDef).isRepeat())) {
                mult = -1
            }

            cur = FormIndex(cur, ix, mult, getChildInstanceRef(curElements, curMultiplicities))
            curMultiplicities.removeAt(curMultiplicities.size - 1)
            curElements.removeAt(curElements.size - 1)
        }
        return cur!!
    }

    fun getNumRepetitions(index: FormIndex): Int {
        val indexes = ArrayList<Int>()
        val multiplicities = ArrayList<Int>()
        val elements = ArrayList<IFormElement>()

        if (!index.isInForm()) {
            throw RuntimeException("not an in-form index")
        }

        collapseIndex(index, indexes, multiplicities, elements)

        if (elements.last() !is GroupDef || !(elements.last() as GroupDef).isRepeat()) {
            throw RuntimeException("current element not a repeat")
        }

        // so painful
        val templNode = mainInstance!!.getTemplate(index.getReference()!!)!!
        val parentPath = templNode.getParent()!!.getRef().genericize()
        val parentNode = mainInstance!!.resolveReference(parentPath.contextualize(index.getReference()!!)!!)
        return parentNode!!.getChildMultiplicity(templNode.getName()!!)
    }

    // repIndex == -1 => next repetition about to be created
    fun descendIntoRepeat(index: FormIndex, repIndex: Int): FormIndex {
        var actualRepIndex = repIndex
        val numRepetitions = getNumRepetitions(index)

        val indexes = ArrayList<Int>()
        val multiplicities = ArrayList<Int>()
        val elements = ArrayList<IFormElement>()
        collapseIndex(index, indexes, multiplicities, elements)

        if (actualRepIndex == -1) {
            actualRepIndex = numRepetitions
        } else {
            if (actualRepIndex < 0 || actualRepIndex >= numRepetitions) {
                throw RuntimeException("selection exceeds current number of repetitions")
            }
        }

        multiplicities.set(multiplicities.size - 1, DataUtil.integer(actualRepIndex))

        return buildIndex(indexes, multiplicities, elements)
    }

    override fun getDeepChildCount(): Int {
        var total = 0
        val e = children.iterator()
        while (e.hasNext()) {
            total += (e.next() as IFormElement).getDeepChildCount()
        }
        return total
    }

    override fun getChildren(): ArrayList<IFormElement> {
        return children
    }

    override fun setChildren(children: ArrayList<IFormElement>?) {
        this.children = children ?: ArrayList()
    }

    fun getTitle(): String? {
        return title
    }

    fun setTitle(title: String?) {
        this.title = title
    }

    override fun getID(): Int {
        return id
    }

    override fun setID(id: Int) {
        this.id = id
    }

    fun getName(): String? {
        return name
    }

    fun setName(name: String?) {
        this.name = name
    }

    fun getLocalizer(): Localizer? {
        return localizer
    }

    fun getOutputFragments(): ArrayList<Any?> {
        return outputFragments
    }

    fun setOutputFragments(outputFragments: ArrayList<Any?>) {
        this.outputFragments = outputFragments
    }

    override fun getMetaData(fieldName: String): Any {
        if (fieldName == "DESCRIPTOR") {
            return name ?: ""
        }
        if (fieldName == "XMLNS") {
            return emptyIfNull(mainInstance!!.schema)
        } else {
            throw IllegalArgumentException()
        }
    }

    override fun getMetaDataFields(): Array<String> {
        return arrayOf("DESCRIPTOR", "XMLNS")
    }

    /**
     * Link a deserialized instance back up with its parent FormDef.
     */
    fun attachControlsToInstanceData() {
        attachControlsToInstanceData(getMainInstance()!!.getRoot()!!)
    }

    private fun attachControlsToInstanceData(node: TreeElement) {
        for (i in 0 until node.getNumChildren()) {
            attachControlsToInstanceData(node.getChildAt(i)!!)
        }

        val `val` = node.getValue()
        var selections: ArrayList<Any?>? = null
        if (`val` is SelectOneData) {
            selections = ArrayList()
            selections.add(`val`.getValue())
        } else if (`val` is SelectMultiData) {
            @Suppress("UNCHECKED_CAST")
            selections = `val`.getValue() as ArrayList<Any?>
        }

        if (selections != null) {
            val q = findQuestionByRef(node.getRef(), this)
                ?: throw RuntimeException("FormDef.attachControlsToInstanceData: can't find question to link")

            if (q.getDynamicChoices() != null) {
                // droos: i think we should do something like initializing the itemset here, so that default answers
                // can be linked to the selectchoices.
                // itemset TODO
            }

            for (i in 0 until selections.size) {
                val s = selections[i] as Selection
                s.attachChoice(q)
            }
        }
    }

    /**
     * Appearance isn't a valid attribute for form, but this method must be included
     * as a result of conforming to the IFormElement interface.
     */
    override fun getAppearanceAttr(): String {
        throw RuntimeException("This method call is not relevant for FormDefs getAppearanceAttr ()")
    }

    /**
     * Appearance isn't a valid attribute for form, but this method must be included
     * as a result of conforming to the IFormElement interface.
     */
    override fun setAppearanceAttr(appearanceAttr: String?) {
        throw RuntimeException("This method call is not relevant for FormDefs setAppearanceAttr()")
    }

    override fun getActionController(): ActionController {
        return this.actionController
    }

    /**
     * Not applicable here.
     */
    override fun getLabelInnerText(): String? {
        return null
    }

    /**
     * Not applicable
     */
    override fun getTextID(): String? {
        return null
    }

    /**
     * Not applicable
     */
    override fun setTextID(textID: String?) {
        throw RuntimeException("This method call is not relevant for FormDefs [setTextID()]")
    }

    fun setDefaultSubmission(profile: SubmissionProfile) {
        submissionProfiles[DEFAULT_SUBMISSION_PROFILE] = profile
    }

    fun addSubmissionProfile(submissionId: String, profile: SubmissionProfile) {
        submissionProfiles[submissionId] = profile
    }

    /**
     * @return The submission profile with the given ID.
     */
    fun getSubmissionProfile(id: String): SubmissionProfile? {
        return submissionProfiles[id]
    }

    @Suppress("UNCHECKED_CAST")
    fun <X : XFormExtension> getExtension(extension: Class<X>): X {
        for (ex in extensions) {
            if (ex.javaClass.isAssignableFrom(extension)) {
                return ex as X
            }
        }
        val newEx: X
        try {
            @Suppress("DEPRECATION")
            newEx = extension.newInstance()
        } catch (e: InstantiationException) {
            throw RuntimeException("Illegally Structured XForm Extension " + extension.name)
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Illegally Structured XForm Extension " + extension.name)
        }
        extensions.add(newEx)
        return newEx
    }

    /**
     * Frees all of the components of this form which are no longer needed once it is completed.
     */
    fun seal() {
        triggerables = null
        triggerIndex = null
        conditionRepeatTargetIndex = null
        // We may need this one, actually
        exprEvalContext = null
    }

    /**
     * Register a handler which is capable of handling send actions.
     */
    fun setSendCalloutHandler(sendCalloutHandler: FormSendCalloutHandler?) {
        this.sendCalloutHandler = sendCalloutHandler
    }

    fun dispatchSendCallout(url: String, paramMap: ListMultimap<String, String>?): String? {
        return if (sendCalloutHandler == null) {
            null
        } else {
            sendCalloutHandler!!.performHttpCalloutForResponse(url, paramMap)
        }
    }

    // Checks if the form element at given form Index belongs to a non counted repeat
    fun isNonCountedRepeat(formIndex: FormIndex): Boolean {
        val currentElement = getChild(formIndex)
        if (currentElement is GroupDef && currentElement.isRepeat()) {
            return currentElement.getCountReference() == null
        }
        return false
    }

    companion object {
        @JvmField
        val STORAGE_KEY: String = "FORMDEF"

        private const val TEMPLATING_RECURSION_LIMIT = 10

        // XML ID's cannot start with numbers, so this should never conflict
        private const val DEFAULT_SUBMISSION_PROFILE = "1"

        @JvmStatic
        fun findQuestionByRef(ref: TreeReference, fe: IFormElement): QuestionDef? {
            var currentRef = ref
            if (fe is FormDef) {
                currentRef = currentRef.genericize()
            }

            if (fe is QuestionDef) {
                val bind = DataInstance.unpackReference(fe.getBind()!!)
                return if (currentRef == bind) fe else null
            } else {
                for (i in 0 until fe.getChildren()!!.size) {
                    val ret = findQuestionByRef(currentRef, fe.getChild(i)!!)
                    if (ret != null)
                        return ret
                }
                return null
            }
        }

        private fun buildRootNodes(
            vertices: List<Triggerable>,
            partialOrdering: List<Pair<Triggerable, Triggerable>>
        ): List<Triggerable> {
            val roots = ArrayList(vertices)

            for (edge in partialOrdering) {
                edge.second.updateStopContextualizingAtFromDominator(edge.first)
                roots.remove(edge.second)
            }
            return roots
        }

        /**
         * Gathers generic children and attribute references for the provided
         * element into the genericRefs list.
         */
        @JvmStatic
        private fun addChildrenOfElement(
            treeElem: AbstractTreeElement,
            genericRefs: MutableList<TreeReference>
        ) {
            // recursively add children of element
            for (i in 0 until treeElem.getNumChildren()) {
                val child = treeElem.getChildAt(i)!!
                val genericChild = child.getRef().genericize()
                if (!genericRefs.contains(genericChild)) {
                    genericRefs.add(genericChild)
                }
                addChildrenOfElement(child, genericRefs)
            }

            // add all the attributes of this element
            for (i in 0 until treeElem.getAttributeCount()) {
                val attrName = treeElem.getAttributeName(i) ?: continue
                val child =
                    treeElem.getAttribute(treeElem.getAttributeNamespace(i), attrName)
                        ?: continue
                val genericChild = child.getRef().genericize()
                if (!genericRefs.contains(genericChild)) {
                    genericRefs.add(genericChild)
                }
            }
        }
    }
}
