package com.example.android.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.domain.model.AuthState
import com.example.android.domain.model.SmsAnalysisResult
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.repository.SmsAnalysisRepository
import com.example.android.domain.repository.ThreatHistoryRepository
import com.example.android.domain.usecase.AddThreatRecordUseCase
import com.example.android.domain.usecase.DeleteThreatRecordUseCase
import com.example.android.domain.usecase.GetAuthStateUseCase
import com.example.android.domain.usecase.GetThreatHistoryUseCase
import com.example.android.domain.usecase.LogoutUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import com.example.android.domain.repository.SyncRepository
import com.example.android.sync.SyncScheduler
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.android.data.remote.dto.DetectionSyncRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DashboardUiState(
    val smsScannedCount: Int = 0,
    val smsSafeCount: Int = 0,
    val smsAttentionCount: Int = 0,
    val smsDangerousCount: Int = 0,
    val qrScannedCount: Int = 0,
    val qrSafeCount: Int = 0,
    val qrAttentionCount: Int = 0,
    val qrDangerousCount: Int = 0,
    val threatCount: Int = 0,
    val threatHistory: List<ThreatRecord> = emptyList(),
    val recentMessages: List<ThreatRecord> = emptyList(),
    val recentQrScans: List<ThreatRecord> = emptyList(),
    val safetyAlerts: List<ThreatRecord> = emptyList(),
    val latestActivity: List<ThreatRecord> = emptyList(),
    val recentAnalyses: List<SmsAnalysisResult> = emptyList(),
    val pendingSyncCount: Int = 0,
    val isSyncing: Boolean = false,
    val isLoading: Boolean = false,
    val lastSyncTimestamp: Long? = null
)

