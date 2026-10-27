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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.SchoolClasses
import com.example.adhigam.data.model.SchoolSections
import com.example.adhigam.data.model.StudentFormLists
import com.example.adhigam.data.model.StudentFormOptions
import com.example.adhigam.data.model.StudentStatus
import com.example.adhigam.data.model.classDisplayName
import com.example.adhigam.ui.components.ErrorState
import com.example.adhigam.ui.components.ListCard
import com.example.adhigam.ui.components.StatusPill
import com.example.adhigam.ui.theme.Amber600
import com.example.adhigam.ui.theme.ErrorRed
import com.example.adhigam.ui.theme.SuccessGreen
import com.example.adhigam.ui.util.formatFullDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Register a new student (encryptedId == null) or edit an existing one; same sections as the web form.
@Composable
fun StudentFormScreen(
    encryptedId: String?,
    onSaved: (String) -> Unit,
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudentFormViewModel = viewModel(key = "student-form-${encryptedId ?: "new"}") {
        StudentFormViewModel(AppContainer.studentFormRepository, encryptedId)
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        state.message?.let {
            showMessage(it)
            viewModel.messageShown()
        }
    }
    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let(onSaved)
    }

    val options = state.options
    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.loadError != null || options == null ->
            ErrorState(state.loadError ?: "Could not load form", onRetry = viewModel::load, modifier = modifier)
        else -> StudentFormContent(state, options, viewModel, modifier)
    }
}

