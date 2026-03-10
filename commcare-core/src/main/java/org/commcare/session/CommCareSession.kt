package org.commcare.session

import org.javarosa.core.util.ListMultimap
import org.commcare.suite.model.ComputedDatum
import org.commcare.suite.model.Detail
import org.commcare.suite.model.EntityDatum
import org.commcare.suite.model.Entry
import org.commcare.suite.model.FormEntry
import org.commcare.suite.model.FormIdDatum
import org.commcare.suite.model.Menu
import org.commcare.suite.model.MultiSelectEntityDatum
import org.commcare.suite.model.RemoteQueryDatum
import org.commcare.suite.model.SessionDatum
import org.commcare.suite.model.StackFrameStep
import org.commcare.suite.model.StackOperation
import org.commcare.suite.model.Text
import org.commcare.util.CommCarePlatform
import org.javarosa.core.model.Constants.EXTRA_POST_SUCCESS
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.core.model.instance.ExternalDataInstanceSource
import org.javarosa.core.model.instance.InstanceInitializationFactory
import org.javarosa.core.services.locale.Localizer
import org.javarosa.core.util.OrderedHashtable
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.parser.XPathSyntaxException
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Hashtable
import java.util.Stack
import java.util.Vector

/**
 * Before arriving at the Form Entry phase, CommCare applications
 * need to determine what form to enter, and with what pre-requisites.
 *
 * A CommCare Session helps to encapsulate this information by identifying
 * the set of possible entry operations (Every piece of data needed to begin
 * entry) and specifying the operation which would most quickly filter our
 * the set of operations.
 *
 * NOTE: Currently horribly coupled to the platform.
 *
 * @author ctsims
 */
open class CommCareSession {

    private val platform: CommCarePlatform?
    private var popped: StackFrameStep? = null
    private var currentCmd: String? = null
    private var smartLinkRedirect: String? = null

    /**
     * A table of all datums (id --> value) that are currently on the session stack
     */
    private val collectedDatums: OrderedHashtable<String, String>
    private var currentXmlns: String? = null

    /**
     * The current session frame data
     */
    @JvmField
    internal var frame: SessionFrame

    /**
     * The stack of pending Frames
     */
    var frameStack: Stack<SessionFrame>
        protected set

    /**
     * Used by touchforms
     */
    @Suppress("unused")
    constructor() : this(null as CommCarePlatform?)

    constructor(platform: CommCarePlatform?) {
        this.platform = platform
        this.collectedDatums = OrderedHashtable()
        this.frame = SessionFrame()
        this.frameStack = Stack()
    }

    /**
     * Copy constructor
     */
    constructor(oldCommCareSession: CommCareSession) {
        // NOTE: 'platform' is being copied in a shallow manner
        this.platform = oldCommCareSession.platform

        val oldPopped = oldCommCareSession.popped
        if (oldPopped != null) {
            this.popped = StackFrameStep(oldPopped)
        }
        this.currentCmd = oldCommCareSession.currentCmd
        this.currentXmlns = oldCommCareSession.currentXmlns
        this.frame = SessionFrame(oldCommCareSession.frame)
        this.smartLinkRedirect = oldCommCareSession.smartLinkRedirect

        collectedDatums = OrderedHashtable()
        val en = oldCommCareSession.collectedDatums.keys()
        while (en.hasMoreElements()) {
            val key = en.nextElement() as String
            collectedDatums.put(key, oldCommCareSession.collectedDatums[key]!!)
        }

        this.frameStack = Stack()
        // NOTE: can't use for/each due to J2ME build issues w/ Stack
        for (i in 0 until oldCommCareSession.frameStack.size) {
            frameStack.addElement(oldCommCareSession.frameStack.elementAt(i))
        }
    }

    /**
     * @param commandId the current command id
     * @return A list of all of the form entry actions that are possible with the given commandId
     * and the given list of already-collected datums
     */
    private fun getEntriesForCommand(commandId: String?): Vector<Entry> {
        val entries = Vector<Entry>()
        if (commandId == null) {
            return entries
        }
        for (s in platform!!.getInstalledSuites()) {
            val menusWithId = s.getMenusWithId(commandId)
            if (menusWithId != null) {
                for (menu in menusWithId) {
                    entries.addAll(getStillValidEntriesFromMenu(menu))
                }
            }

            if (s.getEntries().containsKey(commandId)) {
                entries.addElement(s.getEntries()[commandId])
            }
        }
        return entries
    }

