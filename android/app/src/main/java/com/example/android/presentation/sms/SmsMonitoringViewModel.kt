package com.example.android.presentation.sms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.domain.model.RawSms
import com.example.android.domain.model.SmsAnalysisResult
import com.example.android.domain.repository.SmsAnalysisRepository
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
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class SmsMonitoringViewModel(
    private val checkSmsPermissionUseCase: CheckSmsPermissionUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val updateSettingUseCase: UpdateSettingUseCase,
    private val smsAnalysisRepository: SmsAnalysisRepository,
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
        }
    }

    fun toggleProtection(enabled: Boolean) {
        viewModelScope.launch {
            updateSettingUseCase.setSmsProtection(enabled).onSuccess {
                _uiState.value = _uiState.value.copy(isProtectionEnabled = enabled)
            }
        }
    }

    /**
     * For demonstration or manual test invocation in-app.
     */
    fun analyzeSampleSms(sender: String, body: String) {
        viewModelScope.launch {
            val rawSms = RawSms(
                messageId = System.currentTimeMillis().toString(),
                sender = sender,
                body = body,
                timestamp = System.currentTimeMillis()
            )
            processIncomingSmsUseCase(rawSms)
        }
    }
}
