package org.javarosa.core.model.data.test

import org.javarosa.core.model.data.GeoPointData
import org.junit.Test

import org.junit.Assert.assertEquals

class GeoPointDataTests {

    @Test
    fun testGetData() {
        val pointsA = doubleArrayOf(1.11111, 2.2, -1.111, -4.19999)
        val pointsB = doubleArrayOf(1.0, 2.0, -3.0, 4.0)
        val pointsC = doubleArrayOf(6.899999999, 3.20000001)
        val pointsD = doubleArrayOf(6.0, 3.0, 0.0000000000000001, 0.00000009)

        val data = GeoPointData(pointsA)
        assertEquals("GeoPointData test constructor and decimal truncation",
            "1.11111 2.2 -1.12 -4.2", data.getDisplayText())

        data.setValue(pointsB)
        assertEquals("GeoPointData test setValue on 4 datapoints and decimal truncation",
            "1.0 2.0 -3.0 4.0", data.getDisplayText())

        data.setValue(pointsC)
        assertEquals("GeoPointData test setValue on 2 datapoints",
            "6.899999999 3.20000001", data.getDisplayText())

        data.setValue(pointsD)
        assertEquals("GeoPointData test setValue on 4 datapoints and decimal truncation",
            "6.0 3.0 0.0 0.0", data.getDisplayText())
    }
}
