package org.commcare.session

import org.commcare.suite.model.StackFrameStep
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Vector

/**
 * A Session Frame contains the actions that a user has taken while
 * navigating through a CommCare application. Each action is represented
 * as a StackFrameStep
 *
 * @author ctsims
 */
open class SessionFrame : Externalizable {

    private var steps: Vector<StackFrameStep> = Vector()
    private var snapshot: Vector<StackFrameStep> = Vector()

    /**
     * A Frame is dead if its execution path has finished and it shouldn't
     * be considered part of the stack
     */
    private var dead: Boolean = false

    /**
     * Create a new, un-id'd session frame
     */
    constructor()

    /**
     * Copy constructor
     */
    constructor(oldSessionFrame: SessionFrame) {
        for (step in oldSessionFrame.steps) {
            steps.addElement(StackFrameStep(step))
        }
        for (snapshotStep in oldSessionFrame.snapshot) {
            snapshot.addElement(StackFrameStep(snapshotStep))
        }
        this.dead = oldSessionFrame.dead
    }

    fun getSteps(): Vector<StackFrameStep> {
        return steps
    }

    fun popStep(): StackFrameStep? {
        var recentPop: StackFrameStep? = null

        if (steps.size > 0) {
            recentPop = steps.elementAt(steps.size - 1)
            steps.removeElementAt(steps.size - 1)
        }
        return recentPop
    }

    internal fun rewindToMarkAndSet(
        step: StackFrameStep, evalContext: EvaluationContext,
        observer: StackObserver
    ): Boolean {
        val markIndex = getLatestMarkPosition(steps)

        if (markIndex >= 0) {
            val markDatumId = steps[markIndex].getId()
            observer.dropped(steps.subList(markIndex, steps.size))
            steps = Vector(steps.subList(0, markIndex))
            if (step.getValue() != null) {
                val evaluatedStepValue = step.evaluateValue(evalContext)
                val rewindStep = StackFrameStep(STATE_UNKNOWN, markDatumId, evaluatedStepValue)
                steps.addElement(rewindStep)
                observer.pushed(rewindStep)
            }
            return true
        } else {
            return false
        }
    }

    fun pushStep(step: StackFrameStep) {
        steps.addElement(step)
    }

    /**
     * Requests that the frame capture an original snapshot of its state.
     * This snapshot can be referenced later to compare the eventual state
     * of the frame to an earlier point
     */
    @Synchronized
    fun captureSnapshot() {
        snapshot.removeAllElements()
        for (s in steps) {
            snapshot.addElement(s)
        }
    }

    /**
     * Determines whether the current frame state is incompatible with
     * a previously snapshotted frame state, if one exists. If no snapshot
     * exists, this method will return false.
     *
     * Compatibility is determined by checking that each step in the previous
     * snapshot is matched by an identical step in the current snapshot.
     */
    @Synchronized
    fun isSnapshotIncompatible(): Boolean {
        // No snapshot, can't be incompatible.
        if (snapshot.isEmpty()) {
            return false
        }

        if (snapshot.size > steps.size) {
            return true
        }

        // Go through each step in the snapshot
        for (i in 0 until snapshot.size) {
            if (snapshot.elementAt(i) != steps.elementAt(i)) {
                return true
            }
        }

        // If we didn't find anything wrong, we're good to go!
        return false
    }

    @Synchronized
    fun clearSnapshot() {
        this.snapshot.removeAllElements()
    }

    /**
     * @return Whether this frame is dead or not. Dead frames have finished their session
     * and can never again become part of the stack.
     */
    fun isDead(): Boolean {
        return dead
    }

    /**
     * Kill this frame, ensuring it will never return to the stack.
     */
    fun kill() {
        dead = true
    }

    @Synchronized
    fun addExtraTopStep(key: String, value: Any) {
        if (steps.isNotEmpty()) {
            val topStep = steps.elementAt(steps.size - 1)
            topStep.addExtra(key, value)
        }
    }

    @Synchronized
    fun removeExtraTopStep(key: String) {
        if (steps.isNotEmpty()) {
            val topStep = steps.elementAt(steps.size - 1)
            topStep.removeExtra(key)
        }
    }

