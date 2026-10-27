package com.example.adhigam.ui.staff

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.Assignment
import com.example.adhigam.data.model.SchoolClasses
import com.example.adhigam.data.model.SchoolSections
import com.example.adhigam.data.model.classDisplayName
import com.example.adhigam.data.remote.UploadPart
import com.example.adhigam.data.repository.StaffRepository
import com.example.adhigam.ui.assignments.AssignmentCard
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.util.openAssignmentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class ManageAssignmentsViewModel(private val repository: StaffRepository) :
    LoadViewModel<List<Assignment>>({ repository.getAssignments() }) {

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    // Reads the picked file and uploads it; onResult gets null on success or the error to show.
    fun upload(
        classType: String,
        section: String,
        subject: String,
        title: String,
        file: Uri,
        resolver: ContentResolver,
        onResult: (error: String?) -> Unit
    ) {
        if (_isUploading.value) return
        _isUploading.value = true
        viewModelScope.launch {
            try {
                val part = withContext(Dispatchers.IO) { resolver.readUploadPart(file) }
                repository.uploadAssignment(classType, section, subject, title, part)
                onResult(null)
                refresh()
            } catch (e: Exception) {
                onResult(e.message ?: "Upload failed")
            } finally {
                _isUploading.value = false
            }
        }
    }
}

// Mirrors assignment.html + upload-assignment.html for teachers: their uploaded assignments and a form to add one.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAssignmentsScreen(
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ManageAssignmentsViewModel = viewModel { ManageAssignmentsViewModel(AppContainer.staffRepository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isUploading by viewModel.isUploading.collectAsStateWithLifecycle()
    var showUpload by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val today = LocalDate.now()

    Box(modifier.fillMaxSize()) {
        LoadStateContent(state, onRetry = viewModel::refresh) { assignments ->
            PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Room at the bottom so the last card isn't hidden behind the upload button.
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (assignments.isEmpty()) {
                        item { ListCard { EmptyText("No assignments uploaded yet") } }
                    }
                    items(assignments.sortedByDescending { it.createdDate }, key = { it.fileUrl + it.title + it.createdDate }) { assignment ->
                        AssignmentCard(assignment, today, onDownload = {
                            if (!context.openAssignmentFile(it)) showMessage("Could not open the assignment file")
                        })
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showUpload = true },
            icon = { Icon(Icons.Outlined.Upload, contentDescription = null) },
            text = { Text("Upload") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    if (showUpload) {
        UploadAssignmentDialog(
            isUploading = isUploading,
            onDismiss = { if (!isUploading) showUpload = false },
            onUpload = { classType, section, subject, title, file ->
                viewModel.upload(classType, section, subject, title, file, context.contentResolver) { error ->
                    if (error == null) {
                        showUpload = false
                        showMessage("Assignment uploaded successfully")
                    } else {
                        showMessage(error)
                    }
                }
            }
        )
    }
}

@Composable
private fun UploadAssignmentDialog(
    isUploading: Boolean,
    onDismiss: () -> Unit,
    onUpload: (classType: String, section: String, subject: String, title: String, file: Uri) -> Unit
) {
    var classType by rememberSaveable { mutableStateOf<String?>(null) }
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    var subject by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var file by rememberSaveable { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val fileName = remember(file) { file?.let { context.contentResolver.displayName(it) } }
    // Same file types the backend accepts (validate_file: pdf / image).
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) file = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload Assignment") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Assignment title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("application/pdf", "image/png", "image/jpeg")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(fileName ?: "Choose file (PDF / JPG / PNG)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isUploading,
                onClick = {
                    val cls = classType
                    val sec = section
                    val picked = file
                    // Same check as upload-assignment.html.
                    if (cls == null || sec == null || subject.isBlank() || title.isBlank() || picked == null) {
                        error = "All fields and file are required"
                    } else {
                        error = null
                        onUpload(cls, sec, subject.trim(), title.trim(), picked)
                    }
                }
            ) { Text(if (isUploading) "Uploading…" else "Upload") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUploading) { Text("Cancel") }
        }
    )
}

private fun ContentResolver.displayName(uri: Uri): String? =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

// The backend keeps the extension of the file name, so make sure it has one that matches the type.
private fun ContentResolver.readUploadPart(uri: Uri): UploadPart {
    val mime = getType(uri) ?: "application/octet-stream"
    val extension = when (mime) {
        "application/pdf" -> ".pdf"
        "image/png" -> ".png"
        "image/jpeg" -> ".jpg"
        else -> throw IllegalArgumentException("Only PDF, JPG and PNG files can be uploaded")
    }
    val name = displayName(uri)?.takeIf { n -> listOf(".pdf", ".png", ".jpg", ".jpeg").any { n.lowercase().endsWith(it) } }
        ?: "assignment$extension"
    val bytes = openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("Could not read the file")
    return UploadPart(name = "file", fileName = name, mimeType = mime, bytes = bytes)
}
