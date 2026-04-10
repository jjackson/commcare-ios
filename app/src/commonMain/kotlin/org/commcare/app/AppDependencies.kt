package org.commcare.app

import org.commcare.app.network.ConnectIdApi
import org.commcare.app.network.ConnectMarketplaceApi
import org.commcare.app.platform.PlatformKeychainStore
import org.commcare.app.storage.AppRecordRepository
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.ConnectIdRepository
import org.commcare.app.viewmodel.AppInstallViewModel
import org.commcare.app.viewmodel.ConnectIdTokenManager
import org.commcare.app.viewmodel.ConnectIdViewModel
import org.commcare.app.viewmodel.DemoModeManager
import org.commcare.app.viewmodel.LoginViewModel
import org.commcare.app.viewmodel.SetupViewModel
import org.commcare.app.viewmodel.UserKeyRecordManager

/**
 * Simple dependency container that constructs all app-level services and ViewModels once.
 * Created via `remember {}` in [App] so instances survive recomposition but are
 * tied to the composable lifecycle. Not a DI framework — just a single place to
 * wire constructor dependencies instead of scattering them across the composable body.
 */
class AppDependencies(db: CommCareDatabase) {
    // -- Repositories & services --
    val appRepository = AppRecordRepository(db)
    val connectIdRepository = ConnectIdRepository(db)
    val connectIdApi = ConnectIdApi()
    val keychainStore = PlatformKeychainStore()
    val marketplaceApi = ConnectMarketplaceApi()

    // -- Managers --
    val keyRecordManager = UserKeyRecordManager(db, keychainStore)
    val demoModeManager = DemoModeManager(db)
    val connectIdTokenManager = ConnectIdTokenManager(connectIdApi, connectIdRepository, keychainStore, db)

    // -- ViewModels --
    val connectIdViewModel = ConnectIdViewModel(connectIdApi, connectIdRepository, keychainStore)
    val loginViewModel = LoginViewModel(db).also { it.setKeyRecordManager(keyRecordManager) }
    val setupViewModel = SetupViewModel()
    val appInstallViewModel = AppInstallViewModel(db, appRepository)
}