@Composable
private fun StudentFormContent(
    state: StudentFormUiState,
    options: StudentFormOptions,
    viewModel: StudentFormViewModel,
    modifier: Modifier = Modifier
) {
    val f = state.form
    val update = viewModel::update

    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.isEdit) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Admission No: ${f.admissionNo}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        StatusPill(
                            state.status.name,
                            when (state.status) {
                                StudentStatus.Approved -> SuccessGreen
                                StudentStatus.Pending -> Amber600
                                StudentStatus.Rejected -> ErrorRed
                            }
                        )
                    }
                }
            } else {
                item {
                    Text(
                        "Admission number is assigned when the student is registered. New students start as Pending until approved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                FormSection("General Information", Icons.Outlined.Person) {
                    FormText("Student Name *", f.name, error = state.errorFor(FormField.Name), capitalize = true) { v -> update { it.copy(name = v) } }
                    FormText("Father's Name", f.fatherName, capitalize = true) { v -> update { it.copy(fatherName = v) } }
                    FormSelect("Father's Occupation", f.fatherOccupation, options.occupations) { v -> update { it.copy(fatherOccupation = v) } }
                    FormText("Mother's Name", f.motherName, capitalize = true) { v -> update { it.copy(motherName = v) } }
                    FormSelect("Mother's Occupation", f.motherOccupation, options.occupations) { v -> update { it.copy(motherOccupation = v) } }
                    FormText(
                        "Mobile *", f.mobile, keyboard = KeyboardType.Phone, error = state.errorFor(FormField.Mobile),
                        placeholder = "10-digit mobile number"
                    ) { v -> update { it.copy(mobile = v.filter(Char::isDigit).take(10)) } }
                    FormText("Email", f.email, keyboard = KeyboardType.Email, error = state.errorFor(FormField.Email)) { v -> update { it.copy(email = v.trim()) } }
                    TwoColumns(
                        { FormSelect("Gender", f.gender, StudentFormLists.genders) { v -> update { it.copy(gender = v) } } },
                        { FormSelect("Blood Group", f.bloodGroup, StudentFormLists.bloodGroups) { v -> update { it.copy(bloodGroup = v) } } }
                    )
                    FormDate("Date of Birth", f.dob) { d -> update { it.copy(dob = d) } }
                    TwoColumns(
                        { FormSelect("Religion", f.religion, StudentFormLists.religions) { v -> update { it.copy(religion = v) } } },
                        { FormSelect("Community", f.community, StudentFormLists.communities) { v -> update { it.copy(community = v) } } }
                    )
                    FormSelect("Caste", f.caste, options.castes) { v -> update { it.copy(caste = v) } }
                    TwoColumns(
                        { FormText("Nationality", f.nationality, capitalize = true) { v -> update { it.copy(nationality = v) } } },
                        { FormText("Mother Tongue", f.motherTongue, capitalize = true) { v -> update { it.copy(motherTongue = v) } } }
                    )
                    FormText("Apaar ID / PEN", f.aaparId) { v -> update { it.copy(aaparId = v) } }
                    FormSelect("Special Category", f.specialCategory, StudentFormLists.specialCategories) { v -> update { it.copy(specialCategory = v) } }
                }
            }

            item {
                FormSection("Address & Identity", Icons.Outlined.Home) {
                    FormText("Address", f.address, singleLine = false, capitalize = true) { v -> update { it.copy(address = v) } }
                    TwoColumns(
                        { FormText("District", f.district, capitalize = true) { v -> update { it.copy(district = v) } } },
                        { FormText("State", f.state, capitalize = true) { v -> update { it.copy(state = v) } } }
                    )
                    FormText("Identity Mark 1", f.identityOne) { v -> update { it.copy(identityOne = v) } }
                    FormText("Identity Mark 2", f.identityTwo) { v -> update { it.copy(identityTwo = v) } }
                }
            }

            item {
                FormSection("Emergency & Medical", Icons.Outlined.MedicalServices) {
                    TwoColumns(
                        {
                            FormText("Emergency Mobile 1", f.emergencyMobile1, keyboard = KeyboardType.Phone, error = state.errorFor(FormField.EmergencyMobile1)) { v ->
                                update { it.copy(emergencyMobile1 = v.filter(Char::isDigit).take(10)) }
                            }
                        },
                        {
                            FormText("Emergency Mobile 2", f.emergencyMobile2, keyboard = KeyboardType.Phone, error = state.errorFor(FormField.EmergencyMobile2)) { v ->
                                update { it.copy(emergencyMobile2 = v.filter(Char::isDigit).take(10)) }
                            }
                        }
                    )
                    FormText("Medical Information", f.medicalInformation, singleLine = false) { v -> update { it.copy(medicalInformation = v) } }
                    FormText("Allergies", f.allergies) { v -> update { it.copy(allergies = v) } }
                }
            }

            item {
                FormSection("Previous School", Icons.Outlined.School) {
                    FormText("Previous School Name", f.previousSchool, capitalize = true) { v -> update { it.copy(previousSchool = v) } }
                    FormSelect("Previous Class", f.previousClass, SchoolClasses, display = ::classDisplayName) { v -> update { it.copy(previousClass = v) } }
                    TwoColumns(
                        { FormNumber("Previous Max Marks", f.previousMaxMarks) { v -> update { it.copy(previousMaxMarks = v) } } },
                        { FormNumber("Previous Obtain Marks", f.previousObtainMarks) { v -> update { it.copy(previousObtainMarks = v) } } }
                    )
                    TwoColumns(
                        { FormNumber("Oral Max Marks", f.oralMaxMarks) { v -> update { it.copy(oralMaxMarks = v) } } },
                        { FormNumber("Oral Obtain Marks", f.oralObtainMarks) { v -> update { it.copy(oralObtainMarks = v) } } }
                    )
                    TwoColumns(
                        { FormNumber("Written Max Marks", f.writtenMaxMarks) { v -> update { it.copy(writtenMaxMarks = v) } } },
                        { FormNumber("Written Obtain Marks", f.writtenObtainMarks) { v -> update { it.copy(writtenObtainMarks = v) } } }
                    )
                }
            }

            item {
                FormSection("Bank Information", Icons.Outlined.AccountBalance) {
                    TwoColumns(
                        { FormText("PAN Number", f.panNumber, allCaps = true) { v -> update { it.copy(panNumber = v.uppercase().take(10)) } } },
                        { FormText("Aadhar No.", f.aadharNumber, keyboard = KeyboardType.Number) { v -> update { it.copy(aadharNumber = v.filter { c -> c.isDigit() || c == ' ' }.take(14)) } } }
                    )
                    FormText("Bank Name", f.bankName, capitalize = true) { v -> update { it.copy(bankName = v) } }
                    TwoColumns(
                        { FormText("IFSC Code", f.ifscCode, allCaps = true) { v -> update { it.copy(ifscCode = v.uppercase().take(11)) } } },
                        { FormText("Account No.", f.accountNumber, keyboard = KeyboardType.Number) { v -> update { it.copy(accountNumber = v.filter(Char::isDigit).take(18)) } } }
                    )
                }
            }

            item {
                FormSection("School / Admission", Icons.Outlined.School) {
                    // Selects show names; the form keeps the backend ids (fee_group_id, vehicle_id, pickup_point_id).
                    FormSelect(
                        "Fee Group",
                        options.feeGroups.firstOrNull { it.id == f.feeGroupId }?.name.orEmpty(),
                        options.feeGroups.map { it.name }
                    ) { name -> update { it.copy(feeGroupId = options.feeGroups.firstOrNull { g -> g.name == name }?.id) } }
                    if (options.feeGroups.isEmpty()) {
                        FormHint("No fee groups are set up yet — add them in the web portal (Master → Fee Group).")
                    }
                    TwoColumns(
                        {
                            FormSelect("Class *", f.classType, SchoolClasses, display = ::classDisplayName, error = state.errorFor(FormField.ClassType)) { v ->
                                update { it.copy(classType = v) }
                            }
                        },
                        {
                            FormSelect("Section *", f.section, SchoolSections, error = state.errorFor(FormField.Section)) { v ->
                                update { it.copy(section = v) }
                            }
                        }
                    )
                    FormText("Enrollment No.", f.enrollmentNo) { v -> update { it.copy(enrollmentNo = v) } }
                    val vehicleNames = listOf("None") + options.vehicles.map { it.name }
                    FormSelect(
                        "Route (Vehicle)",
                        options.vehicles.firstOrNull { it.id == f.vehicleId }?.name ?: "",
                        vehicleNames
                    ) { name -> viewModel.setVehicle(options.vehicles.firstOrNull { it.name == name }?.id) }
                    if (f.vehicleId != null) {
                        FormSelect(
                            if (state.isLoadingPickups) "Pickup Point (loading…)" else "Pickup Point",
                            state.pickups.firstOrNull { it.id == f.pickupPointId }?.name.orEmpty(),
                            state.pickups.map { it.name }
                        ) { name -> update { it.copy(pickupPointId = state.pickups.firstOrNull { p -> p.name == name }?.id) } }
                        TwoColumns(
                            {
                                FormSelect("Vehicle Start Month", f.vehicleStartMonth, StudentFormLists.sessionMonths, error = state.errorFor(FormField.VehicleMonths)) { v ->
                                    update { it.copy(vehicleStartMonth = v) }
                                }
                            },
                            {
                                FormSelect("Vehicle Stop Month", f.vehicleStopMonth, StudentFormLists.sessionMonths) { v ->
                                    update { it.copy(vehicleStopMonth = v) }
                                }
                            }
                        )
                    }
                }
            }

            item {
                FormSection("Siblings", Icons.Outlined.FamilyRestroom) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Is Sibling?", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = f.isSibling, onCheckedChange = viewModel::setSibling)
                    }
                    if (f.isSibling) {
                        f.siblings.forEachIndexed { index, sibling ->
                            SiblingRow(
                                index = index,
                                sibling = sibling,
                                canRemove = f.siblings.size > 1,
                                onChange = { viewModel.setSiblingAdmission(index, it) },
                                onFetch = { viewModel.fetchSibling(index) },
                                onRemove = { viewModel.removeSibling(index) }
                            )
                        }
                        TextButton(onClick = viewModel::addSibling) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add sibling")
                        }
                    }
                }
            }

            item {
                FormSection("Documents", Icons.Outlined.Description) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Profile image, medical report (PDF) and previous marksheet are uploaded from the web portal for now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        SaveFormBar(isEdit = state.isEdit, isSaving = state.isSaving, hasErrors = state.showErrors && state.errors.isNotEmpty(), onSave = viewModel::save)
    }
}

