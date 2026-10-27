package com.example.adhigam.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

// An error response from the backend. message is FastAPI's "detail", ready to show to the user.
class ApiException(val httpCode: Int, message: String) : Exception(message)

// Minimal JSON-over-HTTP client for the FastAPI backend, built on HttpURLConnection so no extra
// library is needed. tokenProvider supplies the JWT for the Authorization header.
class ApiClient(
    private val baseUrl: String,
    private val tokenProvider: () -> String? = { null }
) {

    // body is a JSONObject or a JSONArray (e.g. /exam/marks/batch takes a list).
    suspend fun post(path: String, body: Any): Any? = request("POST", path, body)

    suspend fun get(path: String): Any? = request("GET", path, null)

    suspend fun delete(path: String): Any? = request("DELETE", path, null)

    // multipart/form-data POST, like the web's FormData uploads (e.g. /student/upload-assignment).
    suspend fun postMultipart(path: String, fields: Map<String, String>, file: UploadPart): Any? =
        request("POST", path, MultipartBody(fields, file))

    // Absolute URL of a file the backend serves from /static (FILEURL columns hold "/static/…" or a path under it).
    fun fileUrl(path: String): String {
        val clean = path.trim().trimStart('/')
        val relative = if (clean.startsWith("static/")) clean else "static/$clean"
        return baseUrl.trimEnd('/') + "/" + relative
    }

    private suspend fun request(method: String, path: String, body: Any?): Any? = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl.trimEnd('/') + "/" + path.trimStart('/')).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            tokenProvider()?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body is MultipartBody) {
                val boundary = "----AdhigamBoundary" + System.currentTimeMillis()
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                connection.outputStream.use { body.writeTo(it, boundary) }
            } else if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = text.takeIf { it.isNotBlank() }?.let { runCatching { JSONTokener(it).nextValue() }.getOrNull() }

            if (code !in 200..299) throw ApiException(code, errorMessage(code, json))
            json
        } catch (e: SocketTimeoutException) {
            throw IOException("The school server took too long to respond. Please try again.", e)
        } catch (e: IOException) {
            // Connection refused, no network, unknown host … — say it plainly instead of the raw socket error.
            throw IOException("Could not reach the school server. Check your internet connection.", e)
        } finally {
            connection.disconnect()
        }
    }

    // FastAPI sends {"detail": "..."} for HTTPException and {"detail": [{"msg": ...}, ...]} for validation errors.
    private fun errorMessage(code: Int, json: Any?): String {
        val detail = (json as? JSONObject)?.opt("detail")
        return when {
            detail is String && detail.isNotBlank() -> detail
            detail is JSONArray && detail.length() > 0 ->
                (0 until detail.length()).mapNotNull { (detail.opt(it) as? JSONObject)?.optString("msg") }
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                    .ifBlank { "Invalid request" }
            code >= 500 -> "Server error. Please try again later."
            else -> "Request failed ($code)"
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
    }
}

// The file part of a multipart upload; name is the form field (FastAPI's UploadFile parameter).
class UploadPart(val name: String, val fileName: String, val mimeType: String, val bytes: ByteArray)

private class MultipartBody(val fields: Map<String, String>, val file: UploadPart) {
    fun writeTo(out: OutputStream, boundary: String) {
        fun line(text: String = "") = out.write("$text\r\n".toByteArray(Charsets.UTF_8))
        fields.forEach { (name, value) ->
            line("--$boundary")
            line("Content-Disposition: form-data; name=\"$name\"")
            line()
            line(value)
        }
        line("--$boundary")
        line("Content-Disposition: form-data; name=\"${file.name}\"; filename=\"${file.fileName.replace("\"", "")}\"")
        line("Content-Type: ${file.mimeType}")
        line()
        out.write(file.bytes)
        line()
        line("--$boundary--")
    }
}
