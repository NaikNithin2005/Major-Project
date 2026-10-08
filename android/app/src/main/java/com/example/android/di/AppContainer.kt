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

    // Phase 8 Remote Backend API Service
    val backendApiService: com.example.android.data.remote.api.BackendApiService by lazy {
        com.example.android.data.remote.client.ApiClient.createBackendService(authRepository)
    }

    // Phase 8 Sync Repository
    val syncRepository: com.example.android.domain.repository.SyncRepository by lazy {
        com.example.android.data.repository.SyncRepositoryImpl(
            syncQueueDao = database.syncQueueDao(),
            backendApiService = backendApiService,
            authRepository = authRepository
        )
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

    // Phase 6 Risk Engine & Explainable AI Components
    val ruleEngine: com.example.android.domain.rules.RuleEngine by lazy {
        com.example.android.domain.rules.RuleEngine()
    }

    val contextCorrelationEngine: com.example.android.domain.context.ContextCorrelationEngine by lazy {
        com.example.android.domain.context.ContextCorrelationEngine()
    }

    val explainableAiEngine: com.example.android.domain.xai.ExplainableAiEngine by lazy {
        com.example.android.domain.xai.ExplainableAiEngine()
    }

    val riskEngine: com.example.android.domain.riskengine.RiskEngine by lazy {
        com.example.android.domain.riskengine.RiskEngine(
            ruleEngine = ruleEngine,
            contextEngine = contextCorrelationEngine,
            xaiEngine = explainableAiEngine
        )
    }

    // Phase 3 SMS Pipeline Components
    val smsParser: com.example.android.data.parser.SmsParser by lazy {
        com.example.android.data.parser.SmsParser()
    }

    val smsPreprocessor: com.example.android.data.preprocessor.SmsPreprocessor by lazy {
        com.example.android.data.preprocessor.SmsPreprocessor()
    }

    val smishingClassifier: com.example.android.domain.classifier.SmishingClassifier by lazy {
        com.example.android.data.classifier.OnnxTinyBertSmishingClassifier(context)
    }

    val processIncomingSmsUseCase: ProcessIncomingSmsUseCase by lazy {
        ProcessIncomingSmsUseCase(
            smsParser = smsParser,
            smsPreprocessor = smsPreprocessor,
            classifier = smishingClassifier,
            smsAnalysisRepository = smsAnalysisRepository,
            threatHistoryRepository = threatHistoryRepository,
            riskEngine = riskEngine,
            syncRepository = syncRepository,
            context = context
        )
    }

    val checkSmsPermissionUseCase: CheckSmsPermissionUseCase by lazy {
        CheckSmsPermissionUseCase(context)
    }

    // Phase 4 & 5 QR Scanner & XGBoost URL Pipeline Components
    val urlClassifier: com.example.android.domain.classifier.UrlClassifier by lazy {
        com.example.android.data.classifier.OnnxUrlClassifier(context)
    }

    val processQrCodeUseCase: ProcessQrCodeUseCase by lazy {
        ProcessQrCodeUseCase(
            urlClassifier = urlClassifier,
            qrAnalysisRepository = qrAnalysisRepository,
            threatHistoryRepository = threatHistoryRepository,
            riskEngine = riskEngine,
            syncRepository = syncRepository,
            context = context
        )
    }

    val checkCameraPermissionUseCase: CheckCameraPermissionUseCase by lazy {
        CheckCameraPermissionUseCase(context)
    }
}
