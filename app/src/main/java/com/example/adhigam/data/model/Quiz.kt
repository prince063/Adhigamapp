package com.example.adhigam.data.model

import java.time.LocalDateTime

// Row of GET /academics/quizzes (GET_QUIZZES: QUIZ.* plus question_count). Students only get active quizzes,
// staff get drafts too.
data class Quiz(
    val id: Int,                // ID
    val title: String,          // TITLE
    val classType: String,      // CLASS
    val section: String,        // SECTION
    val subject: String,        // SUBJECT
    val durationMins: Int,      // DURATION_MINS (the web portal falls back to 30)
    val questionCount: Int,     // question_count
    val isActive: Boolean = true // IS_ACTIVE: 0 = draft, hidden from students
)

// Body of POST /academics/quizzes (MANAGE_QUIZ); id 0 creates a quiz, otherwise it is updated.
data class QuizDraft(
    val id: Int = 0,
    val title: String,
    val classType: String,
    val section: String,
    val subject: String,
    val durationMins: Int,
    val isActive: Boolean
)

// Row of GET /academics/quizzes/{id}/questions (GET_QUIZ_QUESTIONS). For students CORRECT_OPTION comes back
// null; only the staff quiz screens show it.
data class QuizQuestion(
    val id: Int,                        // ID
    val text: String,                   // QUESTION
    val options: List<QuizOption>,      // OPTION_A..OPTION_D, blank ones left out
    val marks: Int,                     // MARKS
    val correctOption: String? = null   // CORRECT_OPTION ("A".."D"), staff only
)

// Body of POST /academics/quizzes/questions (MANAGE_QUIZ_QUESTION); id 0 adds a question.
data class QuizQuestionDraft(
    val id: Int = 0,
    val text: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctOption: String,
    val marks: Int
)

// Row of GET /academics/quizzes/{id}/results (QUIZ_ATTEMPTS joined with STUDENTS), best score first.
data class QuizAttemptResult(
    val studentName: String,            // student_name
    val admissionNo: String,            // admission_no
    val score: Int,                     // SCORE
    val maxScore: Int,                  // MAX_SCORE
    val submittedAt: LocalDateTime?     // SUBMITTED_AT
) {
    val result: QuizResult get() = QuizResult(score, maxScore)
}

data class QuizOption(val key: String, val text: String)  // key: "A".."D"

// Answer of POST /academics/quizzes/submit (SUBMIT_QUIZ_ATTEMPT).
data class QuizResult(val score: Int, val maxScore: Int) {
    val percent: Int get() = if (maxScore > 0) Math.round(score * 100f / maxScore) else 0
    val passed: Boolean get() = percent >= 33  // same pass mark as quiz-attempt.html
}
