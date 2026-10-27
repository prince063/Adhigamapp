package com.example.adhigam.data.repository

import com.example.adhigam.data.model.Quiz
import com.example.adhigam.data.model.QuizAttemptResult
import com.example.adhigam.data.model.QuizDraft
import com.example.adhigam.data.model.QuizOption
import com.example.adhigam.data.model.QuizQuestion
import com.example.adhigam.data.model.QuizQuestionDraft
import com.example.adhigam.data.model.QuizResult
import com.example.adhigam.data.remote.ApiClient
import com.example.adhigam.data.remote.dateTime
import com.example.adhigam.data.remote.int
import com.example.adhigam.data.remote.str
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

// Online quizzes: attempting them (quiz-attempt.html, Student) and managing them (quiz-manage.html, staff).
interface QuizRepository {
    suspend fun getQuizzes(classType: String? = null, section: String? = null): List<Quiz>
    suspend fun getQuestions(quizId: Int): List<QuizQuestion>
    suspend fun submit(quizId: Int, answers: Map<Int, String>): QuizResult

    suspend fun saveQuiz(quiz: QuizDraft): Int
    suspend fun deleteQuiz(quizId: Int)
    suspend fun saveQuestion(quizId: Int, question: QuizQuestionDraft)
    suspend fun deleteQuestion(questionId: Int)
    suspend fun getResults(quizId: Int): List<QuizAttemptResult>
}

class ApiQuizRepository(private val api: ApiClient) : QuizRepository {

    // GET /academics/quizzes — the student page sends no filter, quiz-manage.html filters by class / section.
    override suspend fun getQuizzes(classType: String?, section: String?): List<Quiz> {
        val query = listOfNotNull(
            classType?.let { "class_type=${it.encoded()}" },
            section?.let { "section=${it.encoded()}" }
        ).joinToString("&")
        val rows = api.get(if (query.isEmpty()) "academics/quizzes" else "academics/quizzes?$query") as? JSONArray ?: JSONArray()
        return rows.objects().mapNotNull { row ->
            Quiz(
                id = row.int("ID", "id") ?: return@mapNotNull null,
                title = row.str("TITLE", "title").orEmpty(),
                classType = row.str("CLASS", "class_type").orEmpty(),
                section = row.str("SECTION", "section").orEmpty(),
                subject = row.str("SUBJECT", "subject").orEmpty(),
                durationMins = row.int("DURATION_MINS", "duration_mins")?.takeIf { it > 0 } ?: 30,
                questionCount = row.int("question_count", "QUESTION_COUNT") ?: 0,
                isActive = row.int("IS_ACTIVE", "is_active") == 1
            )
        }
    }

    // GET /academics/quizzes/{id}/questions
    override suspend fun getQuestions(quizId: Int): List<QuizQuestion> {
        val rows = api.get("academics/quizzes/$quizId/questions") as? JSONArray ?: JSONArray()
        return rows.objects().mapNotNull { row ->
            QuizQuestion(
                id = row.int("ID", "id") ?: return@mapNotNull null,
                text = row.str("QUESTION", "question").orEmpty(),
                options = listOf("A", "B", "C", "D").mapNotNull { key ->
                    row.str("OPTION_$key", "option_${key.lowercase()}")?.let { QuizOption(key, it) }
                },
                marks = row.int("MARKS", "marks") ?: 1,
                correctOption = row.str("CORRECT_OPTION", "correct_option")
            )
        }
    }

    // POST /academics/quizzes/submit {quiz_id, answers: {question_id: "A".."D"}}; unanswered questions are left out.
    override suspend fun submit(quizId: Int, answers: Map<Int, String>): QuizResult {
        val body = JSONObject()
            .put("quiz_id", quizId)
            .put("answers", JSONObject().apply { answers.forEach { (id, option) -> put(id.toString(), option) } })
        val json = api.post("academics/quizzes/submit", body) as? JSONObject
            ?: throw IllegalStateException("Quiz was not submitted")
        return QuizResult(
            score = json.int("score", "SCORE") ?: 0,
            maxScore = json.int("max_score", "MAX_SCORE") ?: 0
        )
    }

    // POST /academics/quizzes; answers {"id": <quiz id>}.
    override suspend fun saveQuiz(quiz: QuizDraft): Int {
        val body = JSONObject()
            .put("id", quiz.id)
            .put("title", quiz.title)
            .put("class_type", quiz.classType)
            .put("section", quiz.section)
            .put("subject", quiz.subject)
            .put("duration_mins", quiz.durationMins)
            .put("is_active", if (quiz.isActive) 1 else 0)
        val json = api.post("academics/quizzes", body) as? JSONObject
        return json?.int("id") ?: quiz.id
    }

    // DELETE /academics/quizzes/{id} (soft delete; its questions go with it).
    override suspend fun deleteQuiz(quizId: Int) {
        api.delete("academics/quizzes/$quizId")
    }

    // POST /academics/quizzes/questions
    override suspend fun saveQuestion(quizId: Int, question: QuizQuestionDraft) {
        val body = JSONObject()
            .put("id", question.id)
            .put("quiz_id", quizId)
            .put("question", question.text)
            .put("option_a", question.optionA)
            .put("option_b", question.optionB)
            .put("option_c", question.optionC)
            .put("option_d", question.optionD)
            .put("correct_option", question.correctOption)
            .put("marks", question.marks)
        api.post("academics/quizzes/questions", body)
    }

    // DELETE /academics/quizzes/questions/{id}
    override suspend fun deleteQuestion(questionId: Int) {
        api.delete("academics/quizzes/questions/$questionId")
    }

    // GET /academics/quizzes/{id}/results — one row per student (a re-attempt overwrites the earlier one).
    override suspend fun getResults(quizId: Int): List<QuizAttemptResult> {
        val rows = api.get("academics/quizzes/$quizId/results") as? JSONArray ?: JSONArray()
        return rows.objects().map { row ->
            QuizAttemptResult(
                studentName = row.str("student_name", "STUDENTNAME").orEmpty(),
                admissionNo = row.str("admission_no", "ADMISSIONNO").orEmpty(),
                score = row.int("SCORE", "score") ?: 0,
                maxScore = row.int("MAX_SCORE", "max_score") ?: 0,
                submittedAt = row.dateTime("SUBMITTED_AT", "submitted_at")
            )
        }
    }

    private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
}
