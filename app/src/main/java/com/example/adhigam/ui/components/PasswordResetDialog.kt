package com.example.adhigam.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.UserRole
import com.example.adhigam.data.repository.AuthRepository
import kotlinx.coroutines.launch

// "Change password" in Profile: the same OTP flow for the logged-in account (the backend has no
// change-password endpoint that takes the old password).
@Composable
fun ChangePasswordDialog(onDismiss: () -> Unit, onDone: (message: String) -> Unit) {
    val authRepository = AppContainer.authRepository
    val session = remember { authRepository.currentSession() }
    PasswordResetDialog(
        title = "Change Password",
        initialMobile = session?.mobile.orEmpty(),
        mobileEditable = session?.mobile.isNullOrBlank(),
        studentAccount = session?.role == UserRole.Student || session?.role == UserRole.Parent,
        authRepository = authRepository,
        onDismiss = onDismiss,
        onDone = onDone
    )
}

// forgot-password.html as a dialog: step 1 sends an OTP to the account's e-mail, step 2 sets the new password.
// Used for "Forgot password?" on the login screen (mobile typed in) and "Change password" in Profile
// (mobile of the logged-in account, not editable).
@Composable
fun PasswordResetDialog(
    title: String,
    initialMobile: String,
    mobileEditable: Boolean,
    studentAccount: Boolean,
    authRepository: AuthRepository,
    onDismiss: () -> Unit,
    onDone: (message: String) -> Unit
) {
    var mobile by rememberSaveable { mutableStateOf(initialMobile) }
    var otpSent by rememberSaveable { mutableStateOf(false) }
    var otp by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun run(block: suspend () -> Unit) {
        if (isLoading) return
        error = null
        isLoading = true
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                error = e.message ?: "Request failed"
            } finally {
                isLoading = false
            }
        }
    }

    fun sendOtp() {
        if (mobile.isBlank()) {
            error = "Enter your mobile number"
            return
        }
        run {
            authRepository.sendPasswordOtp(mobile.trim(), studentAccount)
            otpSent = true
        }
    }

    fun reset() {
        val code = otp.trim().toIntOrNull()
        when {
            code == null || password.isEmpty() -> error = "Fill all fields"
            password != confirm -> error = "Passwords do not match"
            else -> run {
                authRepository.resetPassword(mobile.trim(), code, password, studentAccount)
                onDone("Password reset successfully!")
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (otpSent) "Enter the OTP sent to the e-mail registered with this account (valid for 10 minutes)."
                    else "We'll send an OTP to the e-mail registered with this mobile number.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it.filter(Char::isDigit) },
                    label = { Text("Mobile No") },
                    enabled = mobileEditable && !otpSent,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                if (otpSent) {
                    OutlinedTextField(
                        value = otp,
                        onValueChange = { otp = it.filter(Char::isDigit).take(6) },
                        label = { Text("OTP") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("New password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text("Confirm password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (otpSent) reset() else sendOtp() }, enabled = !isLoading) {
                Text(
                    when {
                        isLoading -> "Please wait…"
                        otpSent -> "Reset password"
                        else -> "Send OTP"
                    }
                )
            }
        },
        dismissButton = {
            if (otpSent) {
                TextButton(onClick = ::sendOtp, enabled = !isLoading) { Text("Resend OTP") }
            } else {
                TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancel") }
            }
        }
    )
}
