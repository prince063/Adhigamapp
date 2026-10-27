package com.example.adhigam.ui.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.StaffProfile
import com.example.adhigam.ui.components.ChangePasswordDialog
import com.example.adhigam.ui.components.DashboardHeaderDark
import com.example.adhigam.ui.profile.AccountSection
import com.example.adhigam.ui.profile.InfoSection
import com.example.adhigam.ui.theme.AdhigamTheme
import com.example.adhigam.ui.theme.Indigo700
import com.example.adhigam.ui.util.formatFullDate

// Profile tab for Teacher and Staff: the USERS record (name, role, contact, address).
@Composable
fun StaffProfileScreen(
    showMessage: (String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StaffProfileViewModel = viewModel { StaffProfileViewModel(AppContainer.staffRepository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showChangePassword by rememberSaveable { mutableStateOf(false) }
    LoadStateContent(state, onRetry = viewModel::refresh, modifier = modifier) { profile ->
        StaffProfileContent(
            profile = profile,
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            onChangePassword = { showChangePassword = true },
            onLogout = onLogout,
            modifier = modifier
        )
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            onDismiss = { showChangePassword = false },
            onDone = { message ->
                showChangePassword = false
                showMessage(message)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StaffProfileContent(
    profile: StaffProfile,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onChangePassword: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val personal = listOf(
        "Mobile" to profile.mobile,
        "Email" to profile.email,
        "Date of birth" to profile.dob?.let(::formatFullDate),
        "Address" to profile.fullAddress
    )
    val work = listOf(
        "Role" to profile.roleType,
        "School" to "AAdhigam School"
    )

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { StaffProfileBanner(profile) }
            item { InfoSection("Personal information", Icons.Outlined.Info, personal) }
            item { InfoSection("Work", Icons.Outlined.Badge, work) }
            item { AccountSection(onChangePassword, onLogout) }
        }
    }
}

@Composable
private fun StaffProfileBanner(profile: StaffProfile) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(DashboardHeaderDark, Indigo700)))
            .padding(vertical = 24.dp, horizontal = 20.dp)
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initialsOf(profile.name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Indigo700
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                profile.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(profile.roleType, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun StaffProfilePreview() {
    // Preview-only sample; the app shows the logged-in user from the session.
    val profile = StaffProfile("Staff Name", "Teacher", "9000000000", null, null, null, null, null, null, null)
    AdhigamTheme {
        StaffProfileContent(profile, isRefreshing = false, onRefresh = {}, onChangePassword = {}, onLogout = {})
    }
}