    private fun getMenusForCommand(commandId: String?): Vector<Menu> {
        val menusWithId = Vector<Menu>()
        for (s in platform!!.getInstalledSuites()) {
            val menus = s.getMenusWithId(commandId)
            if (menus != null && menus.size > 0) {
                menusWithId.addAll(menus)
            }
        }
        return menusWithId
    }

    fun getEntryForNameSpace(xmlns: String?): FormEntry? {
        for (suite in platform!!.getInstalledSuites()) {
            val en = suite.getEntries().elements()
            while (en.hasMoreElements()) {
                val suiteEntry = en.nextElement()
                if (suiteEntry is FormEntry) {
                    if (suiteEntry.getXFormNamespace() == xmlns) {
                        return suiteEntry
                    }
                }
            }
        }
        return null
    }

    /**
     * Retrieve the single entry for the given command ID.
     *
     * @return The entry identified by the command or null if there is no entry with the given command.
     */
    fun getEntryForCommand(commandID: String?): Entry? {
        return if (commandID == null) null else getPlatform().getEntry(commandID)
    }

    private fun getStillValidEntriesFromMenu(menu: Menu): Vector<Entry> {
        val globalEntryMap = platform!!.getCommandToEntryMap()
        val stillValid = Vector<Entry>()
        for (cmd in menu.getCommandIds()) {
            val e = globalEntryMap[cmd]
                ?: throw RuntimeException("No entry found for menu command [$cmd]")
            stillValid.addElement(e)
        }
        return stillValid
    }

    fun getData(): OrderedHashtable<String, String> {
        return collectedDatums
    }

    open fun getPlatform(): CommCarePlatform {
        return this.platform!!
    }

    /**
     * Based on the current state of the session, determine what information is needed next to
     * proceed
     *
     * @return One of the session SessionFrame.STATE_* strings, or null if
     * the session does not need anything else to proceed
     */
    open fun getNeededData(evalContext: EvaluationContext): String? {
        if (currentCmd == null) {
            return SessionFrame.STATE_COMMAND_ID
        }

        val entriesForCurrentCommand = getEntriesForCommand(currentCmd)
        val needDatum = getDataNeededByAllEntries(entriesForCurrentCommand)

        if (needDatum != null) {
            return needDatum
        } else if (entriesForCurrentCommand.isEmpty()) {
            // No entries available directly within the current command, so we must need to select another menu
            return SessionFrame.STATE_COMMAND_ID
        } else if (entriesForCurrentCommand.size == 1) {
            val entry = getEntryForCommand(currentCmd)
            if (entry == null) {
                // command doesn't reference an entry directly so the user must still select one
                return SessionFrame.STATE_COMMAND_ID
            } else if (entry.getPostRequest() != null
                && getCurrentFrameStepExtra(EXTRA_POST_SUCCESS) == null
                && entry.getPostRequest()!!.isRelevant(evalContext)
            ) {
                return SessionFrame.STATE_SYNC_REQUEST
            } else {
                return null
            }
        } else {
            // the only other thing we can need is a form command. If there's
            // still more than one applicable entry, we need to keep going
            return SessionFrame.STATE_COMMAND_ID
        }
    }

    /**
     * Checks that all entries have the same id for their first required data,
     * and if so, returns the data's associated session state. Otherwise,
     * returns null.
     */
    private fun getDataNeededByAllEntries(entries: Vector<Entry>): String? {
        var datumNeededByAllEntriesSoFar: String? = null
        var neededDatumId: String? = null
        for (e in entries) {
            val datumNeededForThisEntry =
                getFirstMissingDatum(collectedDatums, e.getSessionDataReqs())
            if (datumNeededForThisEntry != null) {
                if (neededDatumId == null) {
                    neededDatumId = datumNeededForThisEntry.getDataId()
                    if (datumNeededForThisEntry is MultiSelectEntityDatum) {
                        datumNeededByAllEntriesSoFar = SessionFrame.STATE_MULTIPLE_DATUM_VAL
                    } else if (datumNeededForThisEntry is EntityDatum) {
                        datumNeededByAllEntriesSoFar = SessionFrame.STATE_DATUM_VAL
                    } else if (datumNeededForThisEntry is ComputedDatum) {
                        datumNeededByAllEntriesSoFar = SessionFrame.STATE_DATUM_COMPUTED
                    } else if (datumNeededForThisEntry is RemoteQueryDatum) {
                        datumNeededByAllEntriesSoFar = SessionFrame.STATE_QUERY_REQUEST
                    }
                } else if (neededDatumId != datumNeededForThisEntry.getDataId()) {
                    // data needed from the first entry isn't consistent with
                    // the current entry
                    return null
                }
            } else {
                // we don't need any data, or the first data needed isn't
                // consistent across entries
                return null
            }
        }

        return datumNeededByAllEntriesSoFar
    }

