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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.FeeSummary
import com.example.adhigam.data.model.InstallmentStatus
import com.example.adhigam.data.model.StaffStudentDetail
import com.example.adhigam.data.model.StudentStatus
import com.example.adhigam.data.model.classDisplayName
import com.example.adhigam.ui.components.DashboardHeaderDark
import com.example.adhigam.ui.components.EmptyText
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.StatusPill
import com.example.adhigam.ui.profile.InfoSection
import com.example.adhigam.ui.theme.Amber600
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.Indigo700
import com.example.adhigam.ui.theme.SuccessGreen
import com.example.adhigam.ui.util.formatFullDate
import com.example.adhigam.ui.util.formatRupees

// Mirrors student-view.html: header with Edit / Fees, General, Family, School, Bank and Medical
// information, and the student's outstanding fees.
@Composable
fun StudentDetailScreen(
    encryptedId: String,
    onOpenFees: (String) -> Unit,
    onEdit: (String) -> Unit,
    onOpenMarks: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudentDetailViewModel = viewModel(key = "student-$encryptedId") {
        StudentDetailViewModel(AppContainer.studentRecordRepository, encryptedId)
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Pick up changes saved on the Edit screen when coming back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reloadIfLoaded() }
    LoadStateContent(state, onRetry = viewModel::refresh, modifier = modifier) { data ->
        StudentDetailContent(
            detail = data.detail,
            fees = data.fees,
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            onOpenFees = { onOpenFees(encryptedId) },
            onEdit = { onEdit(encryptedId) },
            onMarks = { onOpenMarks(encryptedId) },
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentDetailContent(
    detail: StaffStudentDetail,
    fees: FeeSummary,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenFees: () -> Unit,
    onEdit: () -> Unit,
    onMarks: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = detail.profile
    // Same rows and order as the web page; empty values are hidden.
    val general = listOf(
        "Mobile" to p.mobile,
        "Email" to p.email,
        "DOB" to p.dob?.let(::formatFullDate),
        "Gender" to p.gender,
        "Religion" to p.religion,
        "Community" to p.community,
        "Caste" to detail.caste,
        "Nationality" to p.nationality,
        "Mother Tongue" to p.motherTongue,
        "Blood Group" to p.bloodGroup,
        "Enrollment No" to p.enrollmentNo,
        "Apaar ID" to detail.aaparId,
        "Address" to p.address,
        "District" to p.district,
        "State" to p.state
    )
    val family = listOf(
        "Father Name" to p.fatherName,
        "Father Occupation" to p.fatherOccupation,
        "Mother Name" to p.motherName,
        "Mother Occupation" to p.motherOccupation,
        "Emergency Mobile 1" to p.emergencyMobile1,
        "Emergency Mobile 2" to p.emergencyMobile2,
        "Is Sibling" to if (detail.isSibling) "Yes" else "No"
    )
    val school = listOf(
        "Class" to classDisplayName(p.classType),
        "Section" to p.section,
        "Fee Group" to p.feeGroup,
        "Admission Year" to p.admissionYear,
        "Previous School" to p.previousSchool,
        "Previous Class" to detail.previousClass,
        "Previous Max Marks" to detail.previousMaxMarks,
        "Previous Obtain Marks" to detail.previousObtainMarks,
        "Special Category" to detail.specialCategory,
        "Status" to detail.status.name
    )
    val medical = listOf(
        "Medical Info" to p.medicalInformation,
        "Allergies" to p.allergies,
        "Medical Report" to detail.medicalReportUrl?.let { "Uploaded" }
    )

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { DetailHeader(detail, onEdit = onEdit, onFees = onOpenFees, onMarks = onMarks) }
            item { OutstandingFeesCard(fees, onViewAll = onOpenFees) }
            item { InfoSection("General Information", Icons.Outlined.Info, general) }
            item { InfoSection("Family Information", Icons.Outlined.FamilyRestroom, family) }
            item { InfoSection("School Information", Icons.Outlined.School, school) }
            item { BankSection(detail) }
            item { InfoSection("Medical Information", Icons.Outlined.MedicalServices, medical) }
        }
    }
}

@Composable
private fun statusColorOf(status: StudentStatus): Color = when (status) {
    StudentStatus.Approved -> SuccessGreen
    StudentStatus.Pending -> Amber600
    StudentStatus.Rejected -> ErrorRed
}

@Composable
private fun DetailHeader(detail: StaffStudentDetail, onEdit: () -> Unit, onFees: () -> Unit, onMarks: () -> Unit) {
    val p = detail.profile
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(DashboardHeaderDark, Indigo700)))
            .padding(20.dp)
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
                    initialsOf(p.name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Indigo700
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                p.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            // Solid badge so the status reads on the dark header.
            Text(
                detail.status.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(statusColorOf(detail.status))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text("Admission No: ${p.admissionNo}", style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Text(p.classLabel, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.height(16.dp))
            // Three short buttons fit one row on small phones.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
                ) {
                    Text("Edit")
                }
                FilledTonalButton(
                    onClick = onFees,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Amber600, contentColor = Color.White)
                ) {
                    Text("Fees")
                }
                FilledTonalButton(
                    onClick = onMarks,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = SuccessGreen, contentColor = Color.White)
                ) {
                    Text("Marks")
                }
            }
        }
    }
}

// Unpaid months (first 8, like the web) with the overall balance.
@Composable
private fun OutstandingFeesCard(fees: FeeSummary, onViewAll: () -> Unit) {
    val pending = fees.pendingMonths.take(8)
    ListCard {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.CurrencyRupee,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Outstanding Fees", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (fees.balance > 0) "Balance ${formatRupees(fees.balance)} of ${formatRupees(fees.totalDue)}"
                    else "All fees paid",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (fees.balance > 0) ErrorRed else SuccessGreen
                )
            }
            TextButton(onClick = onViewAll) { Text("View All") }
        }
        if (pending.isEmpty()) {
            EmptyText("No outstanding fees")
        } else {
            Spacer(Modifier.height(4.dp))
            pending.forEachIndexed { index, month ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(month.month, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        formatRupees(month.balance),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ErrorRed
                    )
                    Spacer(Modifier.width(10.dp))
                    StatusPill(
                        if (month.status == InstallmentStatus.Partial) "Partial" else "Due",
                        if (month.status == InstallmentStatus.Partial) Amber600 else ErrorRed
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

private fun mask(value: String, visible: Int = 4): String {
    val digits = value.filter { !it.isWhitespace() }
    if (digits.length <= visible) return value
    return "•".repeat(digits.length - visible) + digits.takeLast(visible)
}

// Bank & identity numbers are hidden until the staff member taps the eye icon.
@Composable
private fun BankSection(detail: StaffStudentDetail) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    val rows = listOf(
        "Bank Name" to detail.bankName,
        "IFSC Code" to detail.ifscCode,
        "Account No" to detail.accountNumber?.let { if (revealed) it else mask(it) },
        "Aadhar No" to detail.aadharNumber?.let { if (revealed) it else mask(it) },
        "PAN Number" to detail.panNumber?.let { if (revealed) it else mask(it, visible = 3) }
    ).filterNot { it.second.isNullOrBlank() }
    if (rows.isEmpty()) return

    ListCard {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.AccountBalance,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Bank Information",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (revealed) "Hide numbers" else "Show numbers"
                )
            }
        }
        rows.forEachIndexed { index, (label, value) ->
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
        Spacer(Modifier.height(4.dp))
    }
}
