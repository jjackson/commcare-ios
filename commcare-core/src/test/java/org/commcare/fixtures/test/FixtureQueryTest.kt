package org.commcare.fixtures.test

import org.commcare.core.parse.ParseUtils
import org.commcare.test.utilities.CaseTestUtils
import org.commcare.util.mocks.MockDataUtils
import org.commcare.util.mocks.MockUserDataSandbox
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.xml.util.InvalidStructureException
import org.javarosa.xml.util.UnfullfilledRequirementsException
import org.javarosa.xpath.XPathTypeMismatchException
import org.javarosa.xpath.parser.XPathSyntaxException
import org.junit.Before
import org.junit.Test
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

/**
 * Test XPath expressions for fixtures
 *
 * @author Clayton Sims (csims@dimagi.com)
 */
class FixtureQueryTest {

    private lateinit var sandbox: MockUserDataSandbox

    @Before
    fun setUp() {
        sandbox = MockDataUtils.getStaticStorage()
    }

    @Test
    @Throws(XPathSyntaxException::class, UnfullfilledRequirementsException::class,
            XmlPullParserException::class, IOException::class, InvalidStructureException::class)
    fun queryNonHomogenousLookups() {
        ParseUtils.parseIntoSandbox(javaClass.getResourceAsStream("/fixture_create.xml"), sandbox)

        val ec = MockDataUtils.buildContextWithInstance(sandbox, "commtrack:products", CaseTestUtils.FIXTURE_INSTANCE_PRODUCT)
        CaseTestUtils.xpathEvalAndAssert(ec, "count(instance('commtrack:products')/products/product[@heterogenous_attribute = 'present'])", 2.0)
        CaseTestUtils.xpathEvalAndAssert(ec, "instance('commtrack:products')/products/@last_sync", "2018-07-27T12:54:11.987997+00:00")
    }

    @Test(expected = XPathTypeMismatchException::class)
    @Throws(XPathSyntaxException::class, UnfullfilledRequirementsException::class,
            XmlPullParserException::class, IOException::class, InvalidStructureException::class)
    fun queryMissingLookups() {
        ParseUtils.parseIntoSandbox(javaClass.getResourceAsStream("/fixture_create.xml"), sandbox)

        val ec = MockDataUtils.buildContextWithInstance(sandbox, "commtrack:products", CaseTestUtils.FIXTURE_INSTANCE_PRODUCT)
        CaseTestUtils.xpathEvalAndAssert(ec, "instance('commtrack:products')/products/product[@heterogenous_attribute = 'present']/missing_reference", "")
    }

    @Test(expected = XPathTypeMismatchException::class)
    @Throws(XPathSyntaxException::class, UnfullfilledRequirementsException::class,
            XmlPullParserException::class, IOException::class, InvalidStructureException::class)
    fun queryMissingPredicate() {
        ParseUtils.parseIntoSandbox(javaClass.getResourceAsStream("/fixture_create.xml"), sandbox)

        val ec = MockDataUtils.buildContextWithInstance(sandbox, "commtrack:products", CaseTestUtils.FIXTURE_INSTANCE_PRODUCT)
        CaseTestUtils.xpathEvalAndAssert(ec, "instance('commtrack:products')/products/product[@heterogenous_attribute = 'present'][missing_reference='test']", "")
    }
}
