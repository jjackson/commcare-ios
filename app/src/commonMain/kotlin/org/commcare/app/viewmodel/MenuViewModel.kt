package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.commcare.session.CommCareSession
import org.commcare.session.SessionFrame
import org.commcare.util.CommCarePlatform

/**
 * Manages menu navigation state.
 * Loads menus from CommCarePlatform and handles user selections.
 */
class MenuViewModel(
    private val platform: CommCarePlatform,
    private val session: CommCareSession
) {
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
            // Get menus at this level
            val menus = suite.getMenusWithId(targetId)
            if (menus != null) {
                for (menu in menus) {
                    // Set title from menu
                    val menuTitle = try {
                        menu.getName()?.evaluate()
                    } catch (e: Exception) {
                        null
                    }
                    if (menuTitle != null) {
                        title = menuTitle
                    }

                    // Add entries from this menu
                    for (cmdId in menu.getCommandIds()) {
                        val entry = suite.getEntry(cmdId)
                        if (entry != null) {
                            val displayText = try {
                                entry.getText()?.evaluate() ?: cmdId
                            } catch (e: Exception) {
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

            // Add sub-menus that have this menu as root
            for (subMenu in suite.getMenusWithRoot(targetId)) {
                val displayText = try {
                    subMenu.getName()?.evaluate() ?: (subMenu.getId() ?: "Menu")
                } catch (e: Exception) {
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

    fun selectItem(item: MenuItem) {
        try {
            if (item.isMenu) {
                // Navigate into sub-menu
                loadMenus(item.commandId)
            } else {
                // Select a form entry command
                session.setCommand(item.commandId)
                navigationState = NavigationState.FormEntry
            }
        } catch (e: Exception) {
            errorMessage = "Navigation error: ${e.message}"
        }
    }

    fun goBack() {
        try {
            loadMenus()
            title = "CommCare"
        } catch (e: Exception) {
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
