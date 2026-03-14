package org.commcare.backend.session.test

import org.commcare.modern.util.Pair
import org.commcare.session.StackObserver
import org.commcare.test.utilities.MockApp
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class StackObserverTest {

    @Test
    fun stackObserverTest() {
        val mockApp = MockApp("/stack-frame-copy-app/")
        val session = mockApp.getSession()

        session.setCommand("child-visit")
        session.setEntityDatum("mother_case_1", "nancy")

        val shortDetail = session.getPlatform()!!.getDetail("case-list")!!
        val action = shortDetail.getCustomActions(session.getEvaluationContext())[0]

        val observer = StackObserver()
        session.executeStackOperations(action.getStackOperations()!!, session.getEvaluationContext(), observer)

        assertEvents(
            observer,
            StackObserver.StepEvent::class.java, StackObserver.EventType.PUSHED,
            StackObserver.StepEvent::class.java, StackObserver.EventType.PUSHED
        )

        observer.reset()
        session.finishExecuteAndPop(session.getEvaluationContext(), observer)

        assertEvents(
            observer,
            StackObserver.StepEvent::class.java, StackObserver.EventType.DROPPED,
            StackObserver.StepEvent::class.java, StackObserver.EventType.PUSHED
        )

        observer.reset()
        session.finishExecuteAndPop(session.getEvaluationContext(), observer)
        assertEvents(observer, StackObserver.FrameEvent::class.java, StackObserver.EventType.DROPPED)
    }

    private fun assertEvents(
        observer: StackObserver,
        event1: Class<out StackObserver.StackEvent>, type1: StackObserver.EventType
    ) {
        val expected = arrayOf(Pair(event1, type1))
        assertEvents(observer, expected)
    }

    private fun assertEvents(
        observer: StackObserver,
        event1: Class<out StackObserver.StackEvent>, type1: StackObserver.EventType,
        event2: Class<out StackObserver.StackEvent>, type2: StackObserver.EventType
    ) {
        val expected = arrayOf(Pair(event1, type1), Pair(event2, type2))
        assertEvents(observer, expected)
    }

    private fun assertEvents(observer: StackObserver, expected: Array<out Any>) {
        val actual = observer.events.map { e -> Pair(e.javaClass, e.getType()) }.toTypedArray()
        assertArrayEquals(actual, expected)
    }
}
