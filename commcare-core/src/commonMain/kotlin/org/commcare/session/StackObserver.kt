package org.commcare.session

import org.commcare.suite.model.StackFrameStep

/**
 * Observer class that accumulates events resulting from stack operations
 */
open class StackObserver {
    enum class EventType {
        PUSHED, DROPPED, SMART_LINK_SET
    }

    abstract inner class StackEvent(private val _type: EventType) {

        fun getType(): EventType = _type

        abstract fun getSteps(): List<StackFrameStep>

        override fun toString(): String {
            return "<StackEvent type=$_type>"
        }
    }

    inner class FrameEvent(type: EventType, private val frame: SessionFrame) : StackEvent(type) {

        override fun getSteps(): List<StackFrameStep> {
            return frame.getSteps()
        }
    }

    inner class StepEvent : StackEvent {
        private val steps: List<StackFrameStep>

        constructor(type: EventType, step: StackFrameStep) : this(type, listOf(step))

        constructor(type: EventType, steps: List<StackFrameStep>) : super(type) {
            this.steps = steps
        }

        override fun getSteps(): List<StackFrameStep> {
            return this.steps
        }
    }

    internal inner class SmartLinkEvent(private val url: String) : StackEvent(EventType.SMART_LINK_SET) {

        override fun getSteps(): List<StackFrameStep> {
            return emptyList()
        }
    }

    var events: MutableList<StackEvent> = ArrayList()

    /**
     * Called when a new frame is pushed onto the stack
     */
    fun pushed(frame: SessionFrame) {
        events.add(FrameEvent(EventType.PUSHED, frame))
    }

    /**
     * Called when a frame is removed or cleared
     */
    fun dropped(frame: SessionFrame) {
        events.add(FrameEvent(EventType.DROPPED, frame))
    }

    /**
     * Step pushed onto the current frame
     */
    fun pushed(step: StackFrameStep) {
        events.add(StepEvent(EventType.PUSHED, step))
    }

    /**
     * Smart link set on the frame
     */
    fun smartLinkSet(url: String) {
        events.add(SmartLinkEvent(url))
    }

    /**
     * Steps were rewound
     */
    fun dropped(steps: List<StackFrameStep>) {
        events.add(StepEvent(EventType.DROPPED, steps))
    }

    fun getRemovedSteps(): List<StackFrameStep> {
        val removed: MutableList<StackFrameStep> = ArrayList()
        for (event in events) {
            when (event.getType()) {
                EventType.DROPPED -> removed.addAll(event.getSteps())
                else -> {}
            }
        }
        return removed
    }

    fun reset() {
        events = ArrayList()
    }
}
