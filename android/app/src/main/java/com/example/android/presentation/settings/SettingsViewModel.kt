package com.example.android.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.domain.model.AuthState
import com.example.android.domain.model.SystemSettings
import com.example.android.domain.usecase.GetAuthStateUseCase
import com.example.android.domain.usecase.GetSettingsUseCase
import com.example.android.domain.usecase.LogoutUseCase
import com.example.android.domain.usecase.UpdateSettingUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    getAuthStateUseCase: GetAuthStateUseCase,
    private val logoutUseCase: LogoutUseCase,
    getSettingsUseCase: GetSettingsUseCase? = null,
    private val updateSettingUseCase: UpdateSettingUseCase? = null
) : ViewModel() {

    val authState: StateFlow<AuthState> = getAuthStateUseCase().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AuthState.Uninitialized
    )

    val systemSettings: StateFlow<SystemSettings> = (getSettingsUseCase?.invoke()
        ?: kotlinx.coroutines.flow.flowOf(SystemSettings())).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SystemSettings()
        )

    fun toggleSmsProtection(enabled: Boolean) {
        viewModelScope.launch {
            updateSettingUseCase?.setSmsProtection(enabled)
        }
    }

    fun toggleQrScanner(enabled: Boolean) {
        viewModelScope.launch {
            updateSettingUseCase?.setQrScanner(enabled)
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            updateSettingUseCase?.setNotificationsEnabled(enabled)
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        viewModelScope.launch {
            updateSettingUseCase?.setBiometricLock(enabled)
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
        }
    }
}
