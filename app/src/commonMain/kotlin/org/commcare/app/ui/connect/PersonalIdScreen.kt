package org.commcare.app.ui.connect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.commcare.app.viewmodel.ConnectIdViewModel
import org.commcare.app.viewmodel.RegistrationStep

@Composable
fun PersonalIdScreen(
    viewModel: ConnectIdViewModel,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header with back button and title
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (viewModel.currentStep != RegistrationStep.SUCCESS) {
                Text(
                    text = "<",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .clickable {
                            if (viewModel.currentStep == RegistrationStep.PHONE_ENTRY) onCancel()
                            else viewModel.goBack()
                        }
                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                        .padding(end = 8.dp)
                )
            }
            Text(
                text = "Personal ID",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Step content
        when (viewModel.currentStep) {
            RegistrationStep.PHONE_ENTRY      -> PhoneEntryStep(viewModel)
            RegistrationStep.OTP_VERIFICATION -> OtpVerificationStep(viewModel)
            RegistrationStep.NAME_ENTRY       -> NameEntryStep(viewModel)
            RegistrationStep.BACKUP_CODE      -> BackupCodeStep(viewModel)
            RegistrationStep.PHOTO_CAPTURE    -> PhotoCaptureStep(viewModel)
            RegistrationStep.ACCOUNT_CREATION -> AccountCreationStep(viewModel)
            RegistrationStep.BIOMETRIC_SETUP  -> BiometricSetupStep(viewModel)
            RegistrationStep.SUCCESS          -> SuccessStep(viewModel, onComplete)
        }
    }
}
