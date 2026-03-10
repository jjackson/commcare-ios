package org.commcare.suite.model

import org.commcare.core.process.CommCareInstanceInitializer
import org.commcare.modern.session.SessionWrapperInterface
import org.commcare.util.CommCarePlatform
import org.commcare.util.LoggerInterface
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.trace.ReducingTraceReporter
import org.javarosa.core.model.utils.InstrumentationUtils
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.XPathTypeMismatchException
import org.javarosa.xpath.analysis.InstanceNameAccumulatingAnalyzer
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException

import java.util.Hashtable
import java.util.Vector

/**
 * Created by willpride on 1/3/17.
 */
class MenuLoader {
    var loadException: Exception? = null
    var xPathErrorMessage: String? = null
        private set
    var menus: Array<MenuDisplayable>? = null
    var allMenus: Array<MenuDisplayable>? = null
        private set
    private var badges: Array<String>? = null
    private var loggerInterface: LoggerInterface? = null
    private var traceReporter: ReducingTraceReporter? = null

    constructor(
        platform: CommCarePlatform, sessionWrapper: SessionWrapperInterface, menuId: String?,
        loggerInterface: LoggerInterface, shouldOutputEvalTrace: Boolean, hideTrainingRoot: Boolean
    ) : this(platform, sessionWrapper, menuId, loggerInterface, shouldOutputEvalTrace, hideTrainingRoot, false)

    constructor(
        platform: CommCarePlatform, sessionWrapper: SessionWrapperInterface, menuId: String?,
        loggerInterface: LoggerInterface, shouldOutputEvalTrace: Boolean, hideTrainingRoot: Boolean,
        includeBadges: Boolean
    ) {
        this.loggerInterface = loggerInterface
        if (shouldOutputEvalTrace) {
            this.traceReporter = ReducingTraceReporter(false)
        }
        this.getMenuDisplayables(platform, sessionWrapper, menuId, hideTrainingRoot, includeBadges)
    }

    fun getErrorMessage(): String? {
        if (loadException != null) {
            val errorMessage = loadException!!.message
            loggerInterface!!.logError(errorMessage, loadException)
            return errorMessage
        }
        return null
    }

    private fun getMenuDisplayables(
        platform: CommCarePlatform,
        sessionWrapper: SessionWrapperInterface,
        menuID: String?, hideTrainingRoot: Boolean,
        includeBadges: Boolean
    ) {
        val items = Vector<MenuDisplayable>()
        val allItems = Vector<MenuDisplayable>()
        val badgesList = Vector<String>()
        val map = platform.getCommandToEntryMap()
        for (s in platform.getInstalledSuites()) {
            for (m in s.getMenus()) {
                try {
                    if (m.getId() == menuID) {
                        val addToItems = menuIsRelevant(sessionWrapper, m) && menuAssertionsPass(sessionWrapper, m)
                        addRelevantCommandEntries(sessionWrapper, m, items, badgesList, map, includeBadges, allItems, addToItems)
                    } else {
                        addUnaddedMenu(sessionWrapper, menuID, m, items, badgesList, hideTrainingRoot, includeBadges, allItems)
                    }
                } catch (xpe: CommCareInstanceInitializer.FixtureInitializationException) {
                    loadException = xpe
                    menus = arrayOfNulls<MenuDisplayable>(0) as Array<MenuDisplayable>
                    return
                } catch (xpe: XPathSyntaxException) {
                    loadException = xpe
                    menus = arrayOfNulls<MenuDisplayable>(0) as Array<MenuDisplayable>
                    return
                } catch (xpe: XPathException) {
                    loadException = xpe
                    menus = arrayOfNulls<MenuDisplayable>(0) as Array<MenuDisplayable>
                    return
                }
            }
        }
        menus = arrayOfNulls<MenuDisplayable>(items.size) as Array<MenuDisplayable>
        items.copyInto(menus!!)

        allMenus = arrayOfNulls<MenuDisplayable>(allItems.size) as Array<MenuDisplayable>
        allItems.copyInto(allMenus!!)

        if (includeBadges) {
            this.badges = arrayOfNulls<String>(badgesList.size) as Array<String>
            badgesList.copyInto(this.badges!!)
        }
    }

    private fun addUnaddedMenu(
        sessionWrapper: SessionWrapperInterface, currentMenuId: String?,
        toAdd: Menu, items: Vector<MenuDisplayable>, badges: Vector<String>,
        hideTrainingRoot: Boolean, includeBadges: Boolean,
        allItems: Vector<MenuDisplayable>
    ) {
        if (hideTrainingRoot && toAdd.getId() == Menu.TRAINING_MENU_ROOT) {
            return
        }
        if (currentMenuId == toAdd.getRoot()) {
            // make sure we didn't already add this ID
            var idExists = false
            for (o in items) {
                if (o is Menu) {
                    if (o.getId() == toAdd.getId()) {
                        idExists = true
                        break
                    }
                }
            }
            if (!idExists) {
                allItems.add(toAdd)
                if (menuIsRelevant(sessionWrapper, toAdd)) {
                    items.add(toAdd)
                    if (includeBadges) {
                        badges.add(toAdd.getTextForBadge(sessionWrapper.getEvaluationContext(toAdd.getCommandID()!!)).blockingGet())
                    }
                }
            }
        }
    }