    fun getHeaderTitles(): Array<String?> {
        val menus = Hashtable<String, String>()

        for (s in platform!!.getInstalledSuites()) {
            for (m in s.getMenus()) {
                menus.put(m.getId(), m.getName()?.evaluate())
            }
        }

        val steps = frame.getSteps()
        val returnVal = arrayOfNulls<String>(steps.size)

        val entries = platform.getCommandToEntryMap()
        var i = 0
        for (step in steps) {
            if (SessionFrame.STATE_COMMAND_ID == step.getType()) {
                // Menu or form.
                if (menus.containsKey(step.getId())) {
                    returnVal[i] = menus[step.getId()]
                } else if (entries.containsKey(step.getId())) {
                    returnVal[i] = entries[step.getId()]!!.getText()?.evaluate()
                }
            } else if (SessionFrame.STATE_DATUM_VAL == step.getType()) {
                // TODO: Grab the name of the case
            } else if (SessionFrame.STATE_DATUM_COMPUTED == step.getType()) {
                // Nothing to do here
            }

            if (returnVal[i] != null) {
                // Menus contain a potential argument listing where that value is on the screen,
                // clear it out if it exists
                returnVal[i] = Localizer.processArguments(returnVal[i]!!, arrayOf("")).trim()
            }

            ++i
        }

        return returnVal
    }

    /**
     * @return The next relevant datum for the current entry. Requires there to be
     * an entry on the stack
     */
    fun getNeededDatum(): SessionDatum? {
        val entries = getEntriesForCommand(getCommand())
        if (entries.isEmpty()) {
            throw IllegalStateException("The current session has no valid entry")
        }
        return getNeededDatum(entries.firstElement())
    }

    /**
     * @param entry An entry which is consistent as a step on the stack
     * @return A session datum definition if one is pending. Null otherwise.
     */
    fun getNeededDatum(entry: Entry): SessionDatum? {
        return getFirstMissingDatum(collectedDatums, entry.getSessionDataReqs())
    }

    /**
     * Return the first SessionDatum that is in allDatumsNeeded, but is not represented in
     * datumsCollectedSoFar
     */
    private fun getFirstMissingDatum(
        datumsCollectedSoFar: OrderedHashtable<*, *>,
        allDatumsNeeded: Vector<SessionDatum>?
    ): SessionDatum? {
        if (allDatumsNeeded == null) return null
        for (datum in allDatumsNeeded) {
            if (!datumsCollectedSoFar.containsKey(datum.getDataId())) {
                return datum
            }
        }
        return null
    }

    fun getDetail(id: String?): Detail? {
        for (s in platform!!.getInstalledSuites()) {
            val d = s.getDetail(id)
            if (d != null) {
                return d
            }
        }
        return null
    }

    /**
     * When StackFrameSteps are parsed, those that are "datum" operations will be marked as type
     * "unknown". When we encounter a StackFrameStep of unknown type at runtime, we need to
     * determine whether it should be interpreted as STATE_DATUM_COMPUTED, STATE_COMMAND_ID,
     * or STATE_DATUM_VAL. This primarily affects the behavior of stepBack().
     *
     * The logic being employed is: If there is a previous step on the stack whose entries would
     * have added this command, interpret it as a command. If there is an EntityDatum that
     * would have added this as an entity selection, interpret this as a datum_val.
     * Otherwise, interpret it as a computed datum.
     */
    private fun guessUnknownType(popped: StackFrameStep): String {
        val poppedId = popped.getId()
        for (stackFrameStep in frame.getSteps()) {
            val commandId = stackFrameStep.getId()
            val entries = getEntriesForCommand(commandId)
            for (entry in entries) {
                val childCommand = entry.getCommandId()
                if (childCommand == poppedId) {
                    return SessionFrame.STATE_COMMAND_ID
                }
                val data = entry.getSessionDataReqs() ?: continue
                for (datum in data) {
                    if (datum is EntityDatum &&
                        datum.getDataId() == poppedId
                    ) {
                        return SessionFrame.STATE_DATUM_VAL
                    }
                }
            }
        }
        return SessionFrame.STATE_DATUM_COMPUTED
    }

