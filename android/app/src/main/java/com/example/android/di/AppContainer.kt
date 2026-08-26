package com.example.android.di

import android.content.Context
import com.example.android.data.local.database.AegisDatabase
import com.example.android.data.local.datastore.PreferencesDataStore
import com.example.android.data.repository.*
import com.example.android.data.source.FirebaseAuthDataSource
import com.example.android.data.source.FirestoreDataSource
import com.example.android.domain.repository.*
import com.example.android.domain.usecase.*
import com.example.android.security.SecureStorageManager

class AppContainer(private val context: Context) {

    // Database & Storage
    val database: AegisDatabase by lazy {
        AegisDatabase.getInstance(context)
    }

    val preferencesDataStore: PreferencesDataStore by lazy {
        PreferencesDataStore(context)
    }

    val secureStorageManager: SecureStorageManager by lazy {
        SecureStorageManager(context)
    }

    // Remote Data Sources
    val firebaseAuthDataSource: FirebaseAuthDataSource by lazy {
        FirebaseAuthDataSource()
    }

    val firestoreDataSource: FirestoreDataSource by lazy {
        FirestoreDataSource()
    }

    // Repositories
    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(firebaseAuthDataSource, firestoreDataSource)
    }

    val threatHistoryRepository: ThreatHistoryRepository by lazy {
        ThreatHistoryRepositoryImpl(database.threatHistoryDao())
    }

    val smsAnalysisRepository: SmsAnalysisRepository by lazy {
        SmsAnalysisRepositoryImpl(database.smsAnalysisDao())
    }

    val qrAnalysisRepository: QrAnalysisRepository by lazy {
        QrAnalysisRepositoryImpl(database.qrAnalysisDao())
    }

    val feedbackRepository: FeedbackRepository by lazy {
        FeedbackRepositoryImpl(database.feedbackDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(preferencesDataStore)
    }

    val modelRepository: ModelRepository by lazy {
        ModelRepositoryImpl(database.modelVersionDao())
    }

    // Auth Use Cases
    val loginUseCase: LoginUseCase by lazy {
        LoginUseCase(authRepository)
    }

    val registerUseCase: RegisterUseCase by lazy {
        RegisterUseCase(authRepository)
    }

    val logoutUseCase: LogoutUseCase by lazy {
        LogoutUseCase(authRepository)
    }

    val getAuthStateUseCase: GetAuthStateUseCase by lazy {
        GetAuthStateUseCase(authRepository)
    }

    val guestLoginUseCase: GuestLoginUseCase by lazy {
        GuestLoginUseCase(authRepository)
    }

    // Phase 2 Use Cases
    val getThreatHistoryUseCase: GetThreatHistoryUseCase by lazy {
        GetThreatHistoryUseCase(threatHistoryRepository)
    }

    val addThreatRecordUseCase: AddThreatRecordUseCase by lazy {
        AddThreatRecordUseCase(threatHistoryRepository)
    }

    val deleteThreatRecordUseCase: DeleteThreatRecordUseCase by lazy {
        DeleteThreatRecordUseCase(threatHistoryRepository)
    }

    val getSettingsUseCase: GetSettingsUseCase by lazy {
        GetSettingsUseCase(settingsRepository)
    }

    val updateSettingUseCase: UpdateSettingUseCase by lazy {
        UpdateSettingUseCase(settingsRepository)
    }

    val submitFeedbackUseCase: SubmitFeedbackUseCase by lazy {
        SubmitFeedbackUseCase(feedbackRepository)
    }
}
