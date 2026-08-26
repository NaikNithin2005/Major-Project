package com.example.android.presentation.splash

import androidx.lifecycle.ViewModel
import com.example.android.domain.model.AuthState
import com.example.android.domain.usecase.GetAuthStateUseCase
import kotlinx.coroutines.flow.StateFlow

class SplashViewModel(
    getAuthStateUseCase: GetAuthStateUseCase
) : ViewModel() {
    val authState: StateFlow<AuthState> = getAuthStateUseCase()
}