    /**
     * @return true if the current state of the session is such that we are NOT waiting for
     * user-provided input, and false otherwise
     */
    private fun shouldPopNext(evalContext: EvaluationContext): Boolean {
        val neededData = getNeededData(evalContext)
        val poppedStep = popped
        val poppedType = if (poppedStep == null) "" else poppedStep.getType()

        if (neededData == null ||
            SessionFrame.STATE_DATUM_COMPUTED == neededData ||
            SessionFrame.STATE_DATUM_COMPUTED == poppedType ||
            topStepIsMark()
        ) {
            return true
        }

        if (SessionFrame.STATE_DATUM_VAL == neededData) {
            val neededDatum = getNeededDatum()
            if (neededDatum is EntityDatum) {
                if (neededDatum.getCurrentAutoselectableCase(evalContext) != null
                    && neededDatum.getLongDetail() == null
                ) {
                    // If the next needed datum is an entity for which there would be an
                    // auto-selected case in the current eval context, AND there is no case detail,
                    // then we want to step back over this
                    return true
                }
            }
        }

        return SessionFrame.STATE_UNKNOWN == poppedType
                && poppedStep != null && guessUnknownType(poppedStep) == SessionFrame.STATE_DATUM_COMPUTED
    }

    fun stepBack(evalContext: EvaluationContext) {
        // Pop the first thing off of the stack frame, no matter what
        popStepInCurrentSessionFrame()

        // Keep popping things off until the value of needed data indicates that we are back to
        // somewhere where we are waiting for user-provided input
        while (shouldPopNext(evalContext)) {
            popStepInCurrentSessionFrame()
        }
    }

    private fun topStepIsMark(): Boolean {
        return frame.getSteps().isNotEmpty()
                && SessionFrame.STATE_MARK == frame.getSteps().lastElement().getType()
    }

    fun popStep(evalContext: EvaluationContext) {
        popStepInCurrentSessionFrame()

        while (getNeededData(evalContext) == null
            || topStepIsMark()
        ) {
            popStepInCurrentSessionFrame()
        }
    }

    private fun popStepInCurrentSessionFrame() {
        val recentPop = frame.popStep()

        // TODO: Check the "base state" of the frame after popping to see if we invalidated the stack
        syncState()
        popped = recentPop
    }

    fun getSmartLinkRedirect(): String? {
        return smartLinkRedirect
    }

    fun setSmartLinkRedirect(url: String?) {
        smartLinkRedirect = url
    }

    fun setEntityDatum(datum: SessionDatum, value: String?) {
        val datumType = if (datum is MultiSelectEntityDatum) SessionFrame.STATE_MULTIPLE_DATUM_VAL
        else SessionFrame.STATE_DATUM_VAL
        setDatum(datumType, datum.getDataId()!!, value)
    }

    fun setEntityDatum(keyId: String, value: String?) {
        setDatum(SessionFrame.STATE_DATUM_VAL, keyId, value)
    }

    fun setDatum(type: String, keyId: String, value: String?) {
        frame.pushStep(StackFrameStep(type, keyId, value))
        syncState()
    }

    fun setDatum(type: String, keyId: String, value: String?, source: ExternalDataInstanceSource?) {
        val step = StackFrameStep(type, keyId, value)
        if (source != null) {
            step.addDataInstanceSource(source)
        }
        frame.pushStep(step)
        syncState()
    }

    /**
     * Set a (xml) data instance as the result to a session query datum.
     * The instance is available in session's evaluation context until the corresponding query frame is removed
     */
    fun setQueryDatum(queryResultInstance: ExternalDataInstance, vararg extras: ExternalDataInstance) {
        val datum = getNeededDatum()
        if (datum is RemoteQueryDatum) {
            val step = StackFrameStep(
                SessionFrame.STATE_QUERY_REQUEST, datum.getDataId()!!, datum.getValue()
            )
            queryResultInstance.getSource()?.let { step.addDataInstanceSource(it) }
            for (instance in extras) {
                instance.getSource()?.let { step.addDataInstanceSource(it) }
            }
            frame.pushStep(step)
            syncState()
        } else {
            throw RuntimeException("Trying to set query successful when one isn't needed.")
        }
    }

    @Throws(XPathException::class)
    fun setComputedDatum(ec: EvaluationContext) {
        val datum = getNeededDatum()!!
        val form: org.javarosa.xpath.expr.XPathExpression
        try {
            form = XPathParseTool.parseXPath(datum.getValue()!!)!!
        } catch (e: XPathSyntaxException) {
            e.printStackTrace()
            throw RuntimeException(e.message)
        }
        if (datum is FormIdDatum) {
            setXmlns(FunctionUtils.toString(form.eval(ec)))
            setEntityDatum("", "awful")
        } else if (datum is ComputedDatum) {
            setEntityDatum(datum, FunctionUtils.toString(form.eval(ec)))
        }
    }

