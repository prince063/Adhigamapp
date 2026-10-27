package com.example.adhigam.ui.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.Quiz
import com.example.adhigam.data.model.QuizOption
import com.example.adhigam.data.model.QuizQuestion
import com.example.adhigam.data.model.QuizResult
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ErrorState
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.StatusPill
import com.example.adhigam.ui.theme.AdhigamTheme
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.SuccessGreen

// Mirrors the web portal's quiz-attempt.html (Student role): pick an active quiz, answer the
// multiple-choice questions against a countdown, then see the score.
@Composable
fun QuizScreen(
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuizViewModel = viewModel { QuizViewModel(AppContainer.quizRepository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val attempt = state.attempt
    val result = state.result

    when {
        attempt != null -> {
            var confirmLeave by rememberSaveable { mutableStateOf(false) }
            var confirmSubmit by rememberSaveable { mutableStateOf(false) }
            BackHandler { confirmLeave = true }

            AttemptContent(
                attempt = attempt,
                onPick = viewModel::pickAnswer,
                onSubmit = { confirmSubmit = true },
                modifier = modifier
            )
            if (confirmSubmit) {
                AlertDialog(
                    onDismissRequest = { confirmSubmit = false },
                    title = { Text("Submit quiz?") },
                    text = {
                        Text(
                            buildString {
                                if (attempt.unanswered > 0) append("${attempt.unanswered} question(s) are not answered. ")
                                append("You cannot change answers after this.")
                            }
                        )
                    },
                    confirmButton = {
                        Button(onClick = { confirmSubmit = false; viewModel.submit() }) { Text("Submit") }
                    },
                    dismissButton = { TextButton(onClick = { confirmSubmit = false }) { Text("Keep answering") } }
                )
            }
            if (confirmLeave) {
                AlertDialog(
                    onDismissRequest = { confirmLeave = false },
                    title = { Text("Leave quiz?") },
                    text = { Text("Your answers will be lost. The quiz is not submitted.") },
                    confirmButton = {
                        TextButton(onClick = { confirmLeave = false; viewModel.abandonAttempt() }) {
                            Text("Leave", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } }
                )
            }
        }
        result != null -> {
            BackHandler(onBack = viewModel::backToList)
            ResultContent(result.first, result.second, onBack = viewModel::backToList, modifier = modifier)
        }
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.quizzes == null -> ErrorState(
            message = state.error ?: "Could not load quizzes",
            onRetry = viewModel::refresh,
            modifier = modifier
        )
        else -> QuizListContent(
            quizzes = state.quizzes!!,
            startingQuizId = state.startingQuizId,
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            onStart = { viewModel.startQuiz(it, showMessage) },
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuizListContent(
    quizzes: List<Quiz>,
    startingQuizId: Int?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onStart: (Quiz) -> Unit,
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Text("Available quizzes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${quizzes.size} active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
            item {
                ListCard {
                    if (quizzes.isEmpty()) {
                        EmptyText("No active quizzes available")
                    }
                    quizzes.forEachIndexed { index, quiz ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        QuizRow(quiz, isStarting = quiz.id == startingQuizId, enabled = startingQuizId == null) {
                            onStart(quiz)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizRow(quiz: Quiz, isStarting: Boolean, enabled: Boolean, onStart: () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Quiz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                quiz.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(
                    quiz.subject,
                    "${quiz.classType} ${quiz.section}".trim(),
                    "${quiz.questionCount} questions",
                    "${quiz.durationMins} min"
                ).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onStart, enabled = enabled, contentPadding = PaddingValues(horizontal = 16.dp)) {
            if (isStarting) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text("Start")
            }
        }
    }
}

@Composable
private fun AttemptContent(
    attempt: QuizAttempt,
    onPick: (questionId: Int, option: String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val answered = attempt.questions.size - attempt.unanswered
    Column(modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        attempt.quiz.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TimerChip(attempt.secondsLeft)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (attempt.questions.isEmpty()) 0f else answered.toFloat() / attempt.questions.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$answered of ${attempt.questions.size} answered",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(attempt.questions, key = { _, q -> q.id }) { index, question ->
                QuestionCard(
                    number = index + 1,
                    question = question,
                    selected = attempt.answers[question.id],
                    enabled = !attempt.isSubmitting,
                    onPick = { onPick(question.id, it) }
                )
            }
        }
        Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                attempt.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                }
                Button(onClick = onSubmit, enabled = !attempt.isSubmitting, modifier = Modifier.fillMaxWidth()) {
                    if (attempt.isSubmitting) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Submitting…")
                    } else {
                        Text(if (attempt.secondsLeft == 0) "Time up — submit again" else "Submit quiz")
                    }
                }
            }
        }
    }
}

// Turns red in the last minute, like the web page's timer-warn.
@Composable
private fun TimerChip(secondsLeft: Int) {
    val warn = secondsLeft <= 60
    val color = if (warn) ErrorRed else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Timer, contentDescription = "Time left", tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            "%02d:%02d".format(secondsLeft / 60, secondsLeft % 60),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun QuestionCard(
    number: Int,
    question: QuizQuestion,
    selected: String?,
    enabled: Boolean,
    onPick: (String) -> Unit
) {
    ListCard {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Q$number. ${question.text}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (question.marks == 1) "1 mark" else "${question.marks} marks",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            question.options.forEach { option ->
                OptionRow(option, isSelected = option.key == selected, enabled = enabled) { onPick(option.key) }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun OptionRow(option: QuizOption, isSelected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = null, enabled = enabled, modifier = Modifier.padding(12.dp))
        Text("${option.key}. ${option.text}", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ResultContent(quiz: Quiz, result: QuizResult, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Quiz submitted", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(
            "${result.score} / ${result.maxScore}",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text("${result.percent}% — ${quiz.title}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        if (result.passed) StatusPill("PASS", SuccessGreen) else StatusPill("FAIL", ErrorRed)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text("Back to quizzes") }
    }
}

@Preview(showBackground = true)
@Composable
private fun QuizListPreview() {
    // Preview-only sample; the app loads /academics/quizzes.
    AdhigamTheme {
        QuizListContent(
            quizzes = listOf(
                Quiz(1, "Algebra basics", "10th", "A", "Mathematics", 20, 10),
                Quiz(2, "Cell structure", "10th", "A", "Science", 30, 15)
            ),
            startingQuizId = null,
            isRefreshing = false,
            onRefresh = {},
            onStart = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun AttemptPreview() {
    val questions = listOf(
        QuizQuestion(1, "What is 2 + 2?", listOf(QuizOption("A", "3"), QuizOption("B", "4"), QuizOption("C", "5")), 1),
        QuizQuestion(2, "Solve x + 3 = 5", listOf(QuizOption("A", "1"), QuizOption("B", "2")), 2)
    )
    AdhigamTheme {
        AttemptContent(
            attempt = QuizAttempt(
                quiz = Quiz(1, "Algebra basics", "10th", "A", "Mathematics", 20, 2),
                questions = questions,
                answers = mapOf(1 to "B"),
                secondsLeft = 754
            ),
            onPick = { _, _ -> },
            onSubmit = {}
        )
    }
}
