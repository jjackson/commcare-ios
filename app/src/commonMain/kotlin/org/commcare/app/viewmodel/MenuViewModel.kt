package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.commcare.app.engine.NavigationStep
import org.commcare.app.engine.SessionNavigatorImpl
import org.commcare.app.ui.BreadcrumbSegment
import org.commcare.session.SessionFrame
import org.commcare.util.CommCarePlatform
import org.javarosa.xpath.expr.FunctionUtils

/**
 * Manages menu navigation state using the CommCareSession state machine.
 * Supports grid style, display conditions, and shadow modules.
 */
class MenuViewModel(
    val navigator: SessionNavigatorImpl
) {
    private val platform: CommCarePlatform get() = navigator.platform

    var menuItems by mutableStateOf<List<MenuItem>>(emptyList())
        private set
    var title by mutableStateOf("CommCare")
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var navigationState by mutableStateOf<NavigationState>(NavigationState.Menu)
        private set
    var breadcrumbs by mutableStateOf<List<BreadcrumbSegment>>(listOf(BreadcrumbSegment("Home")))
        private set

    /** Menu display style: "grid", "list", or null (default list) */
    var menuStyle by mutableStateOf<String?>(null)
        private set

    fun loadMenus(menuId: String? = null) {
        try {
            navigationState = NavigationState.Menu
            errorMessage = null
            loadMenuItems(menuId)
        } catch (e: Exception) {
            errorMessage = "Failed to load menus: ${e.message}"
        }
    }

    private fun loadMenuItems(menuId: String?) {
        val items = mutableListOf<MenuItem>()
        val targetId = menuId ?: "root"
        var detectedStyle: String? = null

        for (suite in platform.getInstalledSuites()) {
            val menus = suite.getMenusWithId(targetId)
            if (menus != null) {
                for (menu in menus) {
                    // Detect menu style (grid, list, etc.)
                    val style = menu.getStyle()
                    if (style != null) {
                        detectedStyle = style
                    }

                    // Check menu-level relevancy
                    if (!isMenuRelevant(menu)) continue

                    val menuTitle = try {
                        menu.getName()?.evaluate()
                    } catch (_: Exception) {
                        null
                    }
                    if (menuTitle != null) {
                        title = menuTitle
                    }

                    val commandIds = menu.getCommandIds()
                    for (i in commandIds.indices) {
                        val cmdId = commandIds[i]

                        // Check per-command relevancy (display condition)
                        if (!isCommandRelevant(menu, i)) continue

                        val entry = suite.getEntry(cmdId)
                        if (entry != null) {
                            val displayText = try {
                                val raw = entry.getText()?.evaluate() ?: cmdId
                                // Strip unresolved ${N} badge placeholders that
                                // appear when the locale string has parameters
                                // but no evaluation context to resolve them.
                                raw.replace(Regex("\\$\\{\\d+}\\s*"), "")
                            } catch (_: Exception) {
                                cmdId
                            }
                            items.add(MenuItem(
                                commandId = cmdId,
                                displayText = displayText,
                                imageUri = entry.getImageURI(),
                                isMenu = false
                            ))
                        }
                    }
                }
            }

            for (subMenu in suite.getMenusWithRoot(targetId)) {
                // Check sub-menu relevancy
                if (!isMenuRelevant(subMenu)) continue

                val displayText = try {
                    val raw = subMenu.getName()?.evaluate() ?: (subMenu.getId() ?: "Menu")
                    raw.replace(Regex("\\$\\{\\d+}\\s*"), "")
                } catch (_: Exception) {
                    subMenu.getId() ?: "Menu"
                }
                items.add(MenuItem(
                    commandId = subMenu.getId() ?: "",
                    displayText = displayText,
                    imageUri = subMenu.getImageURI(),
                    isMenu = true
                ))
            }
        }

        menuStyle = detectedStyle
        menuItems = items
    }

    /**
     * Check if a menu passes its relevancy condition.
     */
    private fun isMenuRelevant(menu: org.commcare.suite.model.Menu): Boolean {
        return try {
            val relevance = menu.getMenuRelevance() ?: return true
            val ec = navigator.session.getEvaluationContext()
            FunctionUtils.toBoolean(relevance.eval(ec))
        } catch (_: Exception) {
            true // Show menu if relevance evaluation fails
        }
    }

    /**
     * Check if a specific command within a menu passes its display condition.
     */
    private fun isCommandRelevant(menu: org.commcare.suite.model.Menu, index: Int): Boolean {
        return try {
            val relevance = menu.getCommandRelevance(index) ?: return true
            val ec = navigator.session.getEvaluationContext()
            FunctionUtils.toBoolean(relevance.eval(ec))
        } catch (_: Exception) {
            true // Show command if relevance evaluation fails
        }
    }

    /**
     * Select a menu item. Uses the session state machine to determine next step.
     */
    fun selectItem(item: MenuItem) {
        try {
            if (item.isMenu) {
                breadcrumbs = breadcrumbs + BreadcrumbSegment(item.displayText, item.commandId)
                loadMenus(item.commandId)
            } else {
                breadcrumbs = breadcrumbs + BreadcrumbSegment(item.displayText, item.commandId)
                navigator.selectCommand(item.commandId)
                // Check what the session needs next
                when (val step = navigator.getNextStep()) {
                    is NavigationStep.ShowMenu -> loadMenus()
                    is NavigationStep.ShowCaseList -> navigationState = NavigationState.EntitySelect
                    is NavigationStep.ShowCaseSearch -> navigationState = NavigationState.CaseSearch
                    is NavigationStep.StartForm -> navigationState = NavigationState.FormEntry
                    is NavigationStep.SyncRequired -> {
                        errorMessage = "Sync required before proceeding"
                    }
                    is NavigationStep.Error -> {
                        errorMessage = step.message
                    }
                }
            }
        } catch (e: Exception) {
            errorMessage = "Navigation error: ${e.message}"
        }
    }

    fun goBack() {
        try {
            navigator.stepBack()
            if (breadcrumbs.size > 1) {
                breadcrumbs = breadcrumbs.dropLast(1)
            }
            val step = navigator.getNextStep()
            when (step) {
                is NavigationStep.ShowMenu -> {
                    navigationState = NavigationState.Menu
                    loadMenus()
                    title = "CommCare"
                }
                else -> loadMenus()
            }
        } catch (_: Exception) {
            navigationState = NavigationState.Menu
            breadcrumbs = listOf(BreadcrumbSegment("Home"))
            loadMenus()
        }
    }

    /**
     * Navigate to a specific breadcrumb level by popping back to it.
     */
    fun navigateToBreadcrumb(index: Int) {
        if (index >= breadcrumbs.size - 1) return // Already there
        try {
            // Pop back to target level
            val stepsBack = breadcrumbs.size - 1 - index
            for (i in 0 until stepsBack) {
                navigator.stepBack()
            }
            breadcrumbs = breadcrumbs.take(index + 1)
            navigationState = NavigationState.Menu
            val menuId = breadcrumbs.lastOrNull()?.menuId
            loadMenus(menuId)
        } catch (_: Exception) {
            breadcrumbs = listOf(BreadcrumbSegment("Home"))
            navigationState = NavigationState.Menu
            loadMenus()
        }
    }
}

data class MenuItem(
    val commandId: String,
    val displayText: String,
    val imageUri: String? = null,
    val isMenu: Boolean = false
)

sealed class NavigationState {
    data object Menu : NavigationState()
    data object EntitySelect : NavigationState()
    data object CaseSearch : NavigationState()
    data object FormEntry : NavigationState()
}
