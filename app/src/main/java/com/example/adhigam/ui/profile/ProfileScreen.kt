package com.example.adhigam.ui.profile

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.StudentProfile
import com.example.adhigam.data.model.UserRole
import com.example.adhigam.ui.components.ChangePasswordDialog
import com.example.adhigam.ui.components.ErrorState
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.theme.AdhigamTheme
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.Indigo700
import com.example.adhigam.ui.util.formatFullDate

private val HeaderDark = Color(0xFF1E1B4B)

// Mirrors the web "My Profile" page: General, Family and School information, plus health and account actions.
@Composable
fun ProfileScreen(
    role: UserRole,
    showMessage: (String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel { ProfileViewModel(AppContainer.studentProfileRepository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showChangePassword by rememberSaveable { mutableStateOf(false) }

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.profile == null -> ErrorState(
            message = state.error ?: "Could not load profile",
            onRetry = viewModel::refresh,
            modifier = modifier
        )
        else -> ProfileContent(
            profile = state.profile!!,
            role = role,
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
private fun ProfileContent(
    profile: StudentProfile,
    role: UserRole,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onChangePassword: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val general = listOf(
        "Date of birth" to profile.dob?.let(::formatFullDate),
        "Gender" to profile.gender,
        "Blood group" to profile.bloodGroup,
        "Religion" to profile.religion,
        "Community" to profile.community,
        "Nationality" to profile.nationality,
        "Mother tongue" to profile.motherTongue,
        "Mobile" to profile.mobile,
        "Email" to profile.email,
        "Address" to profile.fullAddress
    )
    val family = listOf(
        "Father's name" to profile.fatherName,
        "Father's occupation" to profile.fatherOccupation,
        "Mother's name" to profile.motherName,
        "Mother's occupation" to profile.motherOccupation,
        "Parent mobile" to profile.mobile,
        "Emergency contact" to profile.emergencyMobile1,
        "Emergency contact 2" to profile.emergencyMobile2
    )
    val school = listOf(
        "Class" to profile.classType,
        "Section" to profile.section.takeIf { it != "N" },
        "Admission no" to profile.admissionNo,
        "Enrollment no" to profile.enrollmentNo,
        "Admission year" to profile.admissionYear,
        "Fee group" to profile.feeGroup,
        "Previous school" to profile.previousSchool
    )
    val health = listOf(
        "Medical information" to profile.medicalInformation,
        "Allergies" to profile.allergies
    )

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { ProfileBanner(profile, role) }
            item { InfoSection("General information", Icons.Outlined.Info, general) }
            item { InfoSection("Family information", Icons.Outlined.FamilyRestroom, family) }
            item { InfoSection("School information", Icons.Outlined.School, school) }
            item { InfoSection("Health", Icons.Outlined.MedicalServices, health) }
            item { AccountSection(onChangePassword, onLogout) }
        }
    }
}

@Composable
private fun ProfileBanner(profile: StudentProfile, role: UserRole) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(HeaderDark, Indigo700)))
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
                    text = profile.name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString(""),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Indigo700
                )
            }
            Spacer(Modifier.height(12.dp))
            if (role == UserRole.Parent) {
                Text("Your child", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
            }
            Text(
                profile.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(profile.classLabel, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Text(
                "Admission No: ${profile.admissionNo}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

// Rows with no value are hidden; a section with nothing to show is skipped entirely.
@Composable
fun InfoSection(title: String, icon: ImageVector, rows: List<Pair<String, String?>>) {
    val filled = rows.filterNot { it.second.isNullOrBlank() }
    if (filled.isEmpty()) return
    ListCard {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        filled.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.45f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    value!!,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.55f)
                )
            }
        }
    }
}

@Composable
fun AccountSection(onChangePassword: () -> Unit, onLogout: () -> Unit) {
    ListCard {
        AccountRow("Change password", Icons.Outlined.Lock, MaterialTheme.colorScheme.onSurface, onChangePassword)
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        AccountRow("Logout", Icons.AutoMirrored.Outlined.Logout, ErrorRed, onLogout)
    }
}

@Composable
private fun AccountRow(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

@Preview(showBackground = true, heightDp = 1700)
@Composable
private fun ProfileContentPreview() {
    // Preview-only sample; the app loads the real profile from /student/profile.
    val profile = StudentProfile(
        name = "Student Name", classType = "VIII", section = "A", admissionNo = "1001",
        gender = "Male", dob = null, bloodGroup = "B+", mobile = "9000000000", email = null,
        fatherName = "Father Name", motherName = "Mother Name", address = "Address line",
        photoPath = null, feeGroup = null
    )
    AdhigamTheme {
        ProfileContent(
            profile = profile,
            role = UserRole.Student,
            isRefreshing = false,
            onRefresh = {},
            onChangePassword = {},
            onLogout = {}
        )
    }
}