    @Throws(XPathSyntaxException::class)
    private fun menuIsRelevant(sessionWrapper: SessionWrapperInterface, m: Menu): Boolean {
        val relevance = m.getMenuRelevance()
        if (m.getMenuRelevance() != null) {
            xPathErrorMessage = m.getMenuRelevanceRaw()

            val traceableContext = accumulateInstances(sessionWrapper, m, relevance!!)

            val result = FunctionUtils.toBoolean(relevance.eval(traceableContext))
            InstrumentationUtils.printAndClearTraces(traceReporter, "menu load expand")
            return result
        }
        return true
    }

    @Throws(XPathSyntaxException::class)
    fun menuAssertionsPass(sessionWrapper: SessionWrapperInterface, m: Menu): Boolean {
        val assertions = m.getAssertions()
        val assertionXPathStrings = assertions.getAssertionsXPaths()

        for (i in 0 until assertionXPathStrings.size) {
            val assertionXPath = XPathParseTool.parseXPath(assertionXPathStrings[i])!!
            val traceableContext = accumulateInstances(sessionWrapper, m, assertionXPath)

            val text = assertions.evalAssertionAtIndex(i, assertionXPath, traceableContext)

            InstrumentationUtils.printAndClearTraces(traceReporter, "menu assertions")
            if (text != null) {
                loadException = Exception(text.evaluate())
                return false
            }
        }
        return true
    }

    private fun accumulateInstances(
        sessionWrapper: SessionWrapperInterface,
        m: Menu,
        xPathExpression: XPathExpression
    ): EvaluationContext {
        val instancesNeededByCondition =
            InstanceNameAccumulatingAnalyzer().accumulate(xPathExpression)
        val ec = sessionWrapper.getRestrictedEvaluationContext(
            m.getId()!!,
            instancesNeededByCondition!!
        )
        val traceableContext = EvaluationContext(ec, ec.getOriginalContext())
        if (traceReporter != null) {
            traceableContext.setDebugModeOn(traceReporter)
        }
        return traceableContext
    }

    @Throws(XPathSyntaxException::class)
    private fun addRelevantCommandEntries(
        sessionWrapper: SessionWrapperInterface,
        m: Menu,
        items: Vector<MenuDisplayable>,
        badges: Vector<String>,
        map: Hashtable<String, Entry>,
        includeBadges: Boolean,
        allItems: Vector<MenuDisplayable>,
        addToItems: Boolean
    ) {
        xPathErrorMessage = ""
        for (command in m.getCommandIds()) {
            allItems.add(map[command])
            val relevancyCondition = m.getCommandRelevance(m.indexOfCommand(command))
            if (relevancyCondition != null) {
                xPathErrorMessage = m.getCommandRelevanceRaw(m.indexOfCommand(command))

                val instancesNeededByRelevancyCondition =
                    InstanceNameAccumulatingAnalyzer().accumulate(relevancyCondition)
                val ec = sessionWrapper.getRestrictedEvaluationContext(
                    command,
                    instancesNeededByRelevancyCondition!!
                )

                val ret = relevancyCondition.eval(ec)
                try {
                    if (!FunctionUtils.toBoolean(ret)) {
                        continue
                    }
                } catch (e: XPathTypeMismatchException) {
                    val msg = "relevancy condition for menu item returned non-boolean value : $ret"
                    xPathErrorMessage = msg
                    loadException = e
                    loggerInterface!!.logError(msg, e)
                    throw e
                }
            }

            val e = map[command]!!
            if (e.isView()) {
                // If this is a "view", not an "entry"
                // we only want to display it if all of its
                // datums are not already present
                if (sessionWrapper.getNeededDatum(e) == null) {
                    continue
                }
            }

            if (addToItems) {
                items.add(e)
            }
            if (includeBadges) {
                badges.add(e.getTextForBadge(sessionWrapper.getEvaluationContext(e.getCommandId()!!)).blockingGet())
            }
        }
    }

    fun setxPathErrorMessage(xPathErrorMessage: String?) {
        this.xPathErrorMessage = xPathErrorMessage
    }

    fun getxPathErrorMessage(): String? = xPathErrorMessage

    fun getBadgeText(): Array<String>? = badges

    fun setBadgeText(badgeText: Array<String>?) {
        this.badges = badgeText
    }
}
