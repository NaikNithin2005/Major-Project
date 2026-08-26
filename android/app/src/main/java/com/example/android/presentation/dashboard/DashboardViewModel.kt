package com.example.android.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.domain.model.AuthState
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.usecase.AddThreatRecordUseCase
import com.example.android.domain.usecase.DeleteThreatRecordUseCase
import com.example.android.domain.usecase.GetAuthStateUseCase
import com.example.android.domain.usecase.GetThreatHistoryUseCase
import com.example.android.domain.usecase.LogoutUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    getAuthStateUseCase: GetAuthStateUseCase,
    private val logoutUseCase: LogoutUseCase,
    getThreatHistoryUseCase: GetThreatHistoryUseCase? = null,
    private val addThreatRecordUseCase: AddThreatRecordUseCase? = null,
    private val deleteThreatRecordUseCase: DeleteThreatRecordUseCase? = null
) : ViewModel() {

    val authState: StateFlow<AuthState> = getAuthStateUseCase().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AuthState.Uninitialized
    )

    val threatHistory: StateFlow<List<ThreatRecord>> = (getThreatHistoryUseCase?.invoke()
        ?: kotlinx.coroutines.flow.flowOf(emptyList())).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addSampleThreat(source: String = "SMS", category: String = "Smishing", sender: String = "+18005550199") {
        viewModelScope.launch {
            val record = ThreatRecord(
                id = "threat_${System.currentTimeMillis()}",
                timestamp = System.currentTimeMillis(),
                source = source,
                sender = sender,
                riskScore = 88,
                category = category,
                actionTaken = "BLOCKED",
                details = "Suspicious link detected matching bank phishing patterns"
            )
            addThreatRecordUseCase?.invoke(record)
        }
    }

    fun deleteThreat(id: String) {
        viewModelScope.launch {
            deleteThreatRecordUseCase?.invoke(id)
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
        }
    }
}
