package com.example.adhigam.data

import android.content.Context
import com.example.adhigam.BuildConfig
import com.example.adhigam.data.remote.ApiClient
import com.example.adhigam.data.repository.AdminDashboardRepository
import com.example.adhigam.data.repository.ApiAdminDashboardRepository
import com.example.adhigam.data.repository.ApiAuthRepository
import com.example.adhigam.data.repository.ApiFeeCollectionRepository
import com.example.adhigam.data.repository.ApiLibraryRepository
import com.example.adhigam.data.repository.ApiNoticeRepository
import com.example.adhigam.data.repository.ApiLeaveRepository
import com.example.adhigam.data.repository.ApiQuizRepository
import com.example.adhigam.data.repository.ApiTimetableRepository
import com.example.adhigam.data.repository.ApiResultsLabRepository
import com.example.adhigam.data.repository.ApiStaffRepository
import com.example.adhigam.data.repository.ApiStudentFormRepository
import com.example.adhigam.data.repository.ApiStudentRepository
import com.example.adhigam.data.repository.ApiStudentListRepository
import com.example.adhigam.data.repository.ApiStudentMarksRepository
import com.example.adhigam.data.repository.ApiStudentRecordRepository
import com.example.adhigam.data.repository.ApiStudentProfileRepository
import com.example.adhigam.data.repository.AuthRepository
import com.example.adhigam.data.repository.FeeCollectionRepository
import com.example.adhigam.data.repository.LibraryRepository
import com.example.adhigam.data.repository.NoticeRepository
import com.example.adhigam.data.repository.LeaveRepository
import com.example.adhigam.data.repository.QuizRepository
import com.example.adhigam.data.repository.TimetableRepository
import com.example.adhigam.data.repository.ResultsLabRepository
import com.example.adhigam.data.repository.StaffRepository
import com.example.adhigam.data.repository.StudentFormRepository
import com.example.adhigam.data.repository.StudentListRepository
import com.example.adhigam.data.repository.StudentMarksRepository
import com.example.adhigam.data.repository.StudentProfileRepository
import com.example.adhigam.data.repository.StudentRecordRepository
import com.example.adhigam.data.repository.StudentRepository
import com.example.adhigam.data.session.SessionStore

// Simple manual dependency container. Every repository talks to the FastAPI backend.
object AppContainer {

    // Set by AdhigamApp at start-up. Null in Compose previews.
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val sessionStore: SessionStore? by lazy { appContext?.let(::SessionStore) }

    val apiClient: ApiClient by lazy {
        ApiClient(BuildConfig.API_BASE_URL, tokenProvider = { sessionStore?.token() })
    }

    val authRepository: AuthRepository by lazy {
        ApiAuthRepository(apiClient, checkNotNull(sessionStore) { "AppContainer.init() must run before login" })
    }
    val studentProfileRepository: StudentProfileRepository by lazy { ApiStudentProfileRepository(apiClient) }
    val noticeRepository: NoticeRepository by lazy { ApiNoticeRepository(apiClient) }
    val studentListRepository: StudentListRepository by lazy { ApiStudentListRepository(apiClient) }
    val studentRecordRepository: StudentRecordRepository by lazy { ApiStudentRecordRepository(apiClient) }
    val studentFormRepository: StudentFormRepository by lazy { ApiStudentFormRepository(apiClient) }
    val feeCollectionRepository: FeeCollectionRepository by lazy { ApiFeeCollectionRepository(apiClient) }
    val studentMarksRepository: StudentMarksRepository by lazy { ApiStudentMarksRepository(apiClient) }
    val adminDashboardRepository: AdminDashboardRepository by lazy {
        ApiAdminDashboardRepository(apiClient, noticeRepository, session = { authRepository.currentSession() })
    }
    val libraryRepository: LibraryRepository by lazy {
        ApiLibraryRepository(apiClient, session = { authRepository.currentSession() })
    }
    val studentRepository: StudentRepository by lazy {
        ApiStudentRepository(apiClient, noticeRepository, session = { authRepository.currentSession() })
    }
    val resultsLabRepository: ResultsLabRepository by lazy { ApiResultsLabRepository(apiClient) }
    val quizRepository: QuizRepository by lazy { ApiQuizRepository(apiClient) }
    val leaveRepository: LeaveRepository by lazy { ApiLeaveRepository(apiClient) }
    val timetableRepository: TimetableRepository by lazy { ApiTimetableRepository(apiClient) }
    val staffRepository: StaffRepository by lazy {
        ApiStaffRepository(apiClient, noticeRepository, session = { authRepository.currentSession() })
    }
}
