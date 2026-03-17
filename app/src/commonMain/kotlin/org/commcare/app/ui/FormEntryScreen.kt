package org.commcare.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import org.commcare.app.platform.PlatformAudioCapture
import org.commcare.app.platform.PlatformBarcodeScanner
import org.commcare.app.platform.PlatformImageCapture
import org.commcare.app.platform.PlatformLocationProvider
import org.commcare.app.platform.PlatformSignatureCapture
import org.commcare.app.viewmodel.FormEntryViewModel
import org.commcare.app.viewmodel.LanguageViewModel
import org.commcare.app.viewmodel.QuestionState
import org.commcare.app.viewmodel.QuestionType

@Composable
fun FormEntryScreen(
    viewModel: FormEntryViewModel,
    onComplete: () -> Unit,
    onBack: () -> Unit,
    onSaveDraft: (() -> Unit)? = null,
    languageViewModel: LanguageViewModel? = null
) {
    val layoutDirection = if (languageViewModel?.isRtl == true) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "<",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .semantics { contentDescription = "Go back" }
                    .clickable { onBack() }
                    .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                    .padding(end = 8.dp)
            )
            Text(
                text = viewModel.formTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() }
            )
        }

        // Persistent case tile
        val tileData = viewModel.persistentTileData
        if (tileData != null) {
            PersistentTileBar(data = tileData)
        }

        // Language selector
        if (languageViewModel != null && languageViewModel.availableLanguages.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                for (lang in languageViewModel.availableLanguages) {
                    Text(
                        text = lang,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (lang == languageViewModel.currentLanguage)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .semantics {
                                contentDescription = if (lang == languageViewModel.currentLanguage)
                                    "$lang, selected" else "Switch to $lang"
                            }
                            .clickable {
                                if (languageViewModel.setLanguage(lang)) {
                                    viewModel.refreshAfterLanguageChange()
                                }
                            }
                            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Progress
        LinearProgressIndicator(
            progress = { viewModel.progress },
            modifier = Modifier.fillMaxWidth()
                .semantics { contentDescription = "Form progress" }
        )

        HorizontalDivider()

        if (viewModel.isSubmitting) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics { contentDescription = "Submitting form" }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Submitting form...")
            }
        } else if (viewModel.isComplete) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Form Complete",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { onComplete() }) {
                    Text("Submit")
                }
            }
        } else if (viewModel.isRepeatPrompt) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = viewModel.repeatPromptText,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(onClick = { viewModel.skipRepeat() }) {
                        Text("No")
                    }
                    Button(onClick = { viewModel.addRepeat() }) {
                        Text("Yes, add another")
                    }
                }
            }
        } else {
            val questionList = viewModel.questions
            if (questionList.isNotEmpty()) {
                val swipeThreshold = 100f
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                        .pointerInput(Unit) {
                            var totalDrag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                                onDragEnd = {
                                    if (totalDrag < -swipeThreshold) {
                                        viewModel.nextQuestion()
                                    } else if (totalDrag > swipeThreshold) {
                                        viewModel.previousQuestion()
                                    }
                                }
                            )
                        }
                ) {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                    ) {
                        for ((index, question) in questionList.withIndex()) {
                            QuestionWidget(question, index, viewModel, enabled = !viewModel.isReadOnly)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Navigation buttons
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(onClick = { viewModel.previousQuestion() }) {
                            Text("Back")
                        }
                        if (onSaveDraft != null && !viewModel.isReadOnly) {
                            OutlinedButton(onClick = { onSaveDraft() }) {
                                Text("Save Draft")
                            }
                        }
                        Button(onClick = { viewModel.nextQuestion() },
                            enabled = !viewModel.isReadOnly
                        ) {
                            Text(if (viewModel.isReadOnly) "Review" else "Next")
                        }
                    }
                }
            }
        }
    }
    } // CompositionLocalProvider
}

