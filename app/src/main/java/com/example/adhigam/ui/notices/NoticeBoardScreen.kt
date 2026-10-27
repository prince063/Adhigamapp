package com.example.adhigam.ui.notices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.Notice
import com.example.adhigam.data.model.UserRole
import com.example.adhigam.data.repository.NoticeAudiences
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ErrorState
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.StatusPill
import com.example.adhigam.ui.components.noticeRoleColor
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.util.formatDateTime
import java.time.Duration
import java.time.LocalDateTime

private fun audienceLabel(audience: String): String = when (audience) {
    "All" -> "All (Everyone)"
    "Student" -> "Students only"
    "Teacher" -> "Teachers only"
    "Parent" -> "Parents only"
    else -> audience
}

@Composable
fun NoticeBoardScreen(
    role: UserRole,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoticeBoardViewModel = viewModel { NoticeBoardViewModel(AppContainer.noticeRepository, role) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        state.message?.let {
            showMessage(it)
            viewModel.messageShown()
        }
    }

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.error != null && state.notices.isEmpty() ->
            ErrorState(state.error!!, onRetry = viewModel::refresh, modifier = modifier)
        else -> NoticeBoardContent(state, viewModel, modifier)
    }

    state.confirmDelete?.let { notice ->
        // Same confirmation as the web page.
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete this notice?") },
            text = { Text("\"${notice.title}\" will be removed for everyone.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text("Delete", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoticeBoardContent(state: NoticeBoardUiState, viewModel: NoticeBoardViewModel, modifier: Modifier = Modifier) {
    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.canManage) {
                item { PostNoticeCard(state, viewModel) }
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search notices") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }
            // Staff see every audience, so they can filter by it.
            if (state.canManage) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = state.audienceFilter == null, onClick = { viewModel.setAudienceFilter(null) }, label = { Text("All notices") }) }
                        items(NoticeAudiences) { audience ->
                            FilterChip(
                                selected = state.audienceFilter == audience,
                                onClick = { viewModel.setAudienceFilter(audience) },
                                label = { Text(if (audience == "All") "Everyone" else "${audience}s") }
                            )
                        }
                    }
                }
            }
            if (state.visibleNotices.isEmpty()) {
                item { ListCard { EmptyText(if (state.notices.isEmpty()) "No notices at this time" else "No notice matches your search") } }
            }
            items(state.visibleNotices, key = { it.id }) { notice ->
                NoticeCard(
                    notice = notice,
                    canDelete = state.canManage,
                    isDeleting = state.deletingId == notice.id,
                    onDelete = { viewModel.askDelete(notice) }
                )
            }
        }
    }
}

@Composable
private fun PostNoticeCard(state: NoticeBoardUiState, viewModel: NoticeBoardViewModel) {
    val form = state.form
    ListCard {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = viewModel::toggleForm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Post New Notice", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(
                    if (state.formOpen) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (state.formOpen) "Hide form" else "Show form"
                )
            }
            AnimatedVisibility(visible = state.formOpen) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = form.title,
                        onValueChange = { v -> viewModel.updateForm { it.copy(title = v) } },
                        label = { Text("Title *") },
                        placeholder = { Text("Notice headline") },
                        isError = form.showErrors && form.title.isBlank(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = form.body,
                        onValueChange = { v -> viewModel.updateForm { it.copy(body = v) } },
                        label = { Text("Message *") },
                        placeholder = { Text("Notice details…") },
                        isError = form.showErrors && form.body.isBlank(),
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Audience", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(NoticeAudiences) { audience ->
                            FilterChip(
                                selected = form.audience == audience,
                                onClick = { viewModel.updateForm { it.copy(audience = audience) } },
                                label = { Text(audienceLabel(audience)) }
                            )
                        }
                    }
                    Button(
                        onClick = viewModel::post,
                        enabled = !form.isPosting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (form.isPosting) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Posting…")
                        } else {
                            Text("Post Notice", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(notice: Notice, canDelete: Boolean, isDeleting: Boolean, onDelete: () -> Unit) {
    var expanded by rememberSaveable(notice.id) { mutableStateOf(false) }
    val color = noticeRoleColor(notice.targetRole)
    val isNew = Duration.between(notice.createdAt, LocalDateTime.now()).toHours() < 48
    val isLong = notice.body.length > 140

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Campaign, contentDescription = null, tint = color)
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .animateContentSize()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        notice.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isNew) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill("New", MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    notice.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded || !isLong) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (isLong) {
                    Text(
                        if (expanded) "Show less" else "Read more",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { expanded = !expanded }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatDateTime(notice.createdAt) + (notice.createdBy?.let { " • $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    StatusPill(notice.targetRole, color)
                }
            }
            if (canDelete) {
                if (isDeleting) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete notice", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
