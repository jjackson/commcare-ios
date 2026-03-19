package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class SetupStep { MAIN, ENTER_CODE, INSTALL_FROM_LIST, SCANNING }

class SetupViewModel {
    var currentStep by mutableStateOf(SetupStep.MAIN)
        private set
    var profileUrl by mutableStateOf("")
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun showEnterCode() { currentStep = SetupStep.ENTER_CODE }
    fun showInstallFromList() { currentStep = SetupStep.INSTALL_FROM_LIST }
    fun showScanning() { currentStep = SetupStep.SCANNING }
    fun backToMain() { currentStep = SetupStep.MAIN; errorMessage = null }

    fun onQrScanned(url: String) {
        profileUrl = url
        currentStep = SetupStep.MAIN
    }

    fun onCodeEntered(url: String) {
        profileUrl = url
    }

    fun clearError() { errorMessage = null }
    fun setError(msg: String) { errorMessage = msg }
}
