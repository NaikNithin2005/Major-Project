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

data class DashboardUiState(
    val smsScannedCount: Int = 0,
    val qrScannedCount: Int = 0,
    val threatCount: Int = 0,
    val securityScore: Int = 100,
    val threatHistory: List<ThreatRecord> = emptyList(),
    val recentAnalyses: List<SmsAnalysisResult> = emptyList(),
    val pendingSyncCount: Int = 0,
    val isLoading: Boolean = false
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
    private val submitFeedbackUseCase: com.example.android.domain.usecase.SubmitFeedbackUseCase? = null
) : ViewModel() {

    val authState: StateFlow<AuthState> = getAuthStateUseCase().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AuthState.Uninitialized
    )

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeData()
        observeSyncQueue()
    }

    private fun observeSyncQueue() {
        viewModelScope.launch {
            syncRepository?.observePendingCount()?.collect { count ->
                _uiState.value = _uiState.value.copy(pendingSyncCount = count)
            }
        }
    }

    fun triggerManualSync(context: android.content.Context) {
        viewModelScope.launch {
            try {
                // Ensure all existing threat records in local Room are enqueued if not already synced
                val currentThreats = threatHistoryRepository.getAllThreats().getOrNull() ?: emptyList()
                val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                for (threat in currentThreats) {
                    val normalizedCat = when {
                        threat.category.contains("HIGH", ignoreCase = true) || threat.category.contains("SMISHING", ignoreCase = true) || threat.category.contains("QUISHING", ignoreCase = true) -> "HIGH"
                        threat.category.contains("MEDIUM", ignoreCase = true) || threat.category.contains("SPAM", ignoreCase = true) -> "MEDIUM"
                        threat.category.contains("LOW", ignoreCase = true) -> "LOW"
                        else -> "SAFE"
                    }
                    val req = DetectionSyncRequest(
                        clientEventId = threat.id,
                        eventType = if (threat.source.contains("QR", ignoreCase = true)) "QR" else "SMS",
                        riskScore = threat.riskScore,
                        threatCategory = normalizedCat,
                        recommendedAction = threat.actionTaken.ifBlank { "WARN_USER" },
                        timestamp = isoDateFormat.format(Date(threat.timestamp)),
                        modelVersions = mapOf("model" to "on-device-engine-v1"),
                        modelResults = mapOf("score" to threat.riskScore),
                        ruleResults = emptyList(),
                        contextResults = mapOf("sender" to threat.sender),
                        reasons = if (threat.details.isNotBlank()) threat.details.split(";").map { it.trim() } else listOf("Detected threat pattern"),
                        urlMetadata = null,
                        qrMetadata = null
                    )
                    syncRepository?.enqueueDetection(req)
                }
                syncRepository?.processSyncQueue()
            } catch (ignored: Exception) {
            }
            SyncScheduler.scheduleImmediateSync(context)
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
                val smishingThreatsCount = threatsList.size
                val totalSmsCount = smsList.size
                val totalQrCount = qrList.size
                val calculatedScore = (100 - (smishingThreatsCount * 10)).coerceIn(10, 100)

                DashboardUiState(
                    smsScannedCount = totalSmsCount,
                    qrScannedCount = totalQrCount,
                    threatCount = smishingThreatsCount,
                    securityScore = calculatedScore,
                    threatHistory = threatsList,
                    recentAnalyses = smsList,
                    pendingSyncCount = _uiState.value.pendingSyncCount,
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

    fun submitFeedback(threatId: String, category: String, comment: String) {
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
}
