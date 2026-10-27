package com.example.adhigam.data.repository

import com.example.adhigam.data.model.UserRole
import com.example.adhigam.data.remote.ApiClient
import com.example.adhigam.data.session.Session
import com.example.adhigam.data.session.SessionStore
import com.example.adhigam.data.session.decodeJwtPayload
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

// The three login pages of the web portal.
enum class LoginPortal { Staff, Student, Parent }

interface AuthRepository {
    // Returns the role to open the app with, or throws with the message to show (like the web "detail").
    suspend fun login(portal: LoginPortal, mobile: String, password: String): UserRole
    // Saved, unexpired login, so the app can skip the login screen.
    fun currentSession(): Session?
    fun logout()
    // forgot-password.html: an OTP is e-mailed to the account's address, then it is exchanged for a new password.
    // Student and Parent logins use the student account, Staff the USERS account.
    suspend fun sendPasswordOtp(mobile: String, studentAccount: Boolean)
    suspend fun resetPassword(mobile: String, otp: Int, newPassword: String, studentAccount: Boolean)
}

// Real login against the FastAPI backend: POST /auth/login (staff), /auth/student-login, /auth/parent-login
// with {mobile_no, password}; the response is {access_token, token_type, role, name, user_id}.
class ApiAuthRepository(
    private val api: ApiClient,
    private val sessionStore: SessionStore
) : AuthRepository {

    override suspend fun login(portal: LoginPortal, mobile: String, password: String): UserRole {
        val path = when (portal) {
            LoginPortal.Staff -> "auth/login"
            LoginPortal.Student -> "auth/student-login"
            LoginPortal.Parent -> "auth/parent-login"
        }
        val body = JSONObject().put("mobile_no", mobile).put("password", password)
        val response = try {
            api.post(path, body) as? JSONObject ?: throw IOException("Empty response")
        } catch (e: IOException) {
            // Same wording as the web login pages.
            throw IOException("Login failed. Check your connection.", e)
        }

        val token = response.getString("access_token")
        val backendRole = response.optString("role")
        val role = appRoleFor(backendRole)
            ?: throw IllegalStateException("$backendRole accounts can't use the mobile app yet")
        val claims = decodeJwtPayload(token)
        sessionStore.save(
            Session(
                token = token,
                role = role,
                backendRole = backendRole,
                name = response.optString("name"),
                userId = response.optInt("user_id"),
                mobile = claims?.optString("mobile").takeUnless { it.isNullOrBlank() } ?: mobile,
                // Fall back to 12 hours (the shortest backend expiry) if the token has no exp claim.
                expiresAtEpochSeconds = claims?.optLong("exp")?.takeIf { it > 0 }
                    ?: (System.currentTimeMillis() / 1000 + 12 * 3600)
            )
        )
        return role
    }

    override fun currentSession(): Session? = sessionStore.load()

    override fun logout() = sessionStore.clear()

    // GET /auth/send-otp?mobile_no&clicked_from
    override suspend fun sendPasswordOtp(mobile: String, studentAccount: Boolean) {
        api.get("auth/send-otp?mobile_no=${URLEncoder.encode(mobile, "UTF-8")}&clicked_from=${clickedFrom(studentAccount)}")
    }

    // POST /auth/forgot-password {mobile_no, otp, password, clicked_from}. A wrong OTP still comes back as
    // 200 with {"message": "Invalid or expired OTP"} (FORGOT_PASSWORD proc), so the message is checked.
    override suspend fun resetPassword(mobile: String, otp: Int, newPassword: String, studentAccount: Boolean) {
        val body = JSONObject()
            .put("mobile_no", mobile)
            .put("otp", otp)
            .put("password", newPassword)
            .put("clicked_from", clickedFrom(studentAccount))
        val message = (api.post("auth/forgot-password", body) as? JSONObject)?.optString("message").orEmpty()
        if (!message.equals(PASSWORD_UPDATED, ignoreCase = true)) {
            throw IllegalStateException(message.ifBlank { "Could not reset the password" })
        }
    }

    private fun clickedFrom(studentAccount: Boolean) = if (studentAccount) "Student" else "Staff"

    private fun appRoleFor(backendRole: String): UserRole? = when (backendRole) {
        "Student" -> UserRole.Student
        "Parent" -> UserRole.Parent
        "Teacher" -> UserRole.Teacher
        "Superadmin", "Admin", "Principal" -> UserRole.Staff
        else -> null
    }

    private companion object {
        const val PASSWORD_UPDATED = "Password updated successfully"
    }
}
