package com.example.adhigam.ui.staff

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.QuizAttemptResult
import com.example.adhigam.data.model.QuizQuestion
import com.example.adhigam.data.model.QuizQuestionDraft
import com.example.adhigam.data.repository.QuizRepository
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.StatusPill
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.SuccessGreen
import com.example.adhigam.ui.util.formatDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuizDetail(val questions: List<QuizQuestion>, val results: List<QuizAttemptResult>)

class QuizDetailViewModel(private val repository: QuizRepository, private val quizId: Int) :
    LoadViewModel<QuizDetail>({
        coroutineScope {
            val questions = async { repository.getQuestions(quizId) }
            val results = async { repository.getResults(quizId) }
            QuizDetail(questions.await(), results.await())
        }
    }) {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    // onResult gets null on success or the error to show.
    fun saveQuestion(question: QuizQuestionDraft, onResult: (error: String?) -> Unit) =
        run({ repository.saveQuestion(quizId, question) }, onResult)

    fun deleteQuestion(question: QuizQuestion, onResult: (error: String?) -> Unit) =
        run({ repository.deleteQuestion(question.id) }, onResult)

    private fun run(action: suspend () -> Unit, onResult: (error: String?) -> Unit) {
        if (_isSaving.value) return
        _isSaving.update { true }
        viewModelScope.launch {
            try {
                action()
                onResult(null)
                refresh()
            } catch (e: Exception) {
                onResult(e.message ?: "Request failed")
            } finally {
                _isSaving.update { false }
            }
        }
    }
}

