package com.example.adhigam.data.repository

import com.example.adhigam.data.model.Notice
import com.example.adhigam.data.remote.ApiClient
import com.example.adhigam.data.remote.int
import com.example.adhigam.data.remote.str
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

// The notice board shared by every role: GET /notice, POST /notice, DELETE /notice/{id}.
interface NoticeRepository {
    // Notices for the logged-in user, newest first. The backend picks them from the token's role:
    // "All" notices plus the ones targeted at that role.
    suspend fun getNotices(): List<Notice>
    suspend fun postNotice(title: String, body: String, targetRole: String)
    suspend fun deleteNotice(id: Int)
}

// Audience values of NOTICE_BOARD.TARGET_ROLE, as offered by the web form.
val NoticeAudiences = listOf("All", "Student", "Teacher", "Parent")

class ApiNoticeRepository(private val api: ApiClient) : NoticeRepository {

    override suspend fun getNotices(): List<Notice> {
        val rows = api.get("notice") as? JSONArray ?: return emptyList()
        return (0 until rows.length())
            .mapNotNull { rows.optJSONObject(it)?.toNotice() }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun postNotice(title: String, body: String, targetRole: String) {
        api.post("notice", JSONObject().put("title", title).put("body", body).put("target_role", targetRole))
    }

    override suspend fun deleteNotice(id: Int) {
        api.delete("notice/$id")
    }

    // GET_NOTICES columns: id, title, body, target_role, created_at, created_by.
    private fun JSONObject.toNotice(): Notice? {
        val title = str("title", "TITLE") ?: return null
        return Notice(
            title = title,
            body = str("body", "BODY").orEmpty(),
            targetRole = str("target_role", "TARGET_ROLE") ?: "All",
            createdAt = str("created_at", "CREATED_AT")
                ?.let { runCatching { LocalDateTime.parse(it.replace(' ', 'T').take(19)) }.getOrNull() }
                ?: LocalDateTime.now(),
            id = int("id", "ID") ?: 0,
            createdBy = str("created_by", "CREATED_BY")
        )
    }
}