    fun setXmlns(xmlns: String?) {
        frame.pushStep(StackFrameStep(SessionFrame.STATE_FORM_XMLNS, xmlns, null))
        syncState()
    }

    fun setCommand(commandId: String) {
        frame.pushStep(StackFrameStep(SessionFrame.STATE_COMMAND_ID, commandId, null))
        syncState()
    }

    fun syncState() {
        this.collectedDatums.clear()
        this.currentCmd = null
        this.currentXmlns = null
        this.popped = null

        for (step in frame.getSteps()) {
            if (SessionFrame.isEntitySelectionDatum(step.getType()) ||
                SessionFrame.STATE_DATUM_COMPUTED == step.getType() ||
                SessionFrame.STATE_UNKNOWN == step.getType() &&
                (guessUnknownType(step) == SessionFrame.STATE_DATUM_COMPUTED
                        || guessUnknownType(step) == SessionFrame.STATE_DATUM_VAL)
            ) {
                val key = step.getId()
                val value = step.getValue()
                if (key != null && value != null) {
                    collectedDatums.put(key, value)
                }
            } else if (SessionFrame.STATE_QUERY_REQUEST == step.getType()) {
                collectedDatums.put(step.getId()!!, step.getValue()!!)
            } else if (SessionFrame.STATE_COMMAND_ID == step.getType()) {
                this.currentCmd = step.getId()
            } else if (SessionFrame.STATE_FORM_XMLNS == step.getType()) {
                this.currentXmlns = step.getId()
            }
        }
    }

    fun getPoppedStep(): StackFrameStep? {
        return popped
    }

    fun getForm(): String? {
        if (this.currentXmlns != null) {
            return this.currentXmlns
        }
        val command = getCommand() ?: return null

        val e = platform!!.getCommandToEntryMap()[command]
        return e!!.getXFormNamespace()
    }

    fun getCommand(): String? {
        return this.currentCmd
    }

    /**
     * Clear the current stack frame and release any pending
     * stack frames (completely clearing the session)
     */
    fun clearAllState() {
        frame = SessionFrame()
        frameStack.removeAllElements()
        syncState()
    }

    /**
     * Retrieve an evaluation context in which to evaluate expressions in the
     * current session state
     *
     * @param iif the instance initializer for the current platform
     * @return Evaluation context for current session state
     */
    fun getEvaluationContext(iif: InstanceInitializationFactory): EvaluationContext {
        return this.getEvaluationContext(iif, getCommand(), null)
    }

    /**
     * Retrieve an evaluation context in which to evaluate expressions in the context of a given
     * command in the installed app
     *
     * @param iif the instance initializer for the current platform
     * @return Evaluation context for a command in the installed app
     */
    fun getEvaluationContext(
        iif: InstanceInitializationFactory,
        command: String?,
        instancesToInclude: Set<String>?
    ): EvaluationContext {
        if (command == null) {
            return EvaluationContext(null)
        }
        val entries = getEntriesForCommand(command)
        val menus = getMenusForCommand(command)

        var entry: Entry? = null
        val instancesInScope = Hashtable<String, DataInstance<*>>()
        var menuInstances: Hashtable<String, DataInstance<*>>? = null
        var entryInstances: Hashtable<String, DataInstance<*>>? = null

        if (entries.isNotEmpty()) {
            entry = entries.elementAt(0)
            if (entry != null) {
                entryInstances = entry.getInstances(instancesToInclude)
            }
            if (entryInstances != null) {
                instancesInScope.putAll(entryInstances)
            }
        }

        for (menu in menus) {
            if (menu != null) {
                menuInstances = menu.getInstances(instancesToInclude)
            }
            if (menuInstances != null) {
                instancesInScope.putAll(menuInstances)
            }
        }

        val en = instancesInScope.keys()
        while (en.hasMoreElements()) {
            val key = en.nextElement() as String
            instancesInScope.put(key, instancesInScope[key]!!.initialize(iif, key))
        }
        addInstancesFromFrame(instancesInScope, iif)

        return EvaluationContext(null, instancesInScope)
    }

