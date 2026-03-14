package org.commcare.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
                modifier = Modifier.clickable { onBack() }.padding(end = 8.dp)
            )
            Text(
                text = viewModel.formTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
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
                        modifier = Modifier.clickable {
                            if (languageViewModel.setLanguage(lang)) {
                                viewModel.refreshAfterLanguageChange()
                            }
                        }.padding(horizontal = 8.dp, vertical = 4.dp)
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

        if (viewModel.isSubmitting) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
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
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp)
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
                    modifier = if (enabled) Modifier.clickable {
                        viewModel.answerQuestionString(index, choice)
                    } else Modifier
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
                    modifier = if (enabled) Modifier.clickable {
                        viewModel.toggleMultiSelectChoice(index, choice)
                    } else Modifier
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