    @Synchronized
    fun getTopStep(): StackFrameStep? {
        if (steps.isNotEmpty()) {
            return steps.elementAt(steps.size - 1)
        }
        return null
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        @Suppress("UNCHECKED_CAST")
        steps = ExtUtil.read(`in`, ExtWrapList(StackFrameStep::class.java), pf) as Vector<StackFrameStep>
        @Suppress("UNCHECKED_CAST")
        snapshot = ExtUtil.read(`in`, ExtWrapList(StackFrameStep::class.java), pf) as Vector<StackFrameStep>
        dead = ExtUtil.readBool(`in`)
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.write(out, ExtWrapList(steps))
        ExtUtil.write(out, ExtWrapList(snapshot))
        ExtUtil.writeBool(out, dead)
    }

    override fun toString(): String {
        val output = StringBuilder()

        if (steps.isNotEmpty()) {
            output.append("frame:\t")
            prettyPrintSteps(steps, output)
        }

        if (snapshot.isNotEmpty()) {
            output.append("\nsnapshot:\t")
            prettyPrintSteps(snapshot, output)
        }

        if (dead) {
            output.append("\n[DEAD]")
        }

        return output.toString()
    }

    private fun prettyPrintSteps(
        stepsToPrint: Vector<StackFrameStep>,
        stringBuilder: StringBuilder
    ) {
        if (stepsToPrint.isNotEmpty()) {
            // prevent trailing '/' by intercalating all but last element
            for (i in 0 until stepsToPrint.size - 1) {
                val step = stepsToPrint.elementAt(i)
                stringBuilder.append(step.toString()).append(" \\ ")
            }
            // add the last elem
            stringBuilder.append(stepsToPrint.lastElement())
        }
    }

    companion object {
        // region - Possible states of a SessionFrame

        /**
         * CommCare needs a Command (an entry, view, etc) to proceed. Generally sitting on a menu screen.
         */
        const val STATE_COMMAND_ID: String = "COMMAND_ID"

        /**
         * CommCare needs any piece of information coming from a datum val (other than a computed datum)
         */
        const val STATE_DATUM_VAL: String = "CASE_ID"

        /**
         * Similar to STATE_DATUM_VAL but allows for a reference to vector datum value to be stored
         * against it
         */
        const val STATE_MULTIPLE_DATUM_VAL: String = "SELECTED_ENTITIES"

        /**
         * Signifies that the frame should be rewound to the last MARK, setting the
         * MARK's datum id (which is the next needed datum at that point in the frame)
         * to the value carried in the rewind.
         */
        const val STATE_REWIND: String = "REWIND"

        /**
         * Delineates a rewind point. Contains a datum id, which corresponds to
         * the next needed datum at that point in the frame.
         */
        const val STATE_MARK: String = "MARK"

        /**
         * CommCare needs a computed xpath value to proceed
         */
        const val STATE_DATUM_COMPUTED: String = "COMPUTED_DATUM"

        /**
         * CommCare needs to make a synchronous server request
         */
        const val STATE_SYNC_REQUEST: String = "SYNC_REQUEST"

        /**
         * CommCare needs to make a query request to server
         */
        const val STATE_QUERY_REQUEST: String = "QUERY_REQUEST"

        /**
         * CommCare needs to jump to a specific location in this or another app.
         */
        const val STATE_SMART_LINK: String = "SMART_LINK"

        /**
         * Unknown at parse time - this could be a COMMAND or a COMPUTED, best
         * guess determined by the CommCareSession based on the current frame
         */
        const val STATE_UNKNOWN: String = "STATE_UNKNOWN"

        /**
         * CommCare needs the XMLNS of the form to be entered to proceed
         */
        const val STATE_FORM_XMLNS: String = "FORM_XMLNS"

        // endregion - states

        @JvmStatic
        fun isEntitySelectionDatum(datum: String?): Boolean {
            return STATE_DATUM_VAL == datum || STATE_MULTIPLE_DATUM_VAL == datum
        }

        private fun getLatestMarkPosition(steps: Vector<StackFrameStep>): Int {
            for (index in steps.size - 1 downTo 0) {
                if (STATE_MARK == steps[index].getType()) {
                    return index
                }
            }
            return -1
        }
    }
}