    private fun addInstancesFromFrame(
        instanceMap: Hashtable<String, DataInstance<*>>,
        iif: InstanceInitializationFactory
    ) {
        for (step in frame.getSteps()) {
            instanceMap.putAll(step.getInstances(iif))
        }
    }

    /**
     * @return A copy of the current frame with UNKNOWN types evaluated to their best guess
     */
    fun getFrame(): SessionFrame {
        val copyFrame = SessionFrame(frame)
        for (step in copyFrame.getSteps()) {
            if (step.getType() == SessionFrame.STATE_UNKNOWN) {
                step.setType(guessUnknownType(step))
            }
        }
        return copyFrame
    }

    /**
     * Executes a set of stack operations against the current session environment.
     *
     * The context data and session data provided will consistently match the live frame
     * when the operations began executing, although frame operations will be executed
     * against the most recent frame. (IE: If a new frame is pushed here, xpath expressions
     * calculated within it will be evaluated against the starting, but push actions
     * will happen against the newly pushed frame)
     *
     * @return True if stack ops triggered a rewind, used for determining stack clean-up logic
     */
    fun executeStackOperations(ops: Vector<StackOperation>, ec: EvaluationContext): Boolean {
        return executeStackOperations(ops, ec, StackObserver())
    }

    fun executeStackOperations(
        ops: Vector<StackOperation>, ec: EvaluationContext, observer: StackObserver
    ): Boolean {
        // The on deck frame is the frame that is the target of operations that execute
        // as part of this stack update. If at the end of the stack ops the frame on deck
        // doesn't match the current (living) frame, it will become the current frame
        val onDeck = frame

        var didRewind = false
        for (op in ops) {
            if (!processStackOp(op, ec, observer)) {
                // rewind occurred, stop processing further ops.
                didRewind = true
                break
            }
        }

        popOrSync(onDeck, didRewind, observer)
        return didRewind
    }

    /**
     * @return false if current frame was rewound
     */
    private fun processStackOp(op: StackOperation, ec: EvaluationContext, observer: StackObserver): Boolean {
        when (op.getOp()) {
            StackOperation.OPERATION_CREATE -> {
                val createdFrame = SessionFrame()
                createFrame(createdFrame, op, ec, observer)
            }
            StackOperation.OPERATION_PUSH -> {
                if (!performPush(op, ec, observer)) {
                    return false
                }
            }
            StackOperation.OPERATION_CLEAR -> performClearOperation(op, ec, observer)
            else -> throw RuntimeException("Undefined stack operation: " + op.getOp())
        }

        return true
    }

    private fun createFrame(
        createdFrame: SessionFrame,
        op: StackOperation, ec: EvaluationContext, observer: StackObserver
    ) {
        if (op.isOperationTriggered(ec)) {
            // create has its own event so don't pass through the active observer
            performPushInner(op, createdFrame, ec, StackObserver())
            pushNewFrame(createdFrame, observer)
        }
    }

    /**
     * @return false if push was terminated early by a 'rewind' or 'jump'
     */
    private fun performPushInner(
        op: StackOperation, frame: SessionFrame, ec: EvaluationContext,
        observer: StackObserver
    ): Boolean {
        for (step in op.getStackFrameSteps()) {
            if (SessionFrame.STATE_REWIND == step.getType()) {
                if (frame.rewindToMarkAndSet(step, ec, observer)) {
                    return false
                }
                // if no mark is found ignore the rewind and continue
            } else if (SessionFrame.STATE_SMART_LINK == step.getType()) {
                val url = step.getExtra("url") as Text
                smartLinkRedirect = url.evaluate(ec)
                observer.smartLinkSet(smartLinkRedirect!!)
                return false
            } else {
                pushFrameStep(step, frame, ec, observer)
            }
        }
        return true
    }

    private fun pushFrameStep(
        step: StackFrameStep, frame: SessionFrame, ec: EvaluationContext,
        observer: StackObserver
    ) {
        var neededDatum: SessionDatum? = null
        if (SessionFrame.STATE_MARK == step.getType()) {
            neededDatum = getNeededDatumForFrame(this, frame)
        }
        val pushStep = step.defineStep(ec, neededDatum)
        frame.pushStep(pushStep)
        observer.pushed(pushStep)
    }

    /**
     * @return false if push was terminated early by a 'rewind'
     */
    private fun performPush(op: StackOperation, ec: EvaluationContext, observer: StackObserver): Boolean {
        if (op.isOperationTriggered(ec)) {
            return performPushInner(op, frame, ec, observer)
        }
        return true
    }