@Composable
private fun FormHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FormSection(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    ListCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun TwoColumns(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) { first() }
        Box(Modifier.weight(1f)) { second() }
    }
}

@Composable
private fun FormText(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    error: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    capitalize: Boolean = false,
    allCaps: Boolean = false,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = when {
                allCaps -> KeyboardCapitalization.Characters
                capitalize -> KeyboardCapitalization.Words
                else -> KeyboardCapitalization.None
            }
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FormNumber(label: String, value: String, onValueChange: (String) -> Unit) {
    FormText(label, value, keyboard = KeyboardType.Decimal) { v ->
        onValueChange(v.filter { it.isDigit() || it == '.' }.take(6))
    }
}

// Read-only text field that opens a dropdown, so selects look like the other fields.
@Composable
private fun FormSelect(
    label: String,
    value: String,
    options: List<String>,
    display: (String) -> String = { it },
    error: String? = null,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = if (value.isBlank()) "" else display(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Select") },
            trailingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null) },
            isError = error != null,
            supportingText = if (error != null) { { Text(error) } } else null,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        // Transparent layer on top: a read-only field still takes focus, so taps go here instead.
        Box(
            Modifier
                .matchParentSize()
                .clickable(enabled = options.isNotEmpty()) { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormDate(label: String, value: LocalDate?, onSelect: (LocalDate) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value?.let(::formatFullDate).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Select date") },
            trailingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable { open = true }
        )
    }
    if (open) {
        val todayMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = value?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun SiblingRow(
    index: Int,
    sibling: SiblingEntry,
    canRemove: Boolean,
    onChange: (String) -> Unit,
    onFetch: () -> Unit,
    onRemove: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = sibling.admissionNo,
                onValueChange = onChange,
                label = { Text("Sibling ${index + 1} Admission No.") },
                singleLine = true,
                isError = sibling.notFound,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            if (sibling.isLoading) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                OutlinedButton(onClick = onFetch, enabled = sibling.admissionNo.isNotBlank()) { Text("Fetch") }
            }
            if (canRemove) {
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, contentDescription = "Remove sibling") }
            }
        }
        when {
            sibling.name != null -> Text(sibling.name, style = MaterialTheme.typography.bodySmall, color = SuccessGreen, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            sibling.notFound -> Text("Student not found", style = MaterialTheme.typography.bodySmall, color = ErrorRed, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        }
    }
}

@Composable
private fun SaveFormBar(isEdit: Boolean, isSaving: Boolean, hasErrors: Boolean, onSave: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (hasErrors) "Fix the highlighted fields" else "* required fields",
                style = MaterialTheme.typography.bodySmall,
                color = if (hasErrors) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.height(48.dp)) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving…")
                } else {
                    Text(if (isEdit) "Update Student" else "Register Student", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
