package org.commcare.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import org.commcare.app.platform.PlatformAudioCapture
import org.commcare.app.platform.PlatformBarcodeScanner
import org.commcare.app.platform.PlatformDocumentPicker
import org.commcare.app.platform.PlatformImageCapture
import org.commcare.app.platform.PlatformLocationProvider
import org.commcare.app.platform.PlatformSignatureCapture
import org.commcare.app.platform.PlatformVideoCapture
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
                    .clickable { onBack() }
                    .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                    .padding(end = 8.dp)
            )
            Text(
                text = viewModel.formTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
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
        )

        HorizontalDivider()

        if (viewModel.errorMessage != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Form Error",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = viewModel.errorMessage!!,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(onClick = { onBack() }) {
                    Text("Go Back")
                }
            }
        } else if (viewModel.isSubmitting) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
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
                    style = MaterialTheme.typography.headlineMedium
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
    // Question label with optional inline multimedia
    MediaLabel(question)
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
            val appearance = question.appearance ?: ""
            when {
                appearance.contains("minimal") -> {
                    SelectOneMinimal(question, enabled) { choice ->
                        viewModel.answerQuestionString(index, choice)
                    }
                }
                appearance.contains("compact") -> {
                    val columns = parseCompactColumns(appearance)
                    SelectCompact(question.choices, question.answer, setOf(), columns, enabled,
                        singleSelect = true) { choice ->
                        viewModel.answerQuestionString(index, choice)
                    }
                }
                appearance.contains("quick") -> {
                    SelectOneQuick(question, enabled) { choice ->
                        val accepted = viewModel.answerQuestionString(index, choice)
                        if (accepted) {
                            viewModel.nextQuestion()
                        }
                    }
                }
                appearance.contains("combobox") -> {
                    SelectOneCombobox(question, appearance, enabled) { choice ->
                        viewModel.answerQuestionString(index, choice)
                    }
                }
                appearance.contains("list-nolabel") -> {
                    SelectOneNoLabel(question, enabled) { choice ->
                        viewModel.answerQuestionString(index, choice)
                    }
                }
                appearance.contains("label") -> {
                    SelectOneLabel(question)
                }
                else -> {
                    // Default radio buttons
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
            }
        }

        QuestionType.SELECT_MULTI -> {
            val appearance = question.appearance ?: ""
            when {
                appearance.contains("minimal") -> {
                    SelectMultiMinimal(question, enabled) { choice ->
                        viewModel.toggleMultiSelectChoice(index, choice)
                    }
                }
                appearance.contains("compact") -> {
                    val columns = parseCompactColumns(appearance)
                    SelectCompact(question.choices, question.answer, question.selectedChoices,
                        columns, enabled, singleSelect = false) { choice ->
                        viewModel.toggleMultiSelectChoice(index, choice)
                    }
                }
                appearance.contains("list-nolabel") -> {
                    SelectMultiNoLabel(question, enabled) { choice ->
                        viewModel.toggleMultiSelectChoice(index, choice)
                    }
                }
                appearance.contains("label") -> {
                    SelectMultiLabel(question)
                }
                else -> {
                    // Default checkboxes
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

        QuestionType.VIDEO -> {
            if (enabled) {
                val videoCapture = remember { PlatformVideoCapture() }
                Button(onClick = {
                    videoCapture.captureVideo { path ->
                        if (path != null) viewModel.answerQuestionString(index, path)
                    }
                }) {
                    Text("Record Video")
                }
            }
            if (question.answer.isNotBlank()) {
                Text(
                    text = "Video: ${question.answer}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        QuestionType.UPLOAD -> {
            if (enabled) {
                val documentPicker = remember { PlatformDocumentPicker() }
                Button(onClick = {
                    documentPicker.pickDocument { path ->
                        if (path != null) viewModel.answerQuestionString(index, path)
                    }
                }) {
                    Text("Choose File")
                }
            }
            if (question.answer.isNotBlank()) {
                Text(
                    text = "File: ${question.answer.substringAfterLast("/")}",
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

        QuestionType.DATETIME -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val parts = question.answer.split("T", limit = 2)
                val datePart = parts.getOrElse(0) { "" }
                val timePart = parts.getOrElse(1) { "" }
                OutlinedTextField(
                    value = datePart,
                    onValueChange = { viewModel.answerQuestionString(index, "${it}T${timePart}") },
                    modifier = Modifier.weight(1f),
                    label = { Text("Date") },
                    singleLine = true,
                    enabled = enabled
                )
                OutlinedTextField(
                    value = timePart,
                    onValueChange = { viewModel.answerQuestionString(index, "${datePart}T${it}") },
                    modifier = Modifier.weight(1f),
                    label = { Text("Time") },
                    singleLine = true,
                    enabled = enabled
                )
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

// -- Select Appearance Variants --

// -- Inline Multimedia --

/** Render inline multimedia (image/audio) associated with a question label. */
@Composable
private fun MediaLabel(question: QuestionState) {
    // Inline image from itext
    if (question.imageUri != null) {
        Text(
            text = "[Image: ${question.imageUri}]",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
    // Inline audio play button from itext
    if (question.audioUri != null) {
        OutlinedButton(
            onClick = { /* TODO: platform audio playback */ },
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Text("Play Audio")
        }
    }
}

/** Extract column count from "compact-N" appearance, defaulting to 2. */
private fun parseCompactColumns(appearance: String): Int {
    val match = Regex("""compact-(\d+)""").find(appearance)
    return match?.groupValues?.get(1)?.toIntOrNull() ?: 2
}

/** Minimal appearance: dropdown/spinner for SELECT_ONE. */
@Composable
private fun SelectOneMinimal(
    question: QuestionState,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = question.answer.ifEmpty { "Select..." })
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (choice in question.choices) {
                DropdownMenuItem(
                    text = { Text(choice) },
                    onClick = {
                        onSelect(choice)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Minimal appearance for SELECT_MULTI: dropdown with checkboxes. */
@Composable
private fun SelectMultiMinimal(
    question: QuestionState,
    enabled: Boolean,
    onToggle: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val summary = question.selectedChoices.joinToString(", ").ifEmpty { "Select..." }
    Box {
        OutlinedButton(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = summary, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (choice in question.choices) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = question.selectedChoices.contains(choice),
                                onCheckedChange = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(choice)
                        }
                    },
                    onClick = { onToggle(choice) }
                )
            }
        }
    }
}

/** Compact appearance: grid layout for both SELECT_ONE and SELECT_MULTI. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectCompact(
    choices: List<String>,
    currentAnswer: String,
    selectedChoices: Set<String>,
    columns: Int,
    enabled: Boolean,
    singleSelect: Boolean,
    onSelect: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        maxItemsInEachRow = columns
    ) {
        for (choice in choices) {
            val isSelected = if (singleSelect) currentAnswer == choice else selectedChoices.contains(choice)
            OutlinedCard(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp)
                    .clickable(enabled = enabled) { onSelect(choice) },
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                ),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Text(
                    text = choice,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Quick appearance: radio buttons that auto-advance on selection. */
@Composable
private fun SelectOneQuick(
    question: QuestionState,
    enabled: Boolean,
    onSelectAndAdvance: (String) -> Unit
) {
    for (choice in question.choices) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = (if (enabled) Modifier.clickable {
                onSelectAndAdvance(choice)
            } else Modifier).defaultMinSize(minHeight = 44.dp)
        ) {
            RadioButton(
                selected = question.answer == choice,
                onClick = if (enabled) {{ onSelectAndAdvance(choice) }} else null
            )
            Text(text = choice)
        }
    }
}

/** Combobox appearance: text field with filterable dropdown. */
@Composable
private fun SelectOneCombobox(
    question: QuestionState,
    appearance: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var text by remember(question.answer) { mutableStateOf(question.answer) }
    var expanded by remember { mutableStateOf(false) }
    val isFuzzy = appearance.contains("fuzzy")
    val isMultiword = appearance.contains("multiword")

    val filtered = question.choices.filter { choice ->
        if (text.isBlank()) true
        else if (isFuzzy) {
            choice.lowercase().contains(text.lowercase())
        } else if (isMultiword) {
            text.lowercase().split(" ").all { word ->
                choice.lowercase().contains(word)
            }
        } else {
            choice.lowercase().startsWith(text.lowercase())
        }
    }

    Column {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                expanded = true
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search...") },
            singleLine = true,
            enabled = enabled
        )
        DropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            for (choice in filtered.take(20)) {
                DropdownMenuItem(
                    text = { Text(choice) },
                    onClick = {
                        text = choice
                        onSelect(choice)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Label appearance: show only label text, no selection controls. */
@Composable
private fun SelectOneLabel(question: QuestionState) {
    for (choice in question.choices) {
        Text(
            text = choice,
            style = MaterialTheme.typography.bodyMedium,
            color = if (question.answer == choice) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

/** Label appearance for SELECT_MULTI. */
@Composable
private fun SelectMultiLabel(question: QuestionState) {
    for (choice in question.choices) {
        Text(
            text = choice,
            style = MaterialTheme.typography.bodyMedium,
            color = if (question.selectedChoices.contains(choice)) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

/** List-nolabel appearance: show only radio buttons, no labels. */
@Composable
private fun SelectOneNoLabel(
    question: QuestionState,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (choice in question.choices) {
            RadioButton(
                selected = question.answer == choice,
                onClick = if (enabled) {{ onSelect(choice) }} else null
            )
        }
    }
}

/** List-nolabel appearance for SELECT_MULTI: checkboxes only, no labels. */
@Composable
private fun SelectMultiNoLabel(
    question: QuestionState,
    enabled: Boolean,
    onToggle: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (choice in question.choices) {
            Checkbox(
                checked = question.selectedChoices.contains(choice),
                onCheckedChange = if (enabled) {{ onToggle(choice) }} else null,
                enabled = enabled
            )
        }
    }
}

/** Collapsible group section — for group-border, collapse-open, collapse-closed appearances. */
@Composable
fun CollapsibleGroupSection(
    title: String,
    appearance: String?,
    content: @Composable () -> Unit
) {
    val startExpanded = appearance?.contains("collapse-closed") != true
    var expanded by remember { mutableStateOf(startExpanded) }

    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(text = if (expanded) "v" else ">", style = MaterialTheme.typography.titleSmall)
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    content()
                }
            }
        }
    }
}
