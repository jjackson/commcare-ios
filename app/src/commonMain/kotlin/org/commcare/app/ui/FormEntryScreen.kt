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
import org.commcare.app.viewmodel.FormEntryViewModel
import org.commcare.app.viewmodel.QuestionState
import org.commcare.app.viewmodel.QuestionType

@Composable
fun FormEntryScreen(
    viewModel: FormEntryViewModel,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
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
                            QuestionWidget(question, index, viewModel)
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Validation message
                        if (viewModel.validationMessage != null) {
                            Text(
                                text = viewModel.validationMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
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
                        Button(onClick = { viewModel.nextQuestion() }) {
                            Text("Next")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionWidget(
    question: QuestionState,
    index: Int,
    viewModel: FormEntryViewModel
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
                minLines = if (isMultiline) 3 else 1
            )
        }

        QuestionType.INTEGER -> {
            OutlinedTextField(
                value = question.answer,
                onValueChange = { viewModel.answerQuestionString(index, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Integer") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }

        QuestionType.DECIMAL -> {
            OutlinedTextField(
                value = question.answer,
                onValueChange = { viewModel.answerQuestionString(index, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Decimal") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        }

        QuestionType.DATE -> {
            OutlinedTextField(
                value = question.answer,
                onValueChange = { viewModel.answerQuestionString(index, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Date (YYYY-MM-DD)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true
            )
        }

        QuestionType.TIME -> {
            OutlinedTextField(
                value = question.answer,
                onValueChange = { viewModel.answerQuestionString(index, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Time (HH:MM)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true
            )
        }

        QuestionType.SELECT_ONE -> {
            for (choice in question.choices) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        viewModel.answerQuestionString(index, choice)
                    }
                ) {
                    RadioButton(
                        selected = question.answer == choice,
                        onClick = { viewModel.answerQuestionString(index, choice) }
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
                    modifier = Modifier.clickable {
                        viewModel.toggleMultiSelectChoice(index, choice)
                    }
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { viewModel.toggleMultiSelectChoice(index, choice) }
                    )
                    Text(text = choice)
                }
            }
        }

        QuestionType.TRIGGER -> {
            Button(
                onClick = { viewModel.answerQuestionString(index, "OK") }
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
}
