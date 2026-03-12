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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.commcare.app.viewmodel.FormEntryViewModel
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
                Button(onClick = {
                    viewModel.submitForm()
                    onComplete()
                }) {
                    Text("Submit")
                }
            }
        } else {
            // Question display
            val question = viewModel.currentQuestion
            if (question != null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                ) {
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // Simple input for text questions
                    when (question.questionType) {
                        QuestionType.TEXT,
                        QuestionType.INTEGER,
                        QuestionType.DECIMAL -> {
                            OutlinedTextField(
                                value = question.answer ?: "",
                                onValueChange = { viewModel.answerQuestion(it) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Answer") }
                            )
                        }
                        QuestionType.LABEL -> {
                            // Label only, no input
                        }
                        else -> {
                            Text(
                                text = "Question type: ${question.questionType}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Validation message
                    if (viewModel.validationMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = viewModel.validationMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Navigation buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(onClick = {
                            if (!viewModel.previousQuestion()) {
                                onBack()
                            }
                        }) {
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
