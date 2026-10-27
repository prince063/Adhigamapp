package com.example.adhigam.ui.quiz

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adhigam.data.model.Quiz
import com.example.adhigam.data.model.QuizQuestion
import com.example.adhigam.data.model.QuizResult
import com.example.adhigam.data.repository.QuizRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// A quiz being attempted. answers: question id -> "A".."D".
data class QuizAttempt(
    val quiz: Quiz,
    val questions: List<QuizQuestion>,
    val answers: Map<Int, String> = emptyMap(),
    val secondsLeft: Int,
    val isSubmitting: Boolean = false,
    val error: String? = null
) {
    val unanswered: Int get() = questions.count { it.id !in answers }
}

data class QuizUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val quizzes: List<Quiz>? = null,
    val error: String? = null,
    val startingQuizId: Int? = null,
    val attempt: QuizAttempt? = null,
    val result: Pair<Quiz, QuizResult>? = null
)

// quiz-attempt.html: list -> attempt with a countdown (auto-submits at 0) -> score.
class QuizViewModel(private val repository: QuizRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        load(refresh = false)
    }

    fun refresh() = load(refresh = true)

    // onMessage: shown when the quiz cannot be started (no questions / network error).
    fun startQuiz(quiz: Quiz, onMessage: (String) -> Unit) {
        if (_uiState.value.startingQuizId != null) return
        _uiState.update { it.copy(startingQuizId = quiz.id) }
        viewModelScope.launch {
            try {
                val questions = repository.getQuestions(quiz.id)
                if (questions.isEmpty()) {
                    _uiState.update { it.copy(startingQuizId = null) }
                    onMessage("This quiz has no questions yet")
                    return@launch
                }
                val seconds = quiz.durationMins * 60
                _uiState.update {
                    it.copy(startingQuizId = null, attempt = QuizAttempt(quiz, questions, secondsLeft = seconds))
                }
                startTimer(seconds)
            } catch (e: Exception) {
                _uiState.update { it.copy(startingQuizId = null) }
                onMessage(e.message ?: "Could not load the quiz")
            }
        }
    }

    fun pickAnswer(questionId: Int, option: String) = updateAttempt { attempt ->
        if (attempt.isSubmitting) attempt else attempt.copy(answers = attempt.answers + (questionId to option))
    }

    fun submit() {
        val attempt = _uiState.value.attempt ?: return
        if (attempt.isSubmitting) return
        updateAttempt { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                val result = repository.submit(attempt.quiz.id, attempt.answers)
                timerJob?.cancel()
                _uiState.update { it.copy(attempt = null, result = attempt.quiz to result) }
            } catch (e: Exception) {
                // Stay on the questions so the student can try again (also after the time is up).
                updateAttempt { it.copy(isSubmitting = false, error = e.message ?: "Submission failed") }
            }
        }
    }

    // Leaving an attempt without submitting discards the answers (nothing is saved on the server).
    fun abandonAttempt() {
        timerJob?.cancel()
        _uiState.update { it.copy(attempt = null) }
    }

    fun backToList() {
        _uiState.update { it.copy(result = null) }
        refresh()
    }

    // Counts down against a fixed deadline so a slow tick never stretches the time.
    private fun startTimer(seconds: Int) {
        timerJob?.cancel()
        val deadline = SystemClock.elapsedRealtime() + seconds * 1000L
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val left = ((deadline - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)
                updateAttempt { it.copy(secondsLeft = left) }
                if (left == 0) {
                    submit()
                    break
                }
            }
        }
    }

    private fun updateAttempt(change: (QuizAttempt) -> QuizAttempt) {
        _uiState.update { state -> state.attempt?.let { state.copy(attempt = change(it)) } ?: state }
    }

    private fun load(refresh: Boolean) {
        _uiState.update { it.copy(isLoading = !refresh && it.quizzes == null, isRefreshing = refresh, error = null) }
        viewModelScope.launch {
            try {
                val quizzes = repository.getQuizzes()
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, quizzes = quizzes) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "Something went wrong")
                }
            }
        }
    }
}
