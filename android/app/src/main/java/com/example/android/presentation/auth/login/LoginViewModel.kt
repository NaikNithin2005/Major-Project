package com.example.android.presentation.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.domain.usecase.GuestLoginUseCase
import com.example.android.domain.usecase.LoginUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val pass: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    private val guestLoginUseCase: GuestLoginUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun onPasswordChanged(pass: String) {
        _uiState.value = _uiState.value.copy(pass = pass, errorMessage = null)
    }

    fun login(onSuccess: (() -> Unit)? = null) {
        val email = _uiState.value.email.trim()
        val pass = _uiState.value.pass
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter your email address")
            return
        }
        if (pass.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter your password")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = loginUseCase(email, pass)
            result.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Sign in failed"
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess?.invoke()
            }
        }
    }

    fun loginAsGuest(onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = guestLoginUseCase()
            result.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Guest login failed"
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess?.invoke()
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
