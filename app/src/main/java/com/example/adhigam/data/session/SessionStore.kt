package com.example.adhigam.data.session

import android.content.Context
import android.util.Base64
import com.example.adhigam.data.model.UserRole
import org.json.JSONObject

// The logged-in user, from the /auth/*-login response and its JWT.
data class Session(
    val token: String,
    val role: UserRole,          // app role (Superadmin / Admin / Principal → Staff)
    val backendRole: String,     // role as the backend named it
    val name: String,
    val userId: Int,
    val mobile: String,
    val expiresAtEpochSeconds: Long
) {
    val isExpired: Boolean get() = System.currentTimeMillis() / 1000 >= expiresAtEpochSeconds
}

// Keeps the session across app restarts, like the web portal keeps its token in localStorage.
class SessionStore(context: Context) {

    private val prefs = context.getSharedPreferences("adhigam_session", Context.MODE_PRIVATE)

    fun save(session: Session) {
        prefs.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_ROLE, session.role.name)
            .putString(KEY_BACKEND_ROLE, session.backendRole)
            .putString(KEY_NAME, session.name)
            .putInt(KEY_USER_ID, session.userId)
            .putString(KEY_MOBILE, session.mobile)
            .putLong(KEY_EXPIRES, session.expiresAtEpochSeconds)
            .apply()
    }

    // The saved session, or null when there is none or it has expired.
    fun load(): Session? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val role = prefs.getString(KEY_ROLE, null)?.let { runCatching { UserRole.valueOf(it) }.getOrNull() } ?: return null
        val session = Session(
            token = token,
            role = role,
            backendRole = prefs.getString(KEY_BACKEND_ROLE, null).orEmpty(),
            name = prefs.getString(KEY_NAME, null).orEmpty(),
            userId = prefs.getInt(KEY_USER_ID, 0),
            mobile = prefs.getString(KEY_MOBILE, null).orEmpty(),
            expiresAtEpochSeconds = prefs.getLong(KEY_EXPIRES, 0)
        )
        if (session.isExpired) {
            clear()
            return null
        }
        return session
    }

    fun token(): String? = load()?.token

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_ROLE = "role"
        const val KEY_BACKEND_ROLE = "backend_role"
        const val KEY_NAME = "name"
        const val KEY_USER_ID = "user_id"
        const val KEY_MOBILE = "mobile"
        const val KEY_EXPIRES = "expires_at"
    }
}

// Reads the payload of a JWT (no signature check — the server verifies the token on every request).
fun decodeJwtPayload(token: String): JSONObject? = runCatching {
    val payload = token.split(".")[1]
    JSONObject(String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8))
}.getOrNull()
