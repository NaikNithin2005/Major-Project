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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val smsScannedCount: Int = 0,
    val qrScannedCount: Int = 0,
    val threatCount: Int = 0,
    val securityScore: Int = 100,
    val threatHistory: List<ThreatRecord> = emptyList(),
    val recentAnalyses: List<SmsAnalysisResult> = emptyList(),
    val isLoading: Boolean = false
)

class DashboardViewModel(
    getAuthStateUseCase: GetAuthStateUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val threatHistoryRepository: ThreatHistoryRepository,
    private val smsAnalysisRepository: SmsAnalysisRepository,
    private val qrAnalysisRepository: com.example.android.domain.repository.QrAnalysisRepository? = null,
    private val addThreatRecordUseCase: AddThreatRecordUseCase? = null,
    private val deleteThreatRecordUseCase: DeleteThreatRecordUseCase? = null
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

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
        }
    }
}
