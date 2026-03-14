package org.commcare.cases.util.test

import org.commcare.cases.model.Case
import org.commcare.cases.model.CaseIndex
import org.commcare.cases.util.CasePurgeFilter
import org.javarosa.core.services.storage.IStorageIterator
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.services.storage.util.DummyIndexedStorageUtility
import org.javarosa.core.util.DataUtil
import org.javarosa.core.util.externalizable.LivePrototypeFactory
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class CasePurgeFilterTests {

    private lateinit var a: Case
    private lateinit var b: Case
    private lateinit var c: Case
    private lateinit var d: Case
    private lateinit var e: Case
    private lateinit var storage: DummyIndexedStorageUtility<Case>
    private lateinit var owner: String
    private lateinit var groupOwner: String
    private lateinit var groupOwned: ArrayList<String>
    private lateinit var userOwned: ArrayList<String>

    @Before
    @Throws(Exception::class)
    fun setUp() {
        storage = DummyIndexedStorageUtility(Case::class.java, LivePrototypeFactory())

        owner = "owner"
        groupOwner = "groupowned"

        userOwned = ArrayList()
        userOwned.add(owner)

        groupOwned = ArrayList()
        groupOwned.add(owner)
        groupOwned.add(groupOwner)

        a = Case("a", "a")
        a.setCaseId("a")
        a.setUserId(owner)
        b = Case("b", "b")
        b.setCaseId("b")
        b.setUserId(owner)
        c = Case("c", "c")
        c.setCaseId("c")
        c.setUserId(owner)
        d = Case("d", "d")
        d.setCaseId("d")
        d.setUserId(owner)
        e = Case("e", "e")
        e.setCaseId("e")
        e.setUserId(groupOwner)
    }

    @Test
    fun testGroupOwned() {
        try {
            storage.write(a)
            storage.write(b)
            storage.write(c)
            storage.write(d)
            storage.write(e)

            val present = intArrayOf(a.getID(), c.getID(), d.getID(), b.getID(), e.getID())
            val toRemove = intArrayOf()

            val removed = storage.removeAll(CasePurgeFilter(storage, groupOwned))
            testOutcome(storage, present, toRemove)
            testRemovedClaim(removed, toRemove)
        } catch (e: Exception) {
            e.printStackTrace()
            fail("Unexpected exception " + e.message)
        }
    }

    @Test
    fun testDoubleIndex() {
        a.setClosed(true)

        e.setIndex(CaseIndex("a_c", "a", a.getCaseId(), CaseIndex.RELATIONSHIP_CHILD))
        e.setIndex(CaseIndex("a_e", "a", a.getCaseId(), CaseIndex.RELATIONSHIP_EXTENSION))

        try {
            storage.write(a)
            storage.write(b)
            storage.write(c)
            storage.write(d)
            storage.write(e)

            val present = intArrayOf(a.getID(), e.getID(), b.getID(), c.getID(), d.getID())
            val toRemove = intArrayOf()

            val removed = storage.removeAll(CasePurgeFilter(storage))
            testOutcome(storage, present, toRemove)
            testRemovedClaim(removed, toRemove)
        } catch (e: Exception) {
            e.printStackTrace()
            fail("Unexpected exception " + e.message)
        }
    }

    fun testOutcome(storage: IStorageUtilityIndexed<Case>, p: IntArray, g: IntArray) {
        val present = atv(p)
        val gone = atv(g)

        val iterator = storage.iterate()
        while (iterator.hasMore()) {
            val id = iterator.peekID()
            present.remove(id)
            if (gone.contains(id)) {
                fail("Case: " + iterator.nextRecord().getCaseId() + " not purged")
            }
            iterator.nextID()
        }
        if (present.size > 0) {
            fail("No case with index " + present[0] + " in testdb")
        }
    }

    private fun testRemovedClaim(removed: ArrayList<Int>, toRemove: IntArray) {
        if (removed.size != toRemove.size) {
            fail("caseStorage purge returned incorrect size of returned items")
        }

        for (i in toRemove.indices) {
            removed.remove(DataUtil.integer(toRemove[i]))
        }
        if (removed.size > 0) {
            fail("caseStorage purge returned incorrect set of removed items")
        }
    }

    private fun atv(a: IntArray): ArrayList<Int> {
        val ret = ArrayList<Int>(a.size)
        for (i in a.indices) {
            ret.add(DataUtil.integer(a[i]))
        }
        return ret
    }
}
