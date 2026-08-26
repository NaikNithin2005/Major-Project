package com.example.android.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.domain.model.AuthState
import com.example.android.domain.usecase.GetAuthStateUseCase
import com.example.android.domain.usecase.LogoutUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    getAuthStateUseCase: GetAuthStateUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    val authState: StateFlow<AuthState> = getAuthStateUseCase().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AuthState.Uninitialized
    )

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
        }
    }
}