    private fun pushNewFrame(matchingFrame: SessionFrame, observer: StackObserver) {
        // Before we can push a frame onto the stack, we need to
        // make sure the stack is clean. This means that if the
        // current frame has a snapshot, we've gotta make sure
        // the existing frames are still valid.

        // TODO: We might want to handle this differently in the future,
        // so that we can account for the invalidated frames in the ui
        // somehow.
        cleanStack()

        frameStack.push(matchingFrame)
        observer.pushed(matchingFrame)
    }

    private fun performClearOperation(
        op: StackOperation,
        ec: EvaluationContext, observer: StackObserver
    ) {
        if (op.isOperationTriggered(ec)) {
            frameStack.removeElement(frame)
            observer.dropped(frame)
        }
    }

    private fun popOrSync(onDeck: SessionFrame, didRewind: Boolean, observer: StackObserver): Boolean {
        if (!frame.isDead() && frame !== onDeck) {
            // If the current frame isn't dead, and isn't on deck, that means we've pushed
            // in new frames and need to load up the correct one

            if (!finishAndPop(didRewind, observer)) {
                // Somehow we didn't end up with any frames after that? that's incredibly weird, I guess
                // we should just start over.
                clearAllState()
            }
            return true
        } else {
            syncState()
            return false
        }
    }

    /**
     * Checks to see if the current frame has a clean snapshot. If
     * not, clears the stack and the snapshot (since the snapshot can
     * only be relevant to the existing frames)
     */
    private fun cleanStack() {
        // See whether the current frame was incompatible with its start
        // state.
        if (frame.isSnapshotIncompatible()) {
            // If it is, our frames can no longer make sense.
            frameStack.removeAllElements()
            frame.clearSnapshot()
        }
    }

    /**
     * Called after a session has been completed. Executes any pending stack operations
     * from the current session, completes the session, and pops the top of any pending
     * frames into execution.
     *
     * @return True if there was a pending frame and it has been
     * popped into the current session. False if the stack was empty
     * and the session is over.
     */
    fun finishExecuteAndPop(ec: EvaluationContext): Boolean {
        return finishExecuteAndPop(ec, StackObserver())
    }

    fun finishExecuteAndPop(ec: EvaluationContext, observer: StackObserver): Boolean {
        val ops = getCurrentEntry().getPostEntrySessionOperations()

        // Let the session know that the current frame shouldn't work its way back onto the stack
        markCurrentFrameForDeath()

        // First, see if we have operations to run
        var didRewind = false
        if (ops != null && ops.size > 0) {
            didRewind = executeStackOperations(ops, ec, observer)
        }
        return finishAndPop(didRewind, observer)
    }

    /**
     * Complete the current session (and perform any cleanup), then
     * check the stack for any pending frames, and load the top one
     * into the current session if so.
     *
     * @param didRewind True if rewind occurred during stack pop.
     *                  Helps determine post-pop stack cleanup logic
     * @return True if there was a pending frame and it has been
     * popped into the current session. False if the stack was empty
     * and the session is over.
     */
    private fun finishAndPop(didRewind: Boolean, observer: StackObserver): Boolean {
        cleanStack()

        if (frameStack.empty()) {
            if (!didRewind) {
                observer.dropped(frame)
            }
            return didRewind
        } else {
            observer.dropped(frame)
            frame = frameStack.pop()
            // Ok, so if _after_ popping from the stack, we still have
            // stack members, we need to be careful about making sure
            // that they won't get triggered if we abandon the current
            // frame
            if (frameStack.isNotEmpty()) {
                frame.captureSnapshot()
            }

            syncState()
            return true
        }
    }

    /**
     * Retrieve the single valid entry for the current session, should be called only
     * when the current request is fully built
     *
     * @return The unique valid entry built on this session. Will throw an exception if there isn't
     * a unique entry.
     */
    fun getCurrentEntry(): Entry {
        val e = getEntriesForCommand(getCommand())
        if (e.size > 1) {
            throw IllegalStateException("The current session does not contain a single valid entry")
        }
        if (e.isEmpty()) {
            throw IllegalStateException("The current session has no valid entry")
        }
        return e.elementAt(0)
    }