class DashboardViewModel(
    getAuthStateUseCase: GetAuthStateUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val threatHistoryRepository: ThreatHistoryRepository,
    private val smsAnalysisRepository: SmsAnalysisRepository,
    private val qrAnalysisRepository: com.example.android.domain.repository.QrAnalysisRepository? = null,
    private val addThreatRecordUseCase: AddThreatRecordUseCase? = null,
    private val deleteThreatRecordUseCase: DeleteThreatRecordUseCase? = null,
    private val syncRepository: SyncRepository? = null,
    private val submitFeedbackUseCase: com.example.android.domain.usecase.SubmitFeedbackUseCase? = null,
    private val trustedSenderRepository: com.example.android.domain.repository.TrustedSenderRepository? = null,
    private val feedbackRepository: com.example.android.domain.repository.FeedbackRepository? = null,
    private val settingsRepository: com.example.android.domain.repository.SettingsRepository? = null
) : ViewModel() {

    val authState: StateFlow<AuthState> = getAuthStateUseCase().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AuthState.Uninitialized
    )

    val systemSettings: StateFlow<com.example.android.domain.model.SystemSettings> =
        settingsRepository?.observeSettings()?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.example.android.domain.model.SystemSettings()
        ) ?: MutableStateFlow(com.example.android.domain.model.SystemSettings())

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    val trustedSenders: StateFlow<List<com.example.android.domain.model.TrustedSender>> =
        trustedSenderRepository?.observeTrustedSenders()?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        ) ?: MutableStateFlow(emptyList())

    val feedbackHistory: StateFlow<List<com.example.android.domain.model.UserFeedback>> =
        feedbackRepository?.observeAllFeedback()?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        ) ?: MutableStateFlow(emptyList())

    init {
        observeData()
        observeSyncQueue()
        viewModelScope.launch {
            authState.collect { state ->
                if (state is AuthState.Authenticated && !state.user.isGuest) {
                    restoreCloudHistory()
                }
            }
        }
    }

    private fun observeSyncQueue() {
        viewModelScope.launch {
            syncRepository?.observePendingCount()?.collect { count ->
                val state = authState.value
                val isGuest = (state as? AuthState.Authenticated)?.user?.isGuest == true || state is AuthState.Unauthenticated
                _uiState.value = _uiState.value.copy(pendingSyncCount = if (isGuest) 0 else count)
            }
        }
    }

    fun triggerManualSync(context: android.content.Context) {
        val state = authState.value
        val isGuest = (state as? AuthState.Authenticated)?.user?.isGuest == true || state is AuthState.Unauthenticated
        if (isGuest) {
            android.widget.Toast.makeText(context, "Sign in to synchronize telemetry to Cloud. On-device protection is active.", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        val currentUserId = (state as? AuthState.Authenticated)?.user?.uid ?: return

        if (_uiState.value.isSyncing) return
        _uiState.value = _uiState.value.copy(isSyncing = true)

        viewModelScope.launch {
            try {
                // 1. Instant direct batch sync of all local Room records to Cloud Firestore
                val batchResult = syncRepository?.syncAllLocalThreatsToFirestore(currentUserId)

                // 2. Process and drain any pending queue items
                val summary = syncRepository?.processSyncQueue()
                SyncScheduler.scheduleImmediateSync(context)

                withContext(Dispatchers.Main) {
                    val count = batchResult?.getOrNull() ?: summary?.syncedCount ?: 0
                    val msg = if (count > 0) {
                        "Cloud Sync: $count item(s) synchronized"
                    } else {
                        "Cloud Sync: All records up-to-date"
                    }
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Cloud Sync queued locally", android.widget.Toast.LENGTH_SHORT).show()
                }
            } finally {
                _uiState.value = _uiState.value.copy(isSyncing = false)
            }
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            val qrFlow = qrAnalysisRepository?.observeAllAnalyses() ?: kotlinx.coroutines.flow.flowOf(emptyList())

            combine(
                smsAnalysisRepository.observeAllAnalyses(),
                qrFlow,
                threatHistoryRepository.observeAllThreats()
            ) { smsList, qrList, threatsList ->
                val threatIds = threatsList.map { it.id }.toSet()

                // Map any safe/benign SMS analyses into ThreatRecord format for complete history visibility
                val safeSmsRecords = smsList
                    .filter { it.id !in threatIds }
                    .map { sms ->
                        ThreatRecord(
                            id = sms.id,
                            timestamp = sms.timestamp,
                            source = "SMS",
                            sender = sms.sender,
                            riskScore = sms.riskScore,
                            category = sms.assessment?.category?.name ?: if (sms.isSmishing) "HIGH" else "SAFE",
                            actionTaken = if (sms.isSmishing) "BLOCKED" else "PROCEED_SAFELY",
                            details = sms.assessment?.reasons?.joinToString("; ") ?: "Incoming SMS verified safe",
                            rawContent = sms.rawBody,
                            extractedUrl = null,
                            payloadType = "SMS",
                            severity = sms.assessment?.category?.name ?: if (sms.isSmishing) "HIGH" else "SAFE",
                            modelVersion = "tinybert-smishing-v2",
                            isQuarantined = false,
                            reviewStatus = "NEW",
                            contentRetentionMode = "LOCAL_SAVED"
                        )
                    }

                // Map any safe QR analyses into ThreatRecord format for complete history visibility
                val safeQrRecords = qrList
                    .filter { it.id !in threatIds }
                    .map { qr ->
                        ThreatRecord(
                            id = qr.id,
                            timestamp = qr.timestamp,
                            source = "QR Code (${qr.source})",
                            sender = if (qr.extractedUrl.isNotBlank()) qr.domain else "QR Payload",
                            riskScore = qr.riskScore,
                            category = qr.assessment?.category?.name ?: if (qr.isQuishing) "HIGH" else "SAFE",
                            actionTaken = if (qr.isQuishing) "BLOCKED" else "PROCEED_SAFELY",
                            details = qr.assessment?.reasons?.joinToString("; ") ?: "QR code verified safe",
                            rawContent = qr.rawContent,
                            extractedUrl = qr.extractedUrl,
                            payloadType = "QR",
                            severity = qr.assessment?.category?.name ?: if (qr.isQuishing) "HIGH" else "SAFE",
                            modelVersion = "xgboost-url-v2",
                            isQuarantined = false,
                            reviewStatus = "NEW",
                            contentRetentionMode = "LOCAL_SAVED"
                        )
                    }

                val allHistory = (threatsList + safeSmsRecords + safeQrRecords)
                    .distinctBy { it.id }
                    .sortedByDescending { it.timestamp }

                val smsItems = allHistory.filter { it.source.contains("SMS", ignoreCase = true) }
                val qrItems = allHistory.filter { it.source.contains("QR", ignoreCase = true) }

                val isDangerousItem: (ThreatRecord) -> Boolean = { threat ->
                    threat.riskScore >= 70 ||
                    threat.category.contains("HIGH", ignoreCase = true) ||
                    threat.category.contains("MALICIOUS", ignoreCase = true) ||
                    threat.category.contains("SMISHING", ignoreCase = true) ||
                    threat.category.contains("QUISHING", ignoreCase = true)
                }

                val isAttentionItem: (ThreatRecord) -> Boolean = { threat ->
                    !isDangerousItem(threat) && (
                        threat.riskScore >= 40 ||
                        threat.category.contains("MEDIUM", ignoreCase = true) ||
                        threat.category.contains("SUSPICIOUS", ignoreCase = true) ||
                        threat.category.contains("SPAM", ignoreCase = true)
                    )
                }

                val isSafeItem: (ThreatRecord) -> Boolean = { threat ->
                    !isDangerousItem(threat) && !isAttentionItem(threat)
                }

                val totalSms = smsItems.size
                val safeSms = smsItems.count { isSafeItem(it) }
                val attentionSms = smsItems.count { isAttentionItem(it) }
                val dangerousSms = smsItems.count { isDangerousItem(it) }

                val totalQr = qrItems.size
                val safeQr = qrItems.count { isSafeItem(it) }
                val attentionQr = qrItems.count { isAttentionItem(it) }
                val dangerousQr = qrItems.count { isDangerousItem(it) }

                val safetyAlerts = allHistory.filter { 
                    (isDangerousItem(it) || isAttentionItem(it)) && 
                    it.actionTaken != "RESOLVED" && 
                    it.actionTaken != "FALSE_POSITIVE" 
                }.take(10)

                val recentMessages = smsItems.take(10)
                val recentQrScans = qrItems.take(10)
                val latestActivity = allHistory.take(15)

                DashboardUiState(
                    smsScannedCount = totalSms,
                    smsSafeCount = safeSms,
                    smsAttentionCount = attentionSms,
                    smsDangerousCount = dangerousSms,
                    qrScannedCount = totalQr,
                    qrSafeCount = safeQr,
                    qrAttentionCount = attentionQr,
                    qrDangerousCount = dangerousQr,
                    threatCount = dangerousSms + dangerousQr + attentionSms + attentionQr,
                    threatHistory = allHistory,
                    recentMessages = recentMessages,
                    recentQrScans = recentQrScans,
                    safetyAlerts = safetyAlerts,
                    latestActivity = latestActivity,
                    recentAnalyses = smsList,
                    pendingSyncCount = _uiState.value.pendingSyncCount,
                    isSyncing = _uiState.value.isSyncing,
                    isLoading = false
                )
            }.catch { e ->
                _uiState.value = _uiState.value.copy(isLoading = false)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun deleteThreat(id: String) {
        viewModelScope.launch {
            threatHistoryRepository.deleteThreat(id)
        }
    }

    fun updateThreatAction(id: String, action: String) {
        viewModelScope.launch {
            val existing = threatHistoryRepository.getThreatById(id).getOrNull()
            if (existing != null) {
                val updated = existing.copy(actionTaken = action)
                threatHistoryRepository.updateThreat(updated)
            }
        }
    }

    fun toggleQuarantine(id: String, isQuarantined: Boolean) {
        viewModelScope.launch {
            threatHistoryRepository.updateQuarantineStatus(id, isQuarantined)
        }
    }

    fun updateReviewStatus(id: String, reviewStatus: String) {
        viewModelScope.launch {
            threatHistoryRepository.updateReviewStatus(id, reviewStatus)
        }
    }

    fun saveThreat(threat: ThreatRecord) {
        viewModelScope.launch {
            threatHistoryRepository.addThreat(threat)
        }
    }

    fun clearAllThreats() {
        viewModelScope.launch {
            threatHistoryRepository.clearAllThreats()
        }
    }

    fun toggleTrustSender(sender: String, trust: Boolean) {
        viewModelScope.launch {
            if (trust) {
                trustedSenderRepository?.addTrustedSender(sender)
            } else {
                trustedSenderRepository?.removeTrustedSender(sender)
            }
        }
    }

    fun deleteFeedback(id: String) {
        viewModelScope.launch {
            feedbackRepository?.deleteFeedback(id)
        }
    }

    fun undoFeedbackForThreat(threatId: String) {
        viewModelScope.launch {
            feedbackRepository?.deleteFeedbackForThreat(threatId)
            updateThreatAction(threatId, "WARN_USER")
        }
    }

    fun submitFeedback(
        threatId: String,
        category: String,
        comment: String,
        sender: String = "",
        originalPrediction: String = "",
        correctedClassification: String = ""
    ) {
        viewModelScope.launch {
            submitFeedbackUseCase?.invoke(
                com.example.android.domain.model.UserFeedback(
                    id = java.util.UUID.randomUUID().toString(),
                    threatId = threatId,
                    timestamp = System.currentTimeMillis(),
                    userCategory = category,
                    comment = comment,
                    isSubmitted = true,
                    sender = sender,
                    originalPrediction = originalPrediction,
                    correctedClassification = correctedClassification
                )
            )
            if (correctedClassification == "SAFE" || category.contains("Safe", ignoreCase = true) || category.contains("Legitimate", ignoreCase = true) || category.contains("Trusted", ignoreCase = true)) {
                updateThreatAction(threatId, "MARK_SAFE")
                if (sender.isNotBlank() && category.contains("Trusted", ignoreCase = true)) {
                    toggleTrustSender(sender, true)
                }
            } else if (correctedClassification == "DANGEROUS" || category.contains("Scam", ignoreCase = true) || category.contains("Dangerous", ignoreCase = true)) {
                updateThreatAction(threatId, "CONFIRMED_THREAT")
            }
        }
    }

    private fun legacySubmitFeedback(threatId: String, category: String, comment: String) {
        viewModelScope.launch {
            submitFeedbackUseCase?.invoke(
                com.example.android.domain.model.UserFeedback(
                    id = java.util.UUID.randomUUID().toString(),
                    threatId = threatId,
                    timestamp = System.currentTimeMillis(),
                    userCategory = category,
                    comment = comment,
                    isSubmitted = true
                )
            )
            if (category == "FALSE_POSITIVE") {
                updateThreatAction(threatId, "FALSE_POSITIVE")
            } else if (category == "CORRECT_THREAT") {
                updateThreatAction(threatId, "CONFIRMED_THREAT")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
        }
    }

    fun toggleSmsProtection(enabled: Boolean) {
        viewModelScope.launch { settingsRepository?.setSmsProtection(enabled) }
    }

    fun toggleQrProtection(enabled: Boolean) {
        viewModelScope.launch { settingsRepository?.setQrScanner(enabled) }
    }

    fun toggleUrlAnalysis(enabled: Boolean) {
        viewModelScope.launch { settingsRepository?.setUrlAnalysis(enabled) }
    }

    fun toggleHaptics(context: android.content.Context, enabled: Boolean) {
        viewModelScope.launch {
            com.example.android.util.HapticFeedbackHelper.setHapticsEnabled(context, enabled)
            settingsRepository?.setHaptics(enabled)
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsRepository?.setNotificationsEnabled(enabled) }
    }

    fun toggleNotifyHighSeverity(enabled: Boolean) {
        viewModelScope.launch { settingsRepository?.setNotifyHighSeverity(enabled) }
    }

    fun toggleNotifyMediumSeverity(enabled: Boolean) {
        viewModelScope.launch { settingsRepository?.setNotifyMediumSeverity(enabled) }
    }

    fun toggleNotifyDailyTips(enabled: Boolean) {
        viewModelScope.launch { settingsRepository?.setNotifyDailyTips(enabled) }
    }

    fun toggleShowNotificationPreviews(enabled: Boolean) {
        viewModelScope.launch { settingsRepository?.setShowNotificationPreviews(enabled) }
    }

    fun toggleAiModelEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository?.setAiModelEnabled(enabled) }
    }

    fun toggleOptInAiLearning(enabled: Boolean) {
        viewModelScope.launch { settingsRepository?.setOptInAiLearning(enabled) }
    }

    fun toggleCloudSync(enabled: Boolean) {
        viewModelScope.launch { settingsRepository?.setCloudSyncEnabled(enabled) }
    }

    fun toggleBiometric(enabled: Boolean) {
        viewModelScope.launch { settingsRepository?.setBiometricLock(enabled) }
    }

    fun toggleDarkTheme(enabled: Boolean) {
        viewModelScope.launch { settingsRepository?.setDarkTheme(enabled) }
    }

    fun toggleThreatSharing(enabled: Boolean) {
        viewModelScope.launch { settingsRepository?.setThreatSharingEnabled(enabled) }
    }

    fun restoreCloudHistory(context: android.content.Context? = null) {
        val state = authState.value
        val user = (state as? AuthState.Authenticated)?.user
        if (user == null || user.isGuest) return

        viewModelScope.launch {
            try {
                val res = syncRepository?.restoreUserCloudHistory(user.uid)
                settingsRepository?.syncSettingsWithCloud(user.uid)
                val count = res?.getOrNull() ?: 0
                if (context != null) {
                    if (count > 0) {
                        android.widget.Toast.makeText(context, "Restored $count threat records from cloud", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Cloud history is already up to date", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (context != null) {
                    android.widget.Toast.makeText(context, "Cloud sync check: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

}
