package com.example.android.presentation.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.domain.usecase.RegisterUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val pass: String = "",
    val confirmPass: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class RegisterViewModel(
    private val registerUseCase: RegisterUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(name = name, errorMessage = null)
    }

    fun onEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun onPasswordChanged(pass: String) {
        _uiState.value = _uiState.value.copy(pass = pass, errorMessage = null)
    }

    fun onConfirmPasswordChanged(confirmPass: String) {
        _uiState.value = _uiState.value.copy(confirmPass = confirmPass, errorMessage = null)
    }

    fun register(onSuccess: (() -> Unit)? = null) {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please enter your name")
            return
        }
        if (state.email.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please enter your email address")
            return
        }
        if (state.pass.length < 6) {
            _uiState.value = state.copy(errorMessage = "Password must be at least 6 characters long")
            return
        }
        if (state.pass != state.confirmPass) {
            _uiState.value = state.copy(errorMessage = "Passwords do not match")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            val result = registerUseCase(state.name.trim(), state.email.trim(), state.pass)
            result.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Registration failed"
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess?.invoke()
            }
        }
    }
}
