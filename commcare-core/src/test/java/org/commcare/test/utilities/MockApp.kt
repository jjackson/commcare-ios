package org.commcare.test.utilities

import org.commcare.core.interfaces.RemoteInstanceFetcher
import org.commcare.core.parse.ParseUtils
import org.commcare.modern.session.SessionWrapper
import org.commcare.util.engine.CommCareConfigEngine
import org.commcare.util.mocks.MockUserDataSandbox
import org.javarosa.core.model.FormDef
import org.javarosa.core.model.FormIndex
import org.javarosa.core.test.FormParseInit
import org.javarosa.core.util.externalizable.LivePrototypeFactory
import org.javarosa.form.api.FormEntryController

/**
 * A mock app is a quick test wrapper that makes it easy to start playing with a live instance
 * of a CommCare app including the session and user data that goes along with it.
 *
 * To use this class, make a copy of the /template/ app in the test resources and extend the
 * config and data in that copy, then pass its resource path to the constructor.
 *
 * Created by ctsims on 8/14/2015.
 */
class MockApp
/**
 * Creates and initializes a mockapp that is located at the provided Java Resource path.
 *
 * @param resourcePath The resource path to a an app template. Needs to contain a leading and
 *                     trailing slash, like /path/app/
 */
@Throws(Exception::class)
constructor(resourcePath: String) {
    private val mSessionWrapper: SessionWrapper
    private val APP_BASE: String

    init {
        if (!(resourcePath.startsWith("/") && resourcePath.endsWith("/"))) {
            throw IllegalArgumentException("Invalid resource path for a mock app $resourcePath")
        }
        APP_BASE = resourcePath
        val mPrototypeFactory = setupStaticStorage()
        val mSandbox = MockUserDataSandbox(mPrototypeFactory)
        val mEngine = CommCareConfigEngine(mPrototypeFactory)

        mEngine.installAppFromReference("jr://resource" + APP_BASE + "profile.ccpr")
        mEngine.initEnvironment()
        ParseUtils.parseIntoSandbox(this.javaClass.getResourceAsStream(APP_BASE + "user_restore.xml"), mSandbox)

        // If we parsed in a user, arbitrarily log one in.
        mSandbox.setLoggedInUser(mSandbox.getUserStorage().read(0))

        mSessionWrapper = SessionWrapper(mEngine.getPlatform(), mSandbox)
    }

    /**
     * Loads the provided form and properly initializes external data instances,
     * such as the casedb and commcare session.
     */
    @Throws(RemoteInstanceFetcher.RemoteInstanceException::class)
    fun loadAndInitForm(formFileInApp: String): FormEntryController {
        val fpi = FormParseInit(APP_BASE + formFileInApp)
        val fec = fpi.getFormEntryController()
        fec.jumpToIndex(FormIndex.createBeginningOfFormIndex())

        val fd = fpi.getFormDef()
        // run initialization to ensure xforms-ready event and binds are
        // triggered.
        fd!!.initialize(true, mSessionWrapper.getIIF())
        return fec
    }

    fun getSession(): SessionWrapper = mSessionWrapper

    companion object {
        private fun setupStaticStorage(): LivePrototypeFactory {
            return LivePrototypeFactory()
        }
    }
}
