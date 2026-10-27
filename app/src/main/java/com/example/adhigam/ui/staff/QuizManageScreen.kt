package com.example.adhigam.ui.staff

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.Quiz
import com.example.adhigam.data.model.QuizDraft
import com.example.adhigam.data.model.SchoolClasses
import com.example.adhigam.data.model.SchoolSections
import com.example.adhigam.data.model.classDisplayName
import com.example.adhigam.data.repository.QuizRepository
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.StatusPill
import com.example.adhigam.ui.theme.Amber600
import com.example.adhigam.ui.theme.SuccessGreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Class / section filter of quiz-manage.html; null = all.
data class QuizFilter(val classType: String? = null, val section: String? = null)

class QuizManageViewModel(
    private val repository: QuizRepository,
    private val filterState: MutableStateFlow<QuizFilter> = MutableStateFlow(QuizFilter())
) : LoadViewModel<List<Quiz>>({ repository.getQuizzes(filterState.value.classType, filterState.value.section) }) {

    val filter: StateFlow<QuizFilter> = filterState.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun setFilter(filter: QuizFilter) {
        if (filter == filterState.value) return
        filterState.value = filter
        refresh()
    }

    // onResult gets null on success or the error to show.
    fun save(quiz: QuizDraft, onResult: (error: String?) -> Unit) = run({ repository.saveQuiz(quiz) }, onResult)

    fun delete(quiz: Quiz, onResult: (error: String?) -> Unit) = run({ repository.deleteQuiz(quiz.id) }, onResult)

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

// Mirrors the web portal's quiz-manage.html: quizzes filtered by class / section, each Active or Draft.
// Tapping a quiz opens its questions and results. Unlike the web page, a quiz can also be edited here,
// which is the only way to publish a quiz saved as a draft.
@Composable
fun QuizManageScreen(
    onOpenQuiz: (Quiz) -> Unit,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuizManageViewModel = viewModel { QuizManageViewModel(AppContainer.quizRepository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    // Question counts change on the quiz screen, so reload when coming back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reloadIfLoaded() }

    var editing by rememberSaveable { mutableStateOf<Int?>(null) }  // quiz id being edited, 0 = new quiz
    var deleting by rememberSaveable { mutableStateOf<Int?>(null) }
    val quizzes = state.data.orEmpty()

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            QuizFilterRow(filter, onChange = viewModel::setFilter)
            LoadStateContent(state, onRetry = viewModel::refresh) { list ->
                QuizList(
                    quizzes = list,
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    onOpen = onOpenQuiz,
                    onEdit = { editing = it.id },
                    onDelete = { deleting = it.id }
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = 0 },
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("New Quiz") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    editing?.let { id ->
        QuizFormDialog(
            initial = quizzes.firstOrNull { it.id == id },
            defaultFilter = filter,
            isSaving = isSaving,
            onDismiss = { if (!isSaving) editing = null },
            onSave = { draft ->
                viewModel.save(draft.copy(id = id)) { error ->
                    if (error == null) {
                        editing = null
                        showMessage("Quiz saved")
                    } else {
                        showMessage(error)
                    }
                }
            }
        )
    }
    deleting?.let { id ->
        val quiz = quizzes.firstOrNull { it.id == id }
        if (quiz == null) {
            deleting = null
        } else {
            AlertDialog(
                onDismissRequest = { if (!isSaving) deleting = null },
                title = { Text("Delete quiz?") },
                text = { Text("\"${quiz.title}\" and all its questions will be deleted.") },
                confirmButton = {
                    TextButton(enabled = !isSaving, onClick = {
                        viewModel.delete(quiz) { error ->
                            deleting = null
                            showMessage(error ?: "Quiz deleted")
                        }
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { deleting = null }, enabled = !isSaving) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun QuizFilterRow(filter: QuizFilter, onChange: (QuizFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SelectField(
            label = "Class",
            placeholder = "All Classes",
            value = filter.classType?.let(::classDisplayName),
            options = listOf<String?>(null) + SchoolClasses,
            optionLabel = { it?.let(::classDisplayName) ?: "All Classes" },
            onSelect = { onChange(filter.copy(classType = it)) },
            modifier = Modifier.weight(1f)
        )
        SelectField(
            label = "Section",
            placeholder = "All",
            value = filter.section,
            options = listOf<String?>(null) + SchoolSections,
            optionLabel = { it ?: "All" },
            onSelect = { onChange(filter.copy(section = it)) },
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuizList(
    quizzes: List<Quiz>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpen: (Quiz) -> Unit,
    onEdit: (Quiz) -> Unit,
    onDelete: (Quiz) -> Unit
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Room at the bottom so the last row isn't hidden behind the New Quiz button.
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp)
        ) {
            item {
                ListCard {
                    if (quizzes.isEmpty()) {
                        EmptyText("No quizzes found")
                    }
                    quizzes.forEachIndexed { index, quiz ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        QuizManageRow(quiz, onOpen = { onOpen(quiz) }, onEdit = { onEdit(quiz) }, onDelete = { onDelete(quiz) })
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizManageRow(quiz: Quiz, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    quiz.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                if (quiz.isActive) StatusPill("Active", SuccessGreen) else StatusPill("Draft", Amber600)
            }
            Text(
                listOf(
                    "${classDisplayName(quiz.classType)} ${quiz.section}".trim(),
                    quiz.subject,
                    "${quiz.questionCount} Qs",
                    "${quiz.durationMins} min"
                ).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Outlined.Edit, contentDescription = "Edit quiz", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete quiz", tint = MaterialTheme.colorScheme.error)
        }
    }
}

// The web "New Quiz" modal; also used to edit a quiz (initial != null).
@Composable
private fun QuizFormDialog(
    initial: Quiz?,
    defaultFilter: QuizFilter,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (QuizDraft) -> Unit
) {
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var classType by rememberSaveable { mutableStateOf(initial?.classType ?: defaultFilter.classType) }
    var section by rememberSaveable { mutableStateOf(initial?.section ?: defaultFilter.section ?: "A") }
    var subject by rememberSaveable { mutableStateOf(initial?.subject.orEmpty()) }
    var duration by rememberSaveable { mutableStateOf((initial?.durationMins ?: 30).toString()) }
    var isActive by rememberSaveable { mutableStateOf(initial?.isActive ?: false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Quiz" else "Edit Quiz") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("e.g. Chapter 3 Test") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SelectField(
                    label = "Class",
                    placeholder = "Select Class",
                    value = classType?.let(::classDisplayName),
                    options = SchoolClasses,
                    optionLabel = ::classDisplayName,
                    onSelect = { classType = it }
                )
                SelectField(
                    label = "Section",
                    placeholder = "Select Section",
                    value = section,
                    options = SchoolSections,
                    optionLabel = { it },
                    onSelect = { section = it }
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    placeholder = { Text("e.g. Mathematics") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { text -> duration = text.filter(Char::isDigit).take(3) },
                    label = { Text("Duration (mins)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Active", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Visible to students",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    val cls = classType
                    val minutes = duration.toIntOrNull()
                    // Same required fields as quiz-manage.html.
                    if (title.isBlank() || cls == null) {
                        error = "Title and class are required"
                    } else if (minutes == null || minutes < 1) {
                        error = "Enter the duration in minutes"
                    } else {
                        error = null
                        onSave(QuizDraft(initial?.id ?: 0, title.trim(), cls, section, subject.trim(), minutes, isActive))
                    }
                }
            ) { Text(if (isSaving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") } }
    )
}
