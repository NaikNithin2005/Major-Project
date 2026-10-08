package com.example.android.presentation.sms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.domain.model.ProcessedSms
import com.example.android.domain.model.RawSms
import com.example.android.domain.model.SmsAnalysisResult
import com.example.android.domain.model.SmsFeatureEvidence
import com.example.android.domain.model.SmsProcessResult
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.repository.SmsAnalysisRepository
import com.example.android.domain.repository.ThreatHistoryRepository
import com.example.android.domain.riskengine.RiskAssessment
import com.example.android.domain.riskengine.ThreatCategory
import com.example.android.domain.usecase.CheckSmsPermissionUseCase
import com.example.android.domain.usecase.GetSettingsUseCase
import com.example.android.domain.usecase.ProcessIncomingSmsUseCase
import com.example.android.domain.usecase.UpdateSettingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class SmsMonitoringUiState(
    val isPermissionGranted: Boolean = false,
    val isProtectionEnabled: Boolean = true,
    val recentAnalyses: List<SmsAnalysisResult> = emptyList(),
    val threatRecords: List<ThreatRecord> = emptyList(),
    val activeDetailResult: SmsProcessResult? = null,
    val selectedThreat: ThreatRecord? = null,
    val selectedAnalysis: SmsAnalysisResult? = null,
    val isProcessing: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class SmsMonitoringViewModel(
    private val checkSmsPermissionUseCase: CheckSmsPermissionUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val updateSettingUseCase: UpdateSettingUseCase,
    private val smsAnalysisRepository: SmsAnalysisRepository,
    private val threatHistoryRepository: ThreatHistoryRepository,
    private val processIncomingSmsUseCase: ProcessIncomingSmsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmsMonitoringUiState())
    val uiState: StateFlow<SmsMonitoringUiState> = _uiState.asStateFlow()

    init {
        checkPermissionState()
        loadSettingsAndHistory()
    }

    fun checkPermissionState() {
        val granted = checkSmsPermissionUseCase.isAllSmsPermissionsGranted()
        _uiState.value = _uiState.value.copy(isPermissionGranted = granted)
    }

    private fun loadSettingsAndHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Observe settings Flow
            launch {
                getSettingsUseCase().collect { settings ->
                    _uiState.value = _uiState.value.copy(isProtectionEnabled = settings.realtimeSmsProtection)
                }
            }

            // Observe SMS analysis history from Room
            launch {
                smsAnalysisRepository.observeAllAnalyses()
                    .catch { e ->
                        _uiState.value = _uiState.value.copy(errorMessage = e.message)
                    }
                    .collect { list ->
                        _uiState.value = _uiState.value.copy(
                            recentAnalyses = list,
                            isLoading = false
                        )
                    }
            }

            // Observe Threat History from Room
            launch {
                threatHistoryRepository.observeAllThreats()
                    .catch { e ->
                        _uiState.value = _uiState.value.copy(errorMessage = e.message)
                    }
                    .collect { threats ->
                        _uiState.value = _uiState.value.copy(threatRecords = threats)
                    }
            }
        }
    }

    fun toggleProtection(enabled: Boolean) {
        viewModelScope.launch {
            updateSettingUseCase.setSmsProtection(enabled).onSuccess {
                _uiState.value = _uiState.value.copy(isProtectionEnabled = enabled)
            }
        }
    }

    fun analyzeSampleSms(sender: String, body: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, errorMessage = null)
            val rawSms = RawSms(
                messageId = System.currentTimeMillis().toString(),
                sender = sender,
                body = body,
                timestamp = System.currentTimeMillis()
            )
            processIncomingSmsUseCase(rawSms).fold(
                onSuccess = { analysisResult ->
                    val fallbackCat = when {
                        analysisResult.riskScore >= 76 -> ThreatCategory.HIGH
                        analysisResult.riskScore >= 51 -> ThreatCategory.MEDIUM
                        analysisResult.riskScore >= 26 -> ThreatCategory.LOW
                        else -> ThreatCategory.SAFE
                    }
                    val processResult = SmsProcessResult(
                        analysisResult = analysisResult,
                        assessment = analysisResult.assessment ?: RiskAssessment(
                            riskScore = analysisResult.riskScore,
                            category = fallbackCat
                        ),
                        rawSms = rawSms,
                        processedSms = ProcessedSms(
                            raw = rawSms,
                            normalizedText = rawSms.body,
                            tokens = emptyList(),
                            evidence = SmsFeatureEvidence()
                        ),
                        smishingModelProb = analysisResult.smishingModelProb,
                        isModelAvailable = analysisResult.isModelAvailable
                    )
                    _uiState.value = _uiState.value.copy(
                        selectedAnalysis = analysisResult,
                        activeDetailResult = processResult,
                        isProcessing = false
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = error.message ?: "Failed to process SMS",
                        isProcessing = false
                    )
                }
            )
        }
    }

    fun selectItemForDetail(analysis: SmsAnalysisResult) {
        // Find matching threat record if available (for threats)
        val matchingThreat = _uiState.value.threatRecords.firstOrNull {
            it.timestamp == analysis.timestamp || (it.sender == analysis.sender && it.riskScore == analysis.riskScore)
        }
        _uiState.value = _uiState.value.copy(
            selectedAnalysis = analysis,
            selectedThreat = matchingThreat
        )
    }

    fun dismissDetail() {
        _uiState.value = _uiState.value.copy(
            selectedAnalysis = null,
            selectedThreat = null,
            activeDetailResult = null
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