    /**
     * Retrieves a valid datum definition in the current session's history
     * which contains a selector for the datum Id provided.
     *
     * Can be used to resolve the context about an item that
     * has been selected in this session.
     *
     * @param datumId The ID of a session datum in the session history
     * @return An Entry object which contains a selector for that datum
     * which is in this session history
     */
    fun findDatumDefinition(datumId: String): EntityDatum? {
        // We're performing a walk down the entities in this session here,
        // we should likely generalize this to make it easier to do it for other
        // operations

        val steps = frame.getSteps()

        var stepId = -1
        // walk to our datum
        for (i in 0 until steps.size) {
            val step = steps.elementAt(i)
            if (step.getId() == datumId && stepIsOfType(step, SessionFrame.STATE_DATUM_VAL)) {
                stepId = i
                break
            }
        }
        if (stepId == -1) {
            System.out.println("I don't think this should be possible...")
            return null
        }

        // ok, so now we have our step, we want to walk backwards until we find the entity
        // associated with our ID
        for (i in stepId downTo 0) {
            if (steps.elementAt(i).getType() == SessionFrame.STATE_COMMAND_ID) {
                val entries = this.getEntriesForCommand(steps.elementAt(i).getId())

                // TODO: Don't we know the right entry? What if our last command is an actual entry?
                for (entry in entries) {
                    for (datum in (entry.getSessionDataReqs() ?: Vector())) {
                        if (datum.getDataId() == datumId && datum is EntityDatum) {
                            return datum
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * @return true if the given step is either explicitly of the given type, or if it is of
     * unknown type and guessUnknownType() returns the given type
     */
    private fun stepIsOfType(step: StackFrameStep, desiredType: String): Boolean {
        return desiredType == step.getType() ||
                (SessionFrame.STATE_UNKNOWN == step.getType()
                        && desiredType == guessUnknownType(step))
    }

    private fun markCurrentFrameForDeath() {
        frame.kill()
    }

    /**
     * Does the command only have a view entry, and no other actions available
     * to take?
     */
    fun isViewCommand(command: String?): Boolean {
        val entries = this.getEntriesForCommand(command)
        return entries.size == 1 && entries.elementAt(0).isView()
    }

    fun isRemoteRequestCommand(command: String?): Boolean {
        val entries = this.getEntriesForCommand(command)
        return entries.size == 1 && entries.elementAt(0).isRemoteRequest()
    }

    fun addExtraToCurrentFrameStep(key: String, value: Any) {
        frame.addExtraTopStep(key, value)
    }

    fun removeExtraFromCurrentFrameStep(key: String) {
        frame.removeExtraTopStep(key)
    }

    /**
     * Get the 'extra' value for the given key.
     * This method only supports keys that have a single value. For keys with multiple values
     * use `getCurrentFrameStepExtras().get(key)` which returns a Collection of the values.
     */
    fun getCurrentFrameStepExtra(key: String): Any? {
        val topStep = frame.getTopStep()
        if (topStep != null) {
            return topStep.getExtra(key)
        }
        return null
    }

    fun getCurrentFrameStepExtras(): ListMultimap<String, Any>? {
        val topStep = frame.getTopStep()
        if (topStep != null) {
            return topStep.getExtras()
        }
        return null
    }

    fun serializeSessionState(outputStream: DataOutputStream) {
        frame.writeExternal(outputStream)
        ExtUtil.write(outputStream, ExtWrapList(frameStack))
    }


    companion object {
        /**
         * Builds a session by restoring a serialized SessionFrame and syncing from that.
         * Doesn't support restoring the frame stack
         */
        @JvmStatic
        @Throws(DeserializationException::class, IOException::class)
        fun restoreSessionFromStream(
            ccPlatform: CommCarePlatform,
            inputStream: DataInputStream
        ): CommCareSession {
            val restoredFrame = SessionFrame()
            restoredFrame.readExternal(inputStream, ExtUtil.defaultPrototypes())

            val restoredSession = CommCareSession(ccPlatform)
            restoredSession.frame = restoredFrame
            @Suppress("UNCHECKED_CAST")
            val frames = ExtUtil.read(inputStream, ExtWrapList(SessionFrame::class.java), null) as Vector<SessionFrame>
            val stackFrames = Stack<SessionFrame>()
            while (frames.isNotEmpty()) {
                val lastElement = frames.lastElement()
                frames.remove(lastElement)
                stackFrames.push(lastElement)
            }
            restoredSession.frameStack = stackFrames
            restoredSession.syncState()
            return restoredSession
        }

        private fun getNeededDatumForFrame(
            session: CommCareSession,
            targetFrame: SessionFrame
        ): SessionDatum? {
            val sessionCopy = CommCareSession(session)
            sessionCopy.frame = targetFrame
            sessionCopy.syncState()
            return sessionCopy.getNeededDatum()
        }
    }
}