// One quiz of quiz-manage.html: its questions (correct option highlighted, add / edit / remove) and the
// students' results, which the web page links to a quiz-results.html that does not exist.
@Composable
fun QuizDetailScreen(
    quizId: Int,
    title: String,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuizDetailViewModel = viewModel(key = "quiz-$quizId") {
        QuizDetailViewModel(AppContainer.quizRepository, quizId)
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }  // question id being edited, 0 = new
    var deleting by rememberSaveable { mutableStateOf<Int?>(null) }
    val questions = state.data?.questions.orEmpty()

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                val counts = state.data
                listOf(
                    "Questions" + (counts?.let { " (${it.questions.size})" } ?: ""),
                    "Results" + (counts?.let { " (${it.results.size})" } ?: "")
                ).forEachIndexed { index, label ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) })
                }
            }
            LoadStateContent(state, onRetry = viewModel::refresh) { detail ->
                if (selectedTab == 0) {
                    QuestionsTab(
                        questions = detail.questions,
                        isRefreshing = state.isRefreshing,
                        onRefresh = viewModel::refresh,
                        onEdit = { editing = it.id },
                        onDelete = { deleting = it.id }
                    )
                } else {
                    ResultsTab(detail.results, isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh)
                }
            }
        }
        if (selectedTab == 0 && state.data != null) {
            ExtendedFloatingActionButton(
                onClick = { editing = 0 },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add Question") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }

    editing?.let { id ->
        QuestionFormDialog(
            initial = questions.firstOrNull { it.id == id },
            isSaving = isSaving,
            onDismiss = { if (!isSaving) editing = null },
            onSave = { draft ->
                viewModel.saveQuestion(draft.copy(id = id)) { error ->
                    if (error == null) {
                        editing = null
                        showMessage("Question saved")
                    } else {
                        showMessage(error)
                    }
                }
            }
        )
    }
    deleting?.let { id ->
        val question = questions.firstOrNull { it.id == id }
        if (question == null) {
            deleting = null
        } else {
            AlertDialog(
                onDismissRequest = { if (!isSaving) deleting = null },
                title = { Text("Remove this question?") },
                text = { Text(question.text, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                confirmButton = {
                    TextButton(enabled = !isSaving, onClick = {
                        viewModel.deleteQuestion(question) { error ->
                            deleting = null
                            showMessage(error ?: "Question removed")
                        }
                    }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { deleting = null }, enabled = !isSaving) { Text("Cancel") } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionsTab(
    questions: List<QuizQuestion>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onEdit: (QuizQuestion) -> Unit,
    onDelete: (QuizQuestion) -> Unit
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Room at the bottom so the last card isn't hidden behind the Add Question button.
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (questions.isEmpty()) {
                item { ListCard { EmptyText("No questions yet. Tap \"Add Question\" to begin.") } }
            } else {
                item {
                    Text(
                        "${questions.size} questions • ${questions.sumOf { it.marks }} marks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            itemsIndexed(questions, key = { _, q -> q.id }) { index, question ->
                QuestionCard(index + 1, question, onEdit = { onEdit(question) }, onDelete = { onDelete(question) })
            }
        }
    }
}

@Composable
private fun QuestionCard(number: Int, question: QuizQuestion, onEdit: () -> Unit, onDelete: () -> Unit) {
    ListCard {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Q$number. ${question.text}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (question.marks == 1) "1 mark" else "${question.marks} marks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit question", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Remove question", tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(6.dp))
            question.options.forEach { option ->
                val correct = option.key == question.correctOption
                Row(
                    modifier = Modifier
                        .padding(end = 12.dp, bottom = 4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (correct) SuccessGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${option.key}: ${option.text}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (correct) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (correct) SuccessGreen else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (correct) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = "Correct answer", tint = SuccessGreen, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultsTab(results: List<QuizAttemptResult>, isRefreshing: Boolean, onRefresh: () -> Unit) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (results.isNotEmpty()) {
                item { ResultsSummary(results) }
            }
            item {
                ListCard {
                    if (results.isEmpty()) {
                        EmptyText("No student has attempted this quiz yet")
                    }
                    results.forEachIndexed { index, attempt ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        ResultRow(rank = index + 1, attempt = attempt)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsSummary(results: List<QuizAttemptResult>) {
    val average = results.map { it.result.percent }.average().toInt()
    val passed = results.count { it.result.passed }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SummaryTile("Attempts", results.size.toString(), Modifier.weight(1f))
        SummaryTile("Average", "$average%", Modifier.weight(1f))
        SummaryTile("Passed", "$passed / ${results.size}", Modifier.weight(1f))
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    ListCard(modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultRow(rank: Int, attempt: QuizAttemptResult) {
    val result = attempt.result
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text("$rank", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(attempt.studentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(
                    attempt.admissionNo.takeIf { it.isNotBlank() }?.let { "Adm. no $it" },
                    attempt.submittedAt?.let(::formatDateTime)
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("${result.score} / ${result.maxScore}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            if (result.passed) StatusPill("${result.percent}% PASS", SuccessGreen) else StatusPill("${result.percent}% FAIL", ErrorRed)
        }
    }
}

// The web "Add Question" modal; also used to edit a question (initial != null).
@Composable
private fun QuestionFormDialog(
    initial: QuizQuestion?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (QuizQuestionDraft) -> Unit
) {
    fun optionText(key: String) = initial?.options?.firstOrNull { it.key == key }?.text.orEmpty()

    var text by rememberSaveable { mutableStateOf(initial?.text.orEmpty()) }
    var optionA by rememberSaveable { mutableStateOf(optionText("A")) }
    var optionB by rememberSaveable { mutableStateOf(optionText("B")) }
    var optionC by rememberSaveable { mutableStateOf(optionText("C")) }
    var optionD by rememberSaveable { mutableStateOf(optionText("D")) }
    var correct by rememberSaveable { mutableStateOf(initial?.correctOption ?: "A") }
    var marks by rememberSaveable { mutableStateOf((initial?.marks ?: 1).toString()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val options = mapOf("A" to optionA, "B" to optionB, "C" to optionC, "D" to optionD)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Question" else "Edit Question") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Question") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OptionField("Option A", optionA) { optionA = it }
                OptionField("Option B", optionB) { optionB = it }
                OptionField("Option C (optional)", optionC) { optionC = it }
                OptionField("Option D (optional)", optionD) { optionD = it }
                Text("Correct option", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.keys.forEach { key ->
                        FilterChip(selected = correct == key, onClick = { correct = key }, label = { Text(key) })
                    }
                }
                OutlinedTextField(
                    value = marks,
                    onValueChange = { value -> marks = value.filter(Char::isDigit).take(3) },
                    label = { Text("Marks") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    val points = marks.toIntOrNull()
                    error = when {
                        // Same required fields as quiz-manage.html.
                        text.isBlank() || optionA.isBlank() || optionB.isBlank() -> "Question and Options A, B are required"
                        options[correct].isNullOrBlank() -> "The correct option ($correct) has no text"
                        points == null || points < 1 -> "Marks must be at least 1"
                        else -> null
                    }
                    if (error == null && points != null) {
                        onSave(
                            QuizQuestionDraft(
                                id = initial?.id ?: 0,
                                text = text.trim(),
                                optionA = optionA.trim(),
                                optionB = optionB.trim(),
                                optionC = optionC.trim(),
                                optionD = optionD.trim(),
                                correctOption = correct,
                                marks = points
                            )
                        )
                    }
                }
            ) { Text(if (isSaving) "Saving…" else "Save Question") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") } }
    )
}

@Composable
private fun OptionField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
