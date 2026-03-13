package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.commcare.app.engine.NavigationStep
import org.commcare.app.engine.SessionNavigatorImpl
import org.commcare.session.SessionFrame
import org.commcare.util.CommCarePlatform

/**
 * Manages menu navigation state using the CommCareSession state machine.
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

        for (suite in platform.getInstalledSuites()) {
            val menus = suite.getMenusWithId(targetId)
            if (menus != null) {
                for (menu in menus) {
                    val menuTitle = try {
                        menu.getName()?.evaluate()
                    } catch (_: Exception) {
                        null
                    }
                    if (menuTitle != null) {
                        title = menuTitle
                    }

                    for (cmdId in menu.getCommandIds()) {
                        val entry = suite.getEntry(cmdId)
                        if (entry != null) {
                            val displayText = try {
                                entry.getText()?.evaluate() ?: cmdId
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
                val displayText = try {
                    subMenu.getName()?.evaluate() ?: (subMenu.getId() ?: "Menu")
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

        menuItems = items
    }

    /**
     * Select a menu item. Uses the session state machine to determine next step.
     */
    fun selectItem(item: MenuItem) {
        try {
            if (item.isMenu) {
                loadMenus(item.commandId)
            } else {
                navigator.selectCommand(item.commandId)
                // Check what the session needs next
                when (val step = navigator.getNextStep()) {
                    is NavigationStep.ShowMenu -> loadMenus()
                    is NavigationStep.ShowCaseList -> navigationState = NavigationState.EntitySelect
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
    data object FormEntry : NavigationState()
}