@Composable
private fun QuestionWidget(
    question: QuestionState,
    index: Int,
    viewModel: FormEntryViewModel,
    enabled: Boolean = true
) {
    // Question text
    Text(
        text = question.questionText,
        style = MaterialTheme.typography.bodyLarge
    )

    if (question.isRequired) {
        Text(
            text = "* Required",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    when (question.questionType) {
        QuestionType.TEXT -> {
            val isMultiline = question.appearance?.contains("multiline") == true
            val keyboardType = if (question.appearance?.contains("numeric") == true) {
                KeyboardType.Number
            } else {
                KeyboardType.Text
            }
            OutlinedTextField(
                value = question.answer,
                onValueChange = { viewModel.answerQuestionString(index, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Answer") },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = !isMultiline,
                minLines = if (isMultiline) 3 else 1,
                enabled = enabled
            )
        }

        QuestionType.INTEGER -> {
            OutlinedTextField(
                value = question.answer,
                onValueChange = { viewModel.answerQuestionString(index, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Integer") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = enabled
            )
        }

        QuestionType.DECIMAL -> {
            OutlinedTextField(
                value = question.answer,
                onValueChange = { viewModel.answerQuestionString(index, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Decimal") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                enabled = enabled
            )
        }

        QuestionType.DATE -> {
            OutlinedTextField(
                value = question.answer,
                onValueChange = { viewModel.answerQuestionString(index, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Date (YYYY-MM-DD)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true,
                enabled = enabled
            )
        }

        QuestionType.TIME -> {
            OutlinedTextField(
                value = question.answer,
                onValueChange = { viewModel.answerQuestionString(index, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Time (HH:MM)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true,
                enabled = enabled
            )
        }

        QuestionType.SELECT_ONE -> {
            for (choice in question.choices) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = (if (enabled) Modifier.clickable {
                        viewModel.answerQuestionString(index, choice)
                    } else Modifier).defaultMinSize(minHeight = 44.dp)
                ) {
                    RadioButton(
                        selected = question.answer == choice,
                        onClick = if (enabled) {{ viewModel.answerQuestionString(index, choice) }} else null
                    )
                    Text(text = choice)
                }
            }
        }

        QuestionType.SELECT_MULTI -> {
            for (choice in question.choices) {
                val isSelected = question.selectedChoices.contains(choice)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = (if (enabled) Modifier.clickable {
                        viewModel.toggleMultiSelectChoice(index, choice)
                    } else Modifier).defaultMinSize(minHeight = 44.dp)
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = if (enabled) {{ viewModel.toggleMultiSelectChoice(index, choice) }} else null,
                        enabled = enabled
                    )
                    Text(text = choice)
                }
            }
        }

        QuestionType.TRIGGER -> {
            Button(
                onClick = { viewModel.answerQuestionString(index, "OK") },
                enabled = enabled
            ) {
                Text("OK")
            }
        }

        QuestionType.LABEL -> {
            // Label only, no input needed
        }

        QuestionType.GEOPOINT -> {
            OutlinedTextField(
                value = question.answer,
                onValueChange = { viewModel.answerQuestionString(index, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Location (lat lon alt accuracy)") },
                singleLine = true,
                enabled = enabled
            )
            if (enabled) {
                val locationProvider = remember { PlatformLocationProvider() }
                Button(onClick = { viewModel.captureLocation(index, locationProvider) }) {
                    Text("Capture GPS")
                }
            }
            if (question.answer.isNotBlank()) {
                Text(
                    text = formatGeoPoint(question.answer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        QuestionType.IMAGE -> {
            if (enabled) {
                val imageCapture = remember { PlatformImageCapture() }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.captureImage(index, imageCapture) }) {
                        Text("Take Photo")
                    }
                    OutlinedButton(onClick = { imageCapture.pickFromGallery { path ->
                        if (path != null) viewModel.answerQuestionString(index, path)
                    }}) {
                        Text("Gallery")
                    }
                }
            }
            if (question.answer.isNotBlank()) {
                Text(
                    text = "Image: ${question.answer}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        QuestionType.AUDIO -> {
            if (enabled) {
                val audioCapture = remember { PlatformAudioCapture() }
                Button(onClick = { viewModel.captureAudio(index, audioCapture) }) {
                    Text("Record Audio")
                }
            }
            if (question.answer.isNotBlank()) {
                Text(
                    text = "Audio: ${question.answer}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        QuestionType.SIGNATURE -> {
            if (enabled) {
                val signatureCapture = remember { PlatformSignatureCapture() }
                Button(onClick = { viewModel.captureSignature(index, signatureCapture) }) {
                    Text("Capture Signature")
                }
            }
            if (question.answer.isNotBlank()) {
                Text(
                    text = "Signature: ${question.answer}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        QuestionType.BARCODE -> {
            OutlinedTextField(
                value = question.answer,
                onValueChange = { viewModel.answerQuestionString(index, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Barcode") },
                singleLine = true,
                enabled = enabled
            )
            if (enabled) {
                val barcodeScanner = remember { PlatformBarcodeScanner() }
                Button(onClick = { viewModel.scanBarcode(index, barcodeScanner) }) {
                    Text("Scan Barcode")
                }
            }
        }

        else -> {
            Text(
                text = "Unsupported: ${question.questionType}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Per-question constraint message
    if (question.constraintMessage != null) {
        Text(
            text = question.constraintMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun formatGeoPoint(value: String): String {
    val parts = value.split(" ")
    return when {
        parts.size >= 4 -> "Lat: ${parts[0]}, Lon: ${parts[1]}, Alt: ${parts[2]}m, Acc: ${parts[3]}m"
        parts.size >= 2 -> "Lat: ${parts[0]}, Lon: ${parts[1]}"
        else -> value
    }
}
