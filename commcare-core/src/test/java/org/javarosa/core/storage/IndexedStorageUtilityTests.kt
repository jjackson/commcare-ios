package org.javarosa.core.storage

import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Tests for basic storage utility functions
 *
 * Created by ctsims on 9/22/2017.
 */
abstract class IndexedStorageUtilityTests {

    lateinit var storage: IStorageUtilityIndexed<Shoe>

    lateinit var nike: Shoe

    lateinit var tenSizesOfMensNikes: Array<Shoe>

    lateinit var eightSizesOfWomensNikes: Array<Shoe>

    lateinit var fiveSizesOfMensVans: Array<Shoe>

    protected abstract fun createStorageUtility(): IStorageUtilityIndexed<Shoe>

    @Before
    fun setupStorageContainer() {
        storage = createStorageUtility()

        nike = Shoe("nike", "mens", "10")

        tenSizesOfMensNikes = Array(10) { i ->
            Shoe("nike", "mens", (i + 1).toString())
        }

        eightSizesOfWomensNikes = Array(8) { i ->
            Shoe("nike", "womens", (i + 1).toString())
        }

        fiveSizesOfMensVans = Array(5) { i ->
            Shoe("vans", "mens", (i + 1).toString())
        }
    }

    @Test
    fun ensureIdIssuance() {
        assert(nike.getID() == -1)
        storage.write(nike)
        assertNotEquals("No ID issued to persistable", nike.getID(), -1)
    }

    @Test
    fun testWrite() {
        storage.write(nike)
        val id = nike.getID()
        val shouldBeNike = storage.read(id)
        assertNotNull("Failed to read record from DB", shouldBeNike)
        assertEquals("Incorrect record read from DB", nike, shouldBeNike)
    }

    @Test
    fun testOverwrite() {
        val review = "This shoe is fly"
        storage.write(nike)
        assert(nike.getReviewText() == "")
        nike.setReview(review)
        storage.write(nike)

        val shouldBeNewNike = storage.read(nike.getID())

        assertEquals("Persistable was not ovewritten correctly", review, shouldBeNewNike.getReviewText())
    }

    @Test
    fun testSingleMatch() {
        writeBulkSets()

        val sizeMatch = hashSetOf(
            tenSizesOfMensNikes[2].getID(),
            eightSizesOfWomensNikes[2].getID(),
            fiveSizesOfMensVans[2].getID()
        )

        val matches = storage.getIDsForValue(Shoe.META_SIZE, "3")
        assertEquals("Failed single index match [size][3]", sizeMatch, HashSet(matches))

        val matchesOnVector = storage.getIDsForValues(arrayOf(Shoe.META_SIZE), arrayOf("3"))
        assertEquals("Failed single vector index match [size][3]", sizeMatch, HashSet(matchesOnVector))
    }

    @Test
    fun testBulkMetaMatching() {
        writeBulkSets()

        val matches = storage.getIDsForValues(
            arrayOf(Shoe.META_BRAND, Shoe.META_STYLE),
            arrayOf("nike", "mens")
        )
        assertEquals(
            "Failed index match [brand,style][nike,mens]",
            getIdsFromModels(tenSizesOfMensNikes),
            HashSet(matches)
        )

        val newResultPath = LinkedHashSet<Int>()
        storage.getIDsForValues(
            arrayOf(Shoe.META_BRAND, Shoe.META_STYLE),
            arrayOf("nike", "mens"),
            newResultPath
        )
        assertEquals(
            "Failed index match [brand,style][nike,mens]",
            HashSet(matches),
            newResultPath
        )

        val matchedRecords = storage.getRecordsForValues(
            arrayOf(Shoe.META_BRAND, Shoe.META_STYLE),
            arrayOf("nike", "mens")
        )
        assertEquals(
            "Failed index match [brand,style][nike,mens]",
            getIdsFromModels(tenSizesOfMensNikes),
            getIdsFromModels(matchedRecords.toTypedArray())
        )
    }

    @Test
    fun testReadingAllEntries() {
        writeBulkSets()
        val matches = storage.getIDsForValues(emptyArray(), emptyArray())
        val expectedMatches = getIdsFromModels(tenSizesOfMensNikes)
        expectedMatches.addAll(getIdsFromModels(eightSizesOfWomensNikes))
        expectedMatches.addAll(getIdsFromModels(fiveSizesOfMensVans))
        assertEquals("Failed index match for all entries", expectedMatches, HashSet(matches))
    }

    private fun writeBulkSets() {
        writeAll(tenSizesOfMensNikes)
        writeAll(eightSizesOfWomensNikes)
        writeAll(fiveSizesOfMensVans)
    }

    fun getIdsFromModels(shoes: Array<Shoe>): MutableSet<Int> {
        return shoes.mapTo(HashSet()) { it.getID() }
    }

    private fun writeAll(shoes: Array<Shoe>) {
        for (s in shoes) {
            storage.write(s)
        }
    }
}
