package org.commcare.backend.test

import org.commcare.session.SessionFrame
import org.commcare.suite.model.EntityDatum
import org.commcare.test.utilities.MockApp
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * This tests the functionality of detail screens that display fields for entity subnodes.
 *
 * Created by jschweers on 9/2/2015.
 */
class EntitySubnodeDetailTest {
    lateinit var mApp: MockApp

    @Before
    fun init() {
        mApp = MockApp("/entity_subnode_detail/")
    }

    @Test
    fun testDetailDisplay() {
        val session = mApp.getSession()
        assertEquals(session.getNeededData(), SessionFrame.STATE_COMMAND_ID)

        session.setCommand("m0")

        assertEquals(session.getNeededData(), SessionFrame.STATE_DATUM_VAL)
        val entityDatum = session.getNeededDatum() as EntityDatum
        assertEquals(entityDatum.getDataId(), "report_id_my_report")
        assertEquals(entityDatum.getLongDetail(), "reports.my_report.data")

        val confirmDetail = session.getDetail(entityDatum.getLongDetail())!!
        assertNotNull(confirmDetail.nodeset)

        val detailReference = entityDatum.getNodeset()!!
        val contextualizedRef = confirmDetail.nodeset!!.contextualize(detailReference)!!
        val references = session.getEvaluationContext().expandReference(contextualizedRef)!!
        assertEquals(4, references.size)
    }
}
